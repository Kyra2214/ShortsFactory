package com.shortsfactory.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** Importa vídeos a partir de arquivo local ou URL com autorização do usuário. */
class VideoImporter(private val appContext: Context) {

    private val client = OkHttpClient.Builder().build()

    sealed class ImportResult {
        data class Success(val localPath: String) : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }

    /**
     * Baixa um vídeo de uma URL informada pelo usuário (conteúdo com autorização).
     * Nunca contorna DRM, login, paywall ou bloqueios técnicos.
     */
    suspend fun downloadFromUrl(url: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext ImportResult.Failure(
                    "A fonte não permitiu o acesso (${response.code}). O aplicativo não contorna proteções de download."
                )
            }
            val body = response.body ?: return@withContext ImportResult.Failure("Resposta vazia da fonte.")
            val extension = guessExtension(url, body.contentType()?.toString())
            val outputFile = File(appContext.filesDir, "video_${System.currentTimeMillis()}.$extension")
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output -> input.copyTo(output) }
            }
            ImportResult.Success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar vídeo", e)
            ImportResult.Failure("Falha ao baixar: ${e.message}")
        }
    }

    /** Copia um vídeo selecionado pelo usuário (arquivo local, via SAF) para o armazenamento do app. */
    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val extension = guessExtension(uri.toString(), null)
            val outputFile = File(appContext.filesDir, "video_${System.currentTimeMillis()}.$extension")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output -> input.copyTo(output) }
            }
            ImportResult.Success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao importar vídeo", e)
            ImportResult.Failure("Falha ao importar arquivo: ${e.message}")
        }
    }

    private fun guessExtension(source: String, contentType: String?): String {
        val urlExt = source.substringAfterLast('.', "").takeIf { it.length in 2..5 }
        return when {
            urlExt != null && VIDEO_EXTENSIONS.contains(urlExt.lowercase()) -> urlExt.lowercase()
            contentType != null && "video/mp4" in contentType -> "mp4"
            contentType != null && "webm" in contentType -> "webm"
            else -> "mp4"
        }
    }

    companion object {
        private const val TAG = "VideoImporter"
        private val VIDEO_EXTENSIONS = listOf("mp4", "webm", "mov", "mkv", "avi", "m4v")
    }
}
