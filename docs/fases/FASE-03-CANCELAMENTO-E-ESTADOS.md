# Fase 3 — Cancelamento e estados corretos

**Status: implementada (3.1 a 3.6). `:domain` (62 testes JVM, incluindo processos reais) passou localmente; compilação dos módulos Android, CI completo e o teste instrumentado em arm64 permanecem pendentes.**

Objetivo: cancelar significa cancelar, e o estado gravado reflete a etapa real.

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 3.1 | `FfmpegFailedException(exit, stderrTail)` e `FfmpegCancelledException` distintas; processo preso ao coroutine (`ProcessRunner`) | concluído |
| 3.2 | `cancel()` global removido do `VideoEngine`; cancelamento só pelo `Job` do dono (token por job) | concluído |
| 3.3 | Estado final gravado em `withContext(NonCancellable)` (pipeline, manager, workers) | concluído |
| 3.4 | `runCatching`/`catch (Exception)` que engoliam cancelamento substituídos | concluído |
| 3.5 | `probe` e `isAlreadyTargetFormat` viram `suspend` (IO, timeout de 30 s, cancelável) | concluído |
| 3.6 | Workers distinguem cancelamento do usuário de parada do sistema; testes JVM + 1 instrumentado | concluído (instrumentado não executado) |

## O que mudou

### 3.1 / 3.2 — Processo preso ao coroutine
- `domain/.../pipeline/ProcessRunner.kt` (novo, JVM puro, testável): `withContext(Dispatchers.IO)` + `coroutineScope` + `try/finally { destroyForcibly() }`. `waitFor` roda em `runInterruptible` com `withTimeoutOrNull`. O `finally` mata o processo **antes** do `coroutineScope` esperar o leitor (que só termina com o EOF) — sem isso, cancelar travaria.
- O leitor ignora `IOException` do fluxo fechado: uma exceção real ali substituiria o `CancellationException` no `coroutineScope` e o cancelamento viraria "falha".
- Saída guardada: só o final (≤ 2 KB, `FfmpegFailedException.MAX_TAIL_CHARS`); linhas de progresso `-stats` ficam fora do final (`tailFilter`).
- `FfmpegVideoEngine`: `CoroutineScope(...).launch` solto, `runningProcess` global, `cancel()` e `runBlocking` removidos. `runFfmpeg`/`runFfprobe` delegam ao `ProcessRunner`. Código de saída ≠ 0 → `FfmpegFailedException`; se o dono já foi cancelado → `FfmpegCancelledException` (é `CancellationException`).
- `VideoEngine.cancel()` removido da interface. O `Mutex` do engine continua serializando execuções, mas não guarda processo.

### 3.3 — Estado final em `NonCancellable`
- `MediaAnalysisPipeline.analyze`: ao cancelar, a etapa interrompida vira `CANCELLED` e as seguintes `CANCELLED`, dentro de `NonCancellable` (o callback `onStageUpdate` é suspend e falhava no coroutine cancelado). Cancelamento na fronteira entre etapas marca a **próxima** etapa e preserva a anterior `COMPLETED` (`begin()` com `ensureActive()`).
- `TimeoutCancellationException` com o coroutine ainda ativo é **falha** da etapa, não cancelamento.
- Áudio temporário apagado no `finally` (variável única `audioPath`).
- `ShortsProcessingManager`: flag `cancelled` de instância e `cancel()` removidos. Marcações `queued/running/processing` agora ficam dentro do `try`; qualquer `CancellationException` (ou exceção comum com o coroutine já cancelado, ex.: `IOException` do processo morto) grava o estado em `NonCancellable`. Item cancelado **não** conta como concluído; `isRunning = false` é emitido ao cancelar.

### 3.4 — Cancelamento nunca engolido
- `ShortsProcessingManager`: `runCatching` do foco → `try/catch` que relança `CancellationException` (falha comum → foco central).
- `AnalysisWorker` (probe), `OpenAiTranscriptionService` (probe do fragmento), `EditorViewModel.saveMetadata`, `ProjectViewModel.setSource`, telas Trends/Radar e Grok Settings: `catch (Exception)` agora relança `CancellationException`.
- `runCatching` que restam envolvem só código puro/não-suspend (parse JSON, `valueOf`, `startActivity`, `listAvailableTextModels` bloqueante) e não podem engolir cancelamento.

### 3.5 — `probe` suspend
- `VideoEngine.probe` e `isAlreadyTargetFormat` são `suspend`; `ffprobe` roda no `ProcessRunner` com timeout de 30 s. `MediaAnalysisPipeline.validateInput` e `fallbackTranscript` viram `suspend`.

### 3.6 — Workers
- `WorkerStop.kt`: `ListenableWorker.cancelledByApp()` = `stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP`.
- `AnalysisWorker.recordStop`: cancelamento do usuário → `cancelled`; parada do sistema (restrição, preempção) → `queued` (o WorkManager reagenda; marcar `cancelled` mentiria). Gravação em `NonCancellable`; `videoEngine.cancel()` removido.
- `ExportWorker`: repassa `cancelledByUser = { cancelledByApp() }` ao manager; não converte cancelamento em `retry`/`failure`.
- `ProjectViewModel`: `videoEngine.cancel()` removido de `cancelAnalysis` e de `onCleared` (este último matava ffmpeg de uma análise que roda no WorkManager e sobrevive ao ViewModel).

## Testes (rodam em `./gradlew :domain:test`)
- `ProcessRunnerTest` (8, processos reais `sh`/`sleep`, ignorado no Windows): cancelar mata o processo em < 2 s; cancelar um não mata o de outro dono; cancelamento lança `CancellationException` (nunca `FfmpegFailedException`); tempo limite mata e lança `timedOut`; exit ≠ 0 devolve código e stderr; final limitado a 2 KB mantendo as últimas linhas; `tailFilter`; binário inexistente.
- `MediaPipelineCancellationTest` (10): cancelar no meio de **cada** estágio com fakes que suspendem e `onStageUpdate` com `delay(1)` — resultado `Cancelled`, estágio interrompido `CANCELLED`, posteriores `CANCELLED`, anteriores `COMPLETED`, nenhum `FAILED`, áudio temporário apagado; fronteira entre estágios; erro comum continua `Failed`; timeout interno ativo é `Failed`.
- Verificado por mutação: sem `NonCancellable` na gravação do estado, 7 testes do pipeline falham; sem o `destroyForcibly` no `finally`, os testes de cancelamento do `ProcessRunner` falham.
- Instrumentado (`FfmpegBinaryInstrumentedTest.cancellingTheOwnerKillsTheRealFfmpegProcessWithinTwoSeconds`): ffmpeg real com fonte infinita; `process` morto em < 2 s após cancelar. **Escrito, não executado.**

## Resultado desta rodada (sandbox)
- **Passou:** 62 testes JVM do `:domain` (inclui os 18 novos; os 3 de `TranscriptCodec` ficaram de fora, ver abaixo). Compilados com o Kotlin 2.0.21 e coroutines 1.6.4 que acompanham o Gradle, com um shim local de `runTest` — o sandbox não alcança Maven Central/Google Maven nem tem Android SDK. `TranscriptCodec` (kotlinx.serialization) ficou de fora desta execução local.
- **Não compilado:** `:video-engine`, `:feature-export`, `:data`, `:app` (sem Android SDK). As edições foram revisadas à mão; o CI é quem confirma.

## Gate para concluir a Fase 3
1. `./gradlew :domain:test` verde no CI (com `kotlinx-coroutines-test` 1.9.0 real).
2. Compilação de `:video-engine`, `:feature-export`, `:data`, `:app` verde no CI.
3. Em aparelho arm64: `FfmpegBinaryInstrumentedTest` (incluindo o teste de cancelamento) verde.
4. Manual: cancelar exportação no meio → item `cancelled` na UI, ffmpeg some, itens `done` preservados.

## Não verificado / limitações
- `WorkInfo.STOP_REASON_CANCELLED_BY_APP` (WorkManager 2.10.0) não foi conferido por compilação; se o nome divergir, o ajuste é só em `WorkerStop.kt`.
- `ProcessRunner` assume que o ffmpeg não cria processos filhos (cancelar mata só o PID). Se algum dia o binário forkar, o leitor poderia esperar o EOF de um órfão.
- `ShortsProcessingManager` ainda apaga `outputFile` em falha/cancelamento e usa nome de arquivo por título: isso é a Fase 7 (`.part` + fingerprint).
- Transcrição: `client.newCall(...).execute()` continua bloqueante e só é interrompida ao fim da chamada — `Call.cancel()` via `suspendCancellableCoroutine` é Fase 4.
- Sem teste JVM do `ShortsProcessingManager` (depende de Android `Context`/Room); a cobertura dele é o teste manual do gate 4.
