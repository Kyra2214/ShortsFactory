package com.shortsfactory.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Armazenamento criptografado de chaves sensíveis (ex.: chave da API xAI/Grok). */
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

    fun getApiKey(): String? = prefs.getString(KEY_API, null)

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

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
        private const val KEY_TRANSCRIPTION_API = "openai_transcription_api_key"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_QUALITY = "quality"
        private const val KEY_FPS = "fps"
        private const val KEY_SUBTITLE_STYLE = "subtitle_style"
        private const val KEY_DURATION = "duration_preset"
    }
}
