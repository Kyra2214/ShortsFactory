package com.shortsfactory.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

/** Armazenamento criptografado de chaves sensíveis usadas pelas integrações externas. */
class SecureKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Retorna as chaves xAI na ordem configurada.
     * A chave antiga, salva antes do suporte a múltiplas chaves, é migrada em memória.
     */
    fun getApiKeys(): List<String> {
        val encoded = prefs.getString(KEY_API_KEYS, null)
        if (!encoded.isNullOrBlank()) {
            return runCatching {
                val array = JSONArray(encoded)
                buildList {
                    for (index in 0 until array.length()) {
                        array.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
                    }
                }
            }.getOrDefault(emptyList())
        }

        return prefs.getString(KEY_API, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::listOf)
            ?: emptyList()
    }

    /** Mantém compatibilidade com consumidores antigos que ainda solicitam uma única chave. */
    fun getApiKey(): String? = getApiKeys().firstOrNull()

    fun saveApiKey(key: String) {
        saveApiKeys(listOf(key))
    }

    /** Salva as chaves sem duplicação, preservando a ordem de tentativa do usuário. */
    fun saveApiKeys(keys: List<String>) {
        val normalized = keys.map(String::trim).filter(String::isNotEmpty).distinct()
        val array = JSONArray().apply { normalized.forEach(::put) }
        prefs.edit()
            .putString(KEY_API_KEYS, array.toString())
            .putString(KEY_API, normalized.firstOrNull())
            .apply()
    }

    /** IDs de provedores gratuitos (catálogo) com ao menos uma chave salva. */
    fun configuredProviderIds(): Set<String> =
        prefs.all.keys
            .filter { it.startsWith(KEY_PROVIDER_PREFIX) }
            .map { it.removePrefix(KEY_PROVIDER_PREFIX) }
            .filter { getProviderKeys(it).isNotEmpty() }
            .toSet()

    /** Chaves do provedor gratuito [providerId], na ordem de tentativa. ID inválido retorna vazio. */
    fun getProviderKeys(providerId: String): List<String> {
        val id = normalizeProviderId(providerId) ?: return emptyList()
        val encoded = prefs.getString(KEY_PROVIDER_PREFIX + id, null)
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun hasProviderKey(providerId: String): Boolean = getProviderKeys(providerId).isNotEmpty()

    /** Salva as chaves do provedor sem duplicação; lista vazia remove a entrada (provedor desligado). */
    fun saveProviderKeys(providerId: String, keys: List<String>) {
        val id = normalizeProviderId(providerId) ?: return
        val normalized = keys.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalized.isEmpty()) {
            clearProviderKeys(id)
            return
        }
        val array = JSONArray().apply { normalized.forEach(::put) }
        prefs.edit().putString(KEY_PROVIDER_PREFIX + id, array.toString()).apply()
    }

    fun clearProviderKeys(providerId: String) {
        val id = normalizeProviderId(providerId) ?: return
        prefs.edit().remove(KEY_PROVIDER_PREFIX + id).apply()
    }

    private fun normalizeProviderId(providerId: String): String? =
        providerId.trim().lowercase().takeIf { PROVIDER_ID_REGEX.matches(it) }

    fun getTranscriptionApiKey(): String? = prefs.getString(KEY_TRANSCRIPTION_API, null)

    fun saveTranscriptionApiKey(key: String) {
        prefs.edit().putString(KEY_TRANSCRIPTION_API, key.trim()).apply()
    }

    fun saveSettings(resolution: String, quality: String, fps: Int, subtitleStyle: String, duration: String) {
        prefs.edit()
            .putString(KEY_RESOLUTION, resolution)
            .putString(KEY_QUALITY, quality)
            .putInt(KEY_FPS, fps)
            .putString(KEY_SUBTITLE_STYLE, subtitleStyle)
            .putString(KEY_DURATION, duration)
            .apply()
    }

    fun resolution(): String = prefs.getString(KEY_RESOLUTION, "1080 × 1920") ?: "1080 × 1920"
    fun quality(): String = prefs.getString(KEY_QUALITY, "Normal") ?: "Normal"
    fun fps(): Int = prefs.getInt(KEY_FPS, 30)
    fun subtitleStyle(): String = prefs.getString(KEY_SUBTITLE_STYLE, "creator") ?: "creator"
    fun durationPreset(): String = prefs.getString(KEY_DURATION, "30s") ?: "30s"

    companion object {
        private const val KEY_API = "grok_api_key"
        private const val KEY_API_KEYS = "xai_api_keys"
        private const val KEY_PROVIDER_PREFIX = "free_api_keys_"
        private val PROVIDER_ID_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,39}")
        private const val KEY_TRANSCRIPTION_API = "openai_transcription_api_key"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_QUALITY = "quality"
        private const val KEY_FPS = "fps"
        private const val KEY_SUBTITLE_STYLE = "subtitle_style"
        private const val KEY_DURATION = "duration_preset"
    }
}
