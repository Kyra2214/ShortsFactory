package com.shortsfactory.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.shortsfactory.domain.importing.HttpResume
import com.shortsfactory.domain.importing.ImportExtension
import com.shortsfactory.domain.importing.ImportSpacePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/** Importa vídeos a partir de arquivo local ou URL com autorização do usuário. */
class VideoImporter(private val appContext: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

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
            val partialFile = File(appContext.filesDir, "video_$transferKey.part")
            val metaFile = File(appContext.filesDir, "video_$transferKey.part.meta")
            File(appContext.cacheDir, "video_$transferKey.part").delete() // parcial legado (outro diretório)
            val validator = if (metaFile.isFile) metaFile.readText().trim().ifEmpty { null } else null
            // Sem validador salvo não há como garantir que o conteúdo não mudou: recomeça do zero.
            if (partialFile.isFile && validator == null) { partialFile.delete(); metaFile.delete() }
            var offset = if (partialFile.isFile) partialFile.length() else 0L

            val requestBuilder = Request.Builder().url(url)
                .header("Accept", "video/*,application/octet-stream;q=0.9,*/*;q=0.1")
            if (offset > 0L && validator != null) {
                requestBuilder.header("Range", "bytes=" + offset + "-")
                requestBuilder.header("If-Range", validator)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 416) {
                    partialFile.delete(); metaFile.delete()
                    return@withContext ImportResult.Failure("O download parcial era inválido e foi descartado. Tente novamente.")
                }
                val body = response.body ?: return@withContext ImportResult.Failure("Resposta vazia da fonte.")
                if (!response.isSuccessful && response.code != 206) return@withContext ImportResult.Failure("A fonte não permitiu o acesso (" + response.code + "). O aplicativo não contorna proteções de download.")
                val contentType = body.contentType()?.toString()?.lowercase()
                if (contentType != null && contentType.contains("text/html")) return@withContext ImportResult.Failure("A URL retornou uma página HTML, não um arquivo de vídeo.")

                val finalFile = File(
                    appContext.filesDir,
                    "video_$transferKey." + ImportExtension.resolve(contentType, url.toHttpUrlOrNull()?.encodedPath)
                )
                val append = offset > 0L && response.code == 206
                if (append) {
                    HttpResume.validateResume(HttpResume.parseContentRange(response.header("Content-Range")), offset)
                        ?.let { return@withContext ImportResult.Failure(it) }
                } else {
                    // 200 (inclusive quando If-Range indicou mudança): recomeça e guarda o novo validador.
                    offset = 0L
                    partialFile.delete()
                    val newValidator = HttpResume.ifRangeValidator(response.header("ETag"), response.header("Last-Modified"))
                    if (newValidator != null) metaFile.writeText(newValidator) else metaFile.delete()
                }
                val maxBytes = ImportSpacePolicy.MAX_IMPORT_BYTES
                val expectedLength = body.contentLength().takeIf { it >= 0L } ?: -1L
                val totalExpected = if (expectedLength >= 0L) offset + expectedLength else -1L
                // rename atômico no mesmo diretório: só os bytes restantes ocupam disco novo.
                ImportSpacePolicy.check(expectedLength, totalExpected, appContext.filesDir.usableSpace)
                    ?.let { return@withContext ImportResult.Failure(it) }

                body.byteStream().use { input ->
                    RandomAccessFile(partialFile, "rw").use { output ->
                        output.seek(offset)
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = offset
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maxBytes) { partialFile.delete(); metaFile.delete(); return@withContext ImportResult.Failure("O vídeo excede o limite local.") }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (!partialFile.isFile || partialFile.length() == 0L) { partialFile.delete(); metaFile.delete(); return@withContext ImportResult.Failure("O arquivo baixado está vazio.") }
                if (totalExpected >= 0L && partialFile.length() != totalExpected) {
                    return@withContext ImportResult.Failure("Download interrompido antes do fim; tente novamente para retomar.")
                }
                Files.move(partialFile.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                metaFile.delete()
                ImportResult.Success(finalFile.absolutePath)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao baixar vídeo", e)
            ImportResult.Failure("Falha ao baixar: ${e.message}")
        }
    }

    /** Copia um vídeo selecionado pelo usuário (arquivo local, via SAF) para o armazenamento do app. */
    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val extension = ImportExtension.resolve(appContext.contentResolver.getType(uri), queryDisplayName(uri))
            val outputFile = File(appContext.filesDir, "video_${System.currentTimeMillis()}.$extension")
            val declaredSize = querySize(uri)
            ImportSpacePolicy.check(declaredSize, declaredSize, appContext.filesDir.usableSpace)
                ?.let { return@withContext ImportResult.Failure(it) }
            val input = appContext.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Failure("Não foi possível abrir o arquivo selecionado.")
            try {
                input.use {
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = it.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > ImportSpacePolicy.MAX_IMPORT_BYTES) {
                                outputFile.delete()
                                return@withContext ImportResult.Failure("O vídeo excede o limite local.")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } catch (e: Exception) {
                outputFile.delete()
                throw e
            }
            if (!outputFile.isFile || outputFile.length() == 0L) {
                outputFile.delete()
                return@withContext ImportResult.Failure("O arquivo selecionado está vazio.")
            }
            ImportResult.Success(outputFile.absolutePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao importar vídeo", e)
            ImportResult.Failure("Falha ao importar arquivo: ${e.message}")
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (e: SecurityException) {
        null
    }

    private fun querySize(uri: Uri): Long = try {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
    } catch (e: SecurityException) {
        -1L
    }

    private fun stableTransferKey(url: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }

    companion object {
        private const val TAG = "VideoImporter"
        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S = 30L
    }
}
