package com.shortsfactory.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/** Extrai binários nativos embutidos no APK para um diretório executável do cache. */
object AssetExtractor {

    private const val TAG = "AssetExtractor"
    private const val DIRECTORY = "ffmpeg_bin"

    fun extractIfNeeded(context: Context, assetName: String = "ffmpeg"): File? {
        val dest = File(File(context.cacheDir, DIRECTORY), assetName)
        dest.parentFile?.mkdirs()
        if (dest.exists() && dest.canExecute()) return dest
        return try {
            extractAsset(context, assetName, dest)
            dest.takeIf { it.exists() && it.canExecute() }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao extrair binário $assetName", e)
            null
        }
    }

    private fun extractAsset(context: Context, assetName: String, dest: File) {
        context.assets.open(assetName).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        dest.setExecutable(true, false)
    }
}
