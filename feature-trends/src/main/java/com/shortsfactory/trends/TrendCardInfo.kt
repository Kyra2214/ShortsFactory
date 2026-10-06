package com.shortsfactory.trends

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Column {
        Text(
            card.origin.badgeLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = if (card.origin == TrendOrigin.OFFICIAL_API) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        if (card.origin == TrendOrigin.OFFICIAL_API) {
            card.views?.let { Text("Visualizações: $it", style = MaterialTheme.typography.bodySmall) }
            card.engagement?.let { Text("Engajamento: $it", style = MaterialTheme.typography.bodySmall) }
        }
        if (card.origin == TrendOrigin.AI_INFERENCE) {
            card.aiEstimate?.let { Text("Estimativa da IA: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
