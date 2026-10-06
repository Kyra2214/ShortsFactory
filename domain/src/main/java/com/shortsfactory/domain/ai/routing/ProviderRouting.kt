package com.shortsfactory.domain.ai.routing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Ordena provedores e registra o resultado de cada tentativa. */
interface ProviderRouting {
    fun order(names: List<String>): List<String>
    fun record(name: String, success: Boolean, latencyMs: Long)
}

data class ProviderStat(
    val successes: Int = 0,
    val failures: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastFailureMs: Long = 0L,
    val avgLatencyMs: Long = 0L
)

/**
 * Roteamento por estatística de uso real: taxa de sucesso suavizada, quarentena temporária após falhas
 * seguidas e leve preferência por menor latência. Provedor sem histórico fica no meio da fila e empates
 * preservam a ordem original (catálogo). Nenhum limite de provedor é assumido.
 */
class ProviderStatsTracker(
    initialJson: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: ((String) -> Unit)? = null
) : ProviderRouting {

    private val stats = LinkedHashMap<String, ProviderStat>()

    init {
        initialJson?.let { stats.putAll(parse(it)) }
    }

    @Synchronized
    override fun order(names: List<String>): List<String> {
        val t = now()
        return names.sortedByDescending { score(stats[it], t) }
    }

    @Synchronized
    override fun record(name: String, success: Boolean, latencyMs: Long) {
        val old = stats[name] ?: ProviderStat()
        val latency = latencyMs.coerceAtLeast(0L)
        val avg = if (old.avgLatencyMs == 0L) latency else (old.avgLatencyMs * 7 + latency) / 8
        stats[name] = if (success) {
            old.copy(successes = old.successes + 1, consecutiveFailures = 0, avgLatencyMs = avg)
        } else {
            old.copy(
                failures = old.failures + 1,
                consecutiveFailures = old.consecutiveFailures + 1,
                lastFailureMs = now()
            )
        }
        trim()
        onChange?.invoke(toJson())
    }

    @Synchronized
    fun snapshot(): Map<String, ProviderStat> = LinkedHashMap(stats)

    @Synchronized
    fun toJson(): String = buildJsonObject {
        stats.forEach { (name, s) ->
            put(name, buildJsonObject {
                put("s", s.successes)
                put("f", s.failures)
                put("c", s.consecutiveFailures)
                put("l", s.lastFailureMs)
                put("a", s.avgLatencyMs)
            })
        }
    }.toString()

    /** Mantém o histórico recente: contadores altos são reduzidos pela metade. */
    private fun trim() {
        stats.replaceAll { _, s ->
            if (s.successes + s.failures > MAX_SAMPLES) {
                s.copy(successes = s.successes / 2, failures = s.failures / 2)
            } else s
        }
    }

    private fun score(stat: ProviderStat?, nowMs: Long): Double {
        if (stat == null) return 0.5
        val rate = (stat.successes + 1.0) / (stat.successes + stat.failures + 2.0)
        val latencyFactor = 1.0 / (1.0 + stat.avgLatencyMs / LATENCY_SCALE_MS)
        var value = rate * (0.7 + 0.3 * latencyFactor)
        if (stat.consecutiveFailures > 0) {
            val cooldown = COOLDOWN_MS * stat.consecutiveFailures.coerceAtMost(MAX_COOLDOWN_STEPS)
            if (nowMs - stat.lastFailureMs < cooldown) value *= QUARANTINE_FACTOR
        }
        return value
    }

    companion object {
        private const val MAX_SAMPLES = 200
        private const val LATENCY_SCALE_MS = 60_000.0
        private const val COOLDOWN_MS = 120_000L
        private const val MAX_COOLDOWN_STEPS = 5
        private const val QUARANTINE_FACTOR = 0.2

        fun parse(json: String): Map<String, ProviderStat> {
            val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return emptyMap()
            val out = LinkedHashMap<String, ProviderStat>()
            root.forEach { (name, element) ->
                val o = element as? JsonObject ?: return@forEach
                fun num(key: String): Long = (o[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
                out[name] = ProviderStat(
                    successes = num("s").toInt().coerceAtLeast(0),
                    failures = num("f").toInt().coerceAtLeast(0),
                    consecutiveFailures = num("c").toInt().coerceAtLeast(0),
                    lastFailureMs = num("l").coerceAtLeast(0L),
                    avgLatencyMs = num("a").coerceAtLeast(0L)
                )
            }
            return out
        }
    }
}
