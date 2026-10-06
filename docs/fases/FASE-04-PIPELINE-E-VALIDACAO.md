# Fase 4 — Pipeline e validação de entrada

**Status: implementada (4.1 a 4.7). `:domain` (78 testes JVM) passou localmente; compilação dos módulos Android e CI completo permanecem pendentes.**

Objetivo: nenhum estágio roda com entrada inválida, e a saída da IA é saneada.

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 4.1 | `MediaValidator` dentro de `MediaAnalysisPipeline.validateInput`; worker só delega | concluído |
| 4.2 | Áudio/fragmentos em `cacheDir/analysis/<projectId>/`, apagado inteiro no `finally` | concluído |
| 4.3 | Formato de áudio: `.m4a` (AAC) / `audio/mp4`, já fechado na Fase 1 e aceito pela OpenAI | concluído (sem mudança) |
| 4.4 | `AiResponseSanitizer`: descarta candidatos inválidos e registra quantos/por quê | concluído |
| 4.5 | Retry por tipo (`AiException.transient`, `AiHttpException(code)`), nunca por substring | concluído |
| 4.6 | Transcrição cancelável: `Call.cancel()` via `suspendCancellableCoroutine` | concluído |
| 4.7 | Provedor/modelo que de fato responderam gravados no banco | concluído |

## O que mudou
- **4.1** `validateInput` (pipeline): caminho, arquivo existente e não vazio e, com motor de vídeo, `probe` + `MediaValidator` (duração, resolução, áudio). Falha em `VideoInput` antes de extrair áudio. `AnalysisWorker` perdeu a validação duplicada e a dependência de `VideoEngine`.
- **4.2** `MediaAnalysisPipeline(audioWorkDir)`; `AnalysisPipelineFactory.create(dir)`; o worker passa `cacheDir/analysis/<projectId>`. O áudio e `transcription_chunks` ficam ali e `workDir.deleteRecursively()` roda em sucesso, falha e cancelamento. Sem `audioWorkDir`, usa `.analysis_<arquivo>` ao lado do vídeo (também apagado). Sem concatenação/`replaceLast` de caminho.
- **4.4** Regras (`DiscardReason`): sem título; início/fim ausentes (`ShortCandidate.MISSING_MS`, usado pelo parser em vez de `0`); intervalo inválido (`start<0` ou `end<=start`); fim > duração do vídeo; fora da região transcrita (folga de 1 s). O resumo vai na mensagem da etapa (`"1 válidos, 3 descartados (…)"`) e a lista em `AnalysisOutcome.Success.discarded`. Nenhum candidato válido → `Failed` sem retry. Transcrição sem fala → `Failed` em `Transcription`. Pontuação e seleção só veem candidatos saneados.
- **4.5** `domain/ai/AiExceptions.kt`: `AiException(transient)`, `AiHttpException(code)` (408/429/5xx transitórios), `Throwable.isTransientFailure()` (também `IOException`). `GrokProvider`/`MultiAIProvider` lançam `AiException` com `cause` e `transient` = alguma tentativa foi transitória. `AnalysisOutcome.Failed.retryable`; o worker usa isso e `isTransient(message)` foi removido.
- **4.6** `OpenAiTranscriptionService.requestChunk` é `suspend`: `enqueue` + `invokeOnCancellation { cancel() }`, corpo lido no callback; HTTP de erro → `AiHttpException`.
- **4.7** `AIAnalysisResult.provider/model`; `GrokProvider` preenche com o modelo que respondeu (`withFallbackTracked`), `MultiAIProvider` completa o provedor. O worker grava `"<provedor> · <modelo>"` na coluna `provider` (sem migração; coluna dedicada fica para a Fase 6).

## Testes novos (`./gradlew :domain:test`)
- `AiResponseSanitizerTest` (7): cada motivo, limites (fim == duração passa, folga de 1 s), duração desconhecida, resumo por motivo, classificação transitória (429/408/503 sim; 401/404, mensagem com "timeout"/"http 503" não).
- `MediaPipelineInputAndAiTest` (9): duração 0, sem áudio, resolução 0, arquivo vazio (sem extrair áudio); **cada estágio falhando** (FAILED nele, CANCELLED nos seguintes, temporário apagado); transcrição vazia; candidatos fora do vídeo/sem título/sem início descartados e contados; nenhum válido; retry tipado; provedor/modelo preservados; temporário apagado no sucesso.
- Testes antigos ajustados: transcrição com fala (a regra nova rejeita vazia) e `audioWorkDir`.

## Resultado desta rodada (sandbox)
- **Passou:** 78 testes JVM do `:domain` (mesma configuração da Fase 3: Kotlin 2.0.21 + coroutines 1.6.4 do Gradle, `runTest` local; `TranscriptCodec` fora).
- **Não compilado:** `:feature-ai`, `:data`, `:app` (sem Android SDK/Maven). Edições revisadas à mão.

## Gate para concluir a Fase 4
1. `./gradlew :domain:test` verde no CI.
2. `:feature-ai`, `:data`, `:app` compilam no CI.
3. Manual: analisar vídeo sem áudio → falha imediata em "Importação do vídeo"; cancelar durante a transcrição → requisição HTTP cai na hora; pasta `cache/analysis/<id>` some ao fim.

## Não verificado / limitações
- Sem teste JVM de `OpenAiTranscriptionService` e `GrokProvider` (módulos Android; `MockWebServer` entra na Fase 9/10).
- `GrokProvider` usa `HttpURLConnection` bloqueante: cancelar interrompe só depois da resposta/timeout de 30 s. Migrar para OkHttp com `Call.cancel()` fica pendente.
- O modelo/provedor está concatenado numa coluna de texto até a migração da Fase 6.
- O parser ainda aceita `startMs`/`endMs` só como inteiros em texto; valores decimais viram "ausente" e o candidato é descartado.
