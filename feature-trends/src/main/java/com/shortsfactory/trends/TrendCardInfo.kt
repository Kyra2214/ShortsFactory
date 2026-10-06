package com.shortsfactory.trends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortsfactory.domain.model.TrendCard
import com.shortsfactory.domain.model.TrendOrigin

internal fun TrendOrigin.badgeLabel(): String = when (this) {
    TrendOrigin.OFFICIAL_API -> "Dado oficial da plataforma"
    TrendOrigin.AI_INFERENCE -> "Inferência da IA (não é métrica oficial)"
    TrendOrigin.LINK_ONLY -> "Apenas link de busca"
}

/** Selo de origem + métricas; "Visualizações"/"Engajamento" só com origem oficial. */
@Composable
internal fun TrendCardInfo(card: TrendCard) {
    val official = card.origin == TrendOrigin.OFFICIAL_API
    Column {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = if (official) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (official) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Text(
                card.origin.badgeLabel(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (official) {
            card.views?.let { Text("Visualizações: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            card.engagement?.let { Text("Engajamento: $it", style = MaterialTheme.typography.bodySmall) }
        }
        if (card.origin == TrendOrigin.AI_INFERENCE) {
            card.aiEstimate?.let { Text("Estimativa da IA: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
