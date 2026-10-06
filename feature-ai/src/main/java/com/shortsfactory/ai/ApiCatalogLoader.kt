package com.shortsfactory.ai

import android.content.Context
import com.shortsfactory.domain.ai.catalog.ApiCatalog
import com.shortsfactory.domain.ai.catalog.ApiCatalogParser

object ApiCatalogLoader {
    private const val ASSET = "ai_api_catalog.json"

    @Volatile private var cached: ApiCatalog? = null

    fun load(context: Context): ApiCatalog = cached ?: synchronized(this) {
        cached ?: context.applicationContext.assets.open(ASSET).use {
            ApiCatalogParser.parse(it.readBytes().toString(Charsets.UTF_8))
        }.also { cached = it }
    }
}
