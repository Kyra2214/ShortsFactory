# Fase 5 — Seleção determinística

**Status: implementada (5.1 a 5.5). `:domain` (104 testes JVM) passou localmente; compilação dos módulos Android e CI completo permanecem pendentes.**

Objetivo: nenhum candidato impossível ou sobreposto sai do selector, para qualquer preset.

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 5.1 | Mesmas regras para todos os presets, inclusive `"ai"` (faixa válida, dentro do vídeo, mínimo, teto, sem sobreposição, limite) | concluído |
| 5.2 | `DurationPreset.fromKey`: único mapa chave ↔ duração; preset desconhecido falha com erro claro | concluído |
| 5.3 | Teto do `"ai"` = `suggestedDurationMs` (limitado a `[mínimo, 90 s]`) ou 90 s | concluído |
| 5.4 | Desempate determinístico: score ↓, `startMs` ↑, `endMs` ↑ | concluído |
| 5.5 | Relatório de rejeição (`selectWithReport`) e falha explícita quando nada é selecionável | concluído |

## O que mudou
- **5.1** `CandidateSelector` saiu de `MediaPipeline.kt` para `CandidateSelector.kt`. O atalho `if (maxMs == null) return sorted.take(limit)` foi removido. Regras, nesta ordem: `start >= 0` e `end > start` → `end <= duração do vídeo` (quando conhecida) → `duração >= mínimo (3 s)` → `duração <= teto` → sobreposição (vence o maior score; intervalos encostados não se sobrepõem) → limite `maxCandidates` (0 ou negativo = vazio).
- **5.2** `DurationPreset` ganhou `key`, `ALL`, `fromKeyOrNull` e `fromKey`. Os três mapas duplicados (`MediaPipeline.maxDurationForPreset`, `CandidateSelector.maxDurationFor` com `else -> 30 s`, e o `key()` privado da `SettingsScreen`) foram removidos; `SettingsScreen` e `ProjectScreen` usam `DurationPreset.ALL`/`.key`. Preset desconhecido: o selector lança `IllegalArgumentException` e o pipeline falha em `VideoInput`, antes de extrair áudio, sem retry.
- **5.3** `SelectionRules(minDurationMs = 3_000, aiMaxDurationMs = 90_000)`. No `"ai"`, `select(..., suggestedDurationMs)` usa a sugestão da IA como teto, limitada aos limites globais; sem sugestão vale 90 s. Presets fixos ignoram a sugestão.
- **5.4** Mesmo conjunto de entrada gera sempre a mesma saída, em qualquer ordem (teste com 10 embaralhamentos).
- **5.5** `selectWithReport` devolve `SelectionResult(selected, rejected)` com `RejectionReason` (`INVALID_RANGE`, `TOO_SHORT`, `TOO_LONG`, `BEYOND_VIDEO`, `OVERLAP`, `OVER_LIMIT`). O pipeline passa a duração real do vídeo e `suggestedDurationMs`, e coloca o resumo na mensagem da etapa (`"1 selecionados, 2 rejeitados (1 sobreposto a candidato de maior score, ...)"`). Seleção vazia agora é `Failed` em `CandidateSelection` (sem retry), em vez de `Success` com lista vazia.

## Testes (`:domain`)
- `CandidateSelectorTest` (20): sobreposição com `"ai"`, teto global e sugerido (e limites da sugestão), fim do vídeo (igual passa, +1 ms não), duração desconhecida, mínimo para todos os presets, regras customizadas, empate de score, independência da ordem de entrada, intervalos encostados, propriedade "nenhum candidato impossível/sobreposto" em todos os presets, relatório de cada motivo, `maxCandidates` 0/negativo, preset desconhecido, regras inválidas.
- `DurationPresetTest` (3): ida e volta por chave, durações esperadas, chaves desconhecidas.
- `MediaPipelineSelectionTest` (6): `"ai"` sem sobreposição no pipeline, candidato curto, sugestão da IA como teto, resumo na mensagem da etapa, nada selecionável → `FAILED` + etapas seguintes `CANCELLED` + temporário apagado, preset desconhecido falha antes da extração.
- Fixtures antigas (`MediaPipelineExecutionTest`, `MediaPipelineCancellationTest`) usavam candidato de 2 s; passaram a 4 s, por causa do mínimo de 3 s (vídeo de 10 s, transcrição ampliada).

## Resultado desta rodada (sandbox)
- **Passou:** 104 testes JVM do `:domain` (Kotlin 2.0.21, coroutines do compilador; `runTest` substituído por shim local com `runBlocking`; `TranscriptCodec` fora, como nas fases anteriores). Um `ProcessRunnerTest` falhou uma vez no meio do caminho e passou em 3 reexecuções isoladas e na execução completa; é temporização de processo, não relacionado a esta fase, mas vale observar no CI.
- **Não compilado:** `:feature-settings`, `:feature-projects` e demais módulos Android (sem Android SDK/Maven). As duas edições de UI são pequenas e foram revisadas à mão.

## Gate para concluir a Fase 5
1. `./gradlew :domain:test` verde no CI.
2. `:feature-settings` e `:feature-projects` compilam no CI.
3. Manual: analisar com preset `ai` e conferir que os Shorts sugeridos não se sobrepõem; preset 15 s num vídeo sem trecho curto → falha clara em "Seleção de candidatos".

## Decisões e limitações
- Mínimo de 3 s e teto de 90 s para `"ai"` são constantes de `SelectionRules`; não há tela para configurá-las.
- A sugestão da IA vira **teto rígido**. Se a IA sugerir 20 s e devolver candidatos de 45 s, todos são rejeitados e a análise falha com o resumo dos motivos. Se isso aparecer no uso real, o ajuste é tratar a sugestão como preferência (ex.: tolerância de 1,5×); mantive o que o plano pede.
- `CandidateScorer` continua recebendo `preset.maxMs` (nulo no `"ai"` → ajuste de duração 1,0). O ramo `ratio > 1 → 0` ficou redundante com o selector e foi mantido por segurança.
- O pipeline sem `VideoEngine` não conhece a duração do vídeo, então não limita o fim do candidato nesse caso.
