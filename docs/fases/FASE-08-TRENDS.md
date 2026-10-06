# Fase 8 — Trends: separar dado de inferência

**Status: implementada (8.1 a 8.4). NÃO compilado nem executado: Gradle/Android SDK/Kotlin indisponíveis neste ambiente (a suíte completa não pôde ser rodada; verificação estática de referências/imports feita). Gates pendentes: build, `:domain:test`, lint e CI.**

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 8.1 | Modelo de domínio (`TrendOrigin`, `ProviderCapability`, `TrendCard`), contrato `TrendProvider.capability`, parser Grok sem métricas | concluído |
| 8.2 | UI (`ContentRadarScreen`, `TrendHunterScreen`): selo por origem; "Visualizações"/"Engajamento" só com `OFFICIAL_API`; `aiEstimate` rotulado "estimativa da IA" | concluído |
| 8.3 | `relevanceScore` baseado em índice renomeado/removido ("ordem"); fim da dupla chamada de IA quando `platform == "grok"` | concluído |
| 8.4 | Testes JVM: JSON do Grok com `views: "2M"` e `openable: true` não gera `views`; invariantes de `TrendCard` | concluído |

## 8.1 — O que mudou
- `domain/model/Models.kt`: `TrendOrigin { OFFICIAL_API, AI_INFERENCE, LINK_ONLY }`, `ProviderCapability` (com `toOrigin()`), `TrendCard` ganhou `origin` (default `LINK_ONLY`), `metricsVerified`, `aiEstimate`. Invariantes em `init`: `views`/`engagement` só com `OFFICIAL_API`; `metricsVerified` só com `OFFICIAL_API`; `aiEstimate` só com `AI_INFERENCE`.
- `domain/trends/TrendProviders.kt`: `isAvailable()` substituído por `val capability: ProviderCapability`.
- `feature-trends/TrendProviders.kt`: `GrokTrendProvider` = `AI_INFERENCE`; demais 9 provedores = `LINK_ONLY`, cards com `origin = capability.toOrigin()`.
- `feature-ai/GrokProvider.parseTrends`: cards sempre `AI_INFERENCE`, `views`/`engagement` sempre `null`; valores dados pelo modelo vão para `aiEstimate`. `openable` agora exige também `sourceUrl` http(s).

ASSUMINDO: nenhum provedor tem API oficial integrada; `OFFICIAL_API` existe no contrato para integração futura.
ASSUMINDO: `ProjectDao`/UI atuais continuam compilando porque `origin` tem default e a UI só lê `views`/`engagement` (agora sempre nulos fora de `OFFICIAL_API`); selos ficam para 8.2.

## 8.2 — O que mudou
- `feature-trends/TrendCardInfo.kt` (novo, interno): selo por origem (`OFFICIAL_API` / `AI_INFERENCE` / `LINK_ONLY`); "Visualizações" e "Engajamento" só renderizam com `OFFICIAL_API`; `aiEstimate` aparece como "Estimativa da IA" só com `AI_INFERENCE`.
- `ContentRadarScreen` e `TrendHunterScreen` passam a usar `TrendCardInfo` (rótulo antigo "Visualizações estimadas" removido). "Relevância" do Caçador fica para 8.3.

## 8.3 — O que mudou
- `TrendCard.relevanceScore` renomeado para `order` (posição 1-based de chegada; 0 = sem ordem). `hunterSearch` não reordena nem simula pontuação; a UI mostra "Ordem: #n" em vez de "Relevância".
- `TrendSearchRepositoryImpl.search` e `hunterSearch` ignoram o provider `grok` na parte de plataformas (já coberto por `aiResults`), eliminando a segunda chamada de IA.

## 8.4 — O que mudou
- `TrendResponseParser` (domain, puro) extraído de `GrokProvider.parseTrends` para ser testável no JVM; o provider só extrai o JSON e delega.
- `TrendResponseParserTest` (5 testes): JSON com `views: "2M"` + `openable: true` não gera `views`/`engagement`; `null`/vazio sem estimativa; lista ausente; invariantes de `TrendCard`; `ProviderCapability.toOrigin`.
- A ausência de "Visualizações" para `AI_INFERENCE` na UI é garantida por `TrendCardInfo` (8.2) e pelo invariante do modelo; sem teste de UI Compose neste projeto.
