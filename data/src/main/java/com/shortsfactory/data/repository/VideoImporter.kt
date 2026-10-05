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
import java.io.RandomAccessFile

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
            require(url.startsWith("http://") || url.startsWith("https://")) { "URL de vídeo inválida." }
            val transferKey = stableTransferKey(url)
            val partialFile = File(appContext.cacheDir, "video_$transferKey.part")
            val extensionHint = guessExtension(url, null)
            val finalFile = File(appContext.filesDir, "video_$transferKey.$extensionHint")
            var offset = if (partialFile.isFile) partialFile.length() else 0L

            val requestBuilder = Request.Builder().url(url)
                .header("Accept", "video/*,application/octet-stream;q=0.9,*/*;q=0.1")
            if (offset > 0L) requestBuilder.header("Range", "bytes=" + offset + "-")

            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body ?: return@withContext ImportResult.Failure("Resposta vazia da fonte.")
                if (!response.isSuccessful && response.code != 206) return@withContext ImportResult.Failure("A fonte não permitiu o acesso (" + response.code + "). O aplicativo não contorna proteções de download.")
                val contentType = body.contentType()?.toString()?.lowercase()
                if (contentType != null && contentType.contains("text/html")) return@withContext ImportResult.Failure("A URL retornou uma página HTML, não um arquivo de vídeo.")

                val append = offset > 0L && response.code == 206
                if (append) {
                    val rangeStart = response.header("Content-Range")
                        ?.substringAfter("bytes ", "")
                        ?.substringBefore("-", "")
                        ?.toLongOrNull()
                    if (rangeStart != offset) {
                        return@withContext ImportResult.Failure("A fonte retornou um intervalo HTTP incompatível com o download parcial.")
                    }
                } else {
                    offset = 0L
                    partialFile.delete()
                }
            val maxBytes = MAX_IMPORT_BYTES
            val expectedLength = body.contentLength().takeIf { it >= 0L } ?: -1L
            if (expectedLength >= 0L && offset + expectedLength > maxBytes) return@withContext ImportResult.Failure("O vídeo excede o limite local.")

            body.byteStream().use { input ->
                RandomAccessFile(partialFile, "rw").use { output ->
                    output.seek(offset)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = offset
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) { partialFile.delete(); return@withContext ImportResult.Failure("O vídeo excede o limite local.") }
                        output.write(buffer, 0, read)
                    }
                }
            }
                if (!partialFile.isFile || partialFile.length() == 0L) return@withContext ImportResult.Failure("O arquivo baixado está vazio.")
                partialFile.copyTo(finalFile, overwrite = true)
                partialFile.delete()
                ImportResult.Success(finalFile.absolutePath)
            }
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
            val input = appContext.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Failure("Não foi possível abrir o arquivo selecionado.")
            input.use {
                FileOutputStream(outputFile).use { output -> it.copyTo(output) }
            }
            if (!outputFile.isFile || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext ImportResult.Failure("O arquivo selecionado está vazio.")
            }
            ImportResult.Success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao importar vídeo", e)
            ImportResult.Failure("Falha ao importar arquivo: ${e.message}")
        }
    }

    private fun stableTransferKey(url: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
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
        private const val MAX_IMPORT_BYTES = 8L * 1024L * 1024L * 1024L
        private val VIDEO_EXTENSIONS = listOf("mp4", "webm", "mov", "mkv", "avi", "m4v")
    }
}
