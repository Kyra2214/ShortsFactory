package com.shortsfactory.ai

import com.shortsfactory.domain.ai.catalog.ApiProviderEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Valida uma chave consultando o endpoint de modelos do provedor (sem enviar conteúdo do usuário). */
object FreeApiKeyTester {

    sealed interface Result {
        data object Valid : Result
        data object Rejected : Result
        data class Failed(val message: String) : Result
    }

    suspend fun test(provider: ApiProviderEntry, key: String): Result = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(provider.modelsEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer ${key.trim()}")
                setRequestProperty("Accept", "application/json")
            }
            try {
                when (val code = connection.responseCode) {
                    in 200..299 -> Result.Valid
                    401, 403 -> Result.Rejected
                    429 -> Result.Failed("Limite do provedor atingido. Tente mais tarde.")
                    else -> Result.Failed("O provedor respondeu com erro HTTP $code.")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed("Sem conexão com o provedor.")
        }
    }
}
