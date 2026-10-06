package com.shortsfactory.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ShortsFactoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SfDarkColors else SfLightColors,
        typography = SfTypography,
        shapes = SfShapes,
        content = content
    )
}
