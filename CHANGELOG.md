# Changelog

## Fase 10 — QA e release (Fase 10 implementada)
- 10.4 `ShortsProcessingManagerTest` (4 testes JVM com DAOs falsos: falha parcial, reuso, intervalo editado, cancelamento); README/Roadmap alinhados. Estes testes do módulo Android aguardam a CI.
- 10.3 `proguard-rules.pro` (Room, serialization, WorkManager worker, Tink, atributos de stack trace); CI/README com `lintDebug lintRelease`. R8 não executado.
- 10.2 Saída do ffmpeg só vai ao logcat em build debuggable; tail de erro já limitado a ~2 KB (verificado). Não compilado/testado.
- 10.1 `allowBackup=false` + `data_extraction_rules.xml` (cloud-backup e device-transfer sem dados do app). Não compilado/testado.
- Validação local integrada: `:domain:test` passou com 139 testes, 0 falhas/erros; `tools/ffmpeg-validation/validate_crop.sh` passou 5/5 verificações com FFmpeg real; checksums FFmpeg e `git diff --check` passaram.
- CI: incluído `lintRelease`; AVD API 35 reduzido a `disk-size: 4G` após 6G ainda exceder o espaço do runner; teste de migração ajustado para verificar Room v5 (cadeia 1→5). Builds Android, lint/R8 e instrumentação aguardam a nova execução.
- Primeira CI do PR #3 (`37395753125`) encontrou um defeito no fixture de `VideoImporterDownloadTest`: `MockResponse.setBody()` redefinia o `Content-Length` depois do cabeçalho de 9 GB. Reordenada a configuração para preservar o tamanho anunciado e adicionada asserção de tipo explícita; reexecução pendente.

## Fase 9 — Importação (Fase 9 implementada)
- 9.4 `ImportPoliciesTest` (5) + `VideoImporterDownloadTest` com `MockWebServer` (9); `mockwebserver` e `isReturnDefaultValues` no módulo `data`. Escritos, não executados.
- 9.3 `ImportExtension` (MIME antes da URL); validação `probe` + `MediaValidator` antes de criar o projeto (arquivo inválido é apagado). Não compilado/testado.
- 9.2 `HttpResume`; retomada com `Range`+`If-Range` e `Content-Range` validado; parcial em `filesDir` com `.meta`; `Files.move(ATOMIC_MOVE)` no lugar de `copyTo`; download truncado mantém parcial. Não compilado/testado.
- 9.1 `ImportSpacePolicy` (limite 8 GiB + espaço livre); `VideoImporter` com timeouts, checagem de espaço (download e SAF), limite no SAF, parcial apagado em falha, `CancellationException` propagada. Não compilado/testado.

## Fase 8 — Trends (Fase 8 implementada)
- 8.4 `TrendResponseParser` (domain) extraído do `GrokProvider`; `TrendResponseParserTest` (5 testes JVM novos, escritos e não executados).
- 8.3 `relevanceScore` → `order` (ordem de chegada, sem pontuação fingida); UI "Ordem: #n"; `platform == "grok"` não chama a IA duas vezes. Não compilado/testado.
- 8.2 `TrendCardInfo`: selo de origem nos cards; métricas só com `OFFICIAL_API`; `aiEstimate` rotulado "Estimativa da IA". Não compilado/testado.
- 8.1 `TrendOrigin`/`ProviderCapability`; `TrendCard` com `origin`, `metricsVerified`, `aiEstimate` e invariantes (métricas só com `OFFICIAL_API`); `TrendProvider.capability` substitui `isAvailable()`; `GrokProvider.parseTrends` não copia `views`/`engagement` do LLM (vão para `aiEstimate`). Não compilado/testado.

## Fase 7 — Export e Batch (Fase 7 implementada)
- 7.8 Token por lote (`id` do lote guarda as gravações), `cancelOpen` ao cancelar (evita retomar lote cancelado); flag `cancelled` de instância já inexistente. 3 testes JVM novos escritos (não executados); SQL da migração 4 → 5 validado em SQLite real.
- 7.7 `exportBatch` retorna `ExportBatchResult`; `ExportWorker` fiel (failure/partial/success + contagens), `setForeground` com notificação e permissões/serviço `dataSync` no manifest. Não compilado/testado.
- 7.6 `export_batches` (Room v5, `MIGRATION_4_5`), estado do lote (`ExportBatchState`), progresso por item com throttle ~1 s e retomada ao reabrir o app via `getResumableByProject`. Não compilado/testado.
- 7.5 Preset `ORIGINAL` (0×0, caía em 1080×1920) removido do modelo/UI.
- 7.5 Estilo de legenda das configurações chega ao export (UI → scheduler → worker → manager) e entra no fingerprint. Não compilado/testado.
- 7.4 Export usa `subtitlesJson` persistido; transcript só como fallback; leitura da tabela legada `subtitles` removida do manager. Não compilado/testado.
- 7.3 Export usa `focusTrackJson` persistido; recalcula só se ausente. Não compilado/testado.
- 7.2 Reuso de export `done` só com fingerprint igual (nome do arquivo) e `probe` válido (`ExportOutputValidator`); inválido é regerado. Não compilado/testado.
- 7.1 Nome estável por export (`ExportFileNaming`), saída em `.part` + rename atômico, falha/cancelamento apagam só o `.part`; `-f mp4` explícito no engine. Não compilado/testado.

## Fase 6 — Persistência, Editor e Room (Fase 6 implementada)
- 6.7 `exportError` só muda em transição explícita; 6.6 `completedAtMs` zerado em retry.
- 6.5 Editor: `ShortRangeValidator` + `ProjectStore.updateShort` (valida, persiste `hook`, invalida export ao mudar o intervalo); tela mostra erro e só volta ao salvar.
- 6.4 `ProjectStore.saveAnalysis` transacional; re-análise substitui candidatos.
- 6.3 `intervalVersion`, `focusTrackJson`, `subtitlesJson` persistidos.
- 6.2 Migração 3 → 4 (recria tabelas, descarta órfãos), `exportSchema = true`.
- 6.1 FKs com CASCADE e índices; `REPLACE` removido dos inserts.
- 7 testes JVM novos (111 em `:domain`); SQL da migração validado em SQLite real; testes Room instrumentados escritos, não executados.

## Fase 5 — Seleção determinística (Fase 5 implementada)
- 5.5 `selectWithReport` + resumo na etapa; seleção vazia falha explicitamente (sem retry).
- 5.4 Desempate determinístico (score, `startMs`, `endMs`).
- 5.3 Preset `ai`: teto = sugestão da IA (limitada) ou 90 s.
- 5.2 `DurationPreset.fromKey` único; preset desconhecido falha em vez de cair em 30 s.
- 5.1 Mesmas regras para todos os presets: dentro do vídeo, mínimo 3 s, teto, sem sobreposição, limite.
- 29 testes JVM novos; 104 testes de `:domain` passaram localmente.

## Fase 4 — Pipeline e validação de entrada (Fase 4 implementada)
- 4.7 Provedor/modelo reais gravados; 4.6 transcrição cancela a chamada HTTP; 4.5 retry tipado (`AiException`/`AiHttpException`).
- 4.4 `AiResponseSanitizer`; 4.2 temporários em `cache/analysis/<projectId>/`; 4.1 validação de mídia dentro do pipeline.
- 16 testes JVM novos; 78 testes de `:domain` passaram localmente.

## Fase 3 — Cancelamento e estados corretos (Fase 3 implementada)
- 3.6 Workers: cancelamento do usuário (`cancelled`) vs parada do sistema (`queued`); `cancel()` global removido do ViewModel; teste instrumentado de cancelamento.
- 3.5 `probe`/`isAlreadyTargetFormat` suspend com timeout; 3.4 `runCatching`/`catch` não engolem mais `CancellationException`.
- 3.3 Estado final em `NonCancellable` (pipeline, manager, workers); flag `cancelled` do manager removida.
- 3.1/3.2 `ProcessRunner` (processo preso ao coroutine), `FfmpegFailedException`/`FfmpegCancelledException`; `VideoEngine.cancel()` removido.
- 18 testes JVM novos (`ProcessRunnerTest`, `MediaPipelineCancellationTest`); 62 testes de `:domain` passaram localmente.

## Fase 2 — 2.5 (Fase 2 implementada)
- Testes golden: `FfmpegFilterBuilderTest`, `SubtitleTimingTest`, `FocusTrackBuilderTest` (17 casos novos; 47 testes de `:domain:test` passaram localmente).
- Corrigida referência suspendida a `update` em `MediaPipeline.cancelPendingStages`; arquivo temporário de áudio alinhado a `.m4a` com limpeza no `finally`.

## Fase 2 — 2.4
- `detectFocusTrack` com uma chamada `fps=1`; `FocusTrackBuilder` (tempo correto, método por detecções, suavização); `FaceDetector` fechado por chamada.

## Fase 2 — 2.3
- Legendas por PNG + `overlay` (`SubtitleBitmapRenderer`, `FfmpegFilterBuilder.filterGraph`); `drawtext` removido (ausente no binário).

## Fase 2 — 2.2
- `SubtitleTiming` (fromTranscript/toClipRelative); legendas em tempo relativo ao clipe, `y` fixo, `expansion=none`, escape validado; duplicações no pipeline e no export removidas.

## Fase 2 — 2.1
- `FfmpegFilterBuilder` (domain) monta crop/scale/fps; `processClip` delega ao builder.

## Fase 1 — 1.4 (Fase 1 implementada)
- `FfmpegBinaryInstrumentedTest` (app/androidTest): version, encoders, filters e clipe de 2 s em arm64; ignorado fora de arm64.

## Fase 1 — 1.3
- Procedência/versão/licença/checksum dos binários FFmpeg documentados; `SHA256SUMS` verificado no CI; `THIRD_PARTY_NOTICES.md`.

## Fase 1 — 1.2
- Codecs fechados em `FfmpegCodecs`: vídeo `h264_mediacodec`, áudio `aac`; áudio de análise `.m4a` (sem libx264/libmp3lame).
- MIME de transcrição `audio/mp4`; configuração do binário registrada em `tools/ffmpeg-binary/`.

## Fase 1 — 1.1
- Binários FFmpeg/ffprobe movidos de `assets` para `jniLibs/arm64-v8a` (`libffmpeg.so`, `libffprobe.so`).
- `useLegacyPackaging = true`; engine executa de `nativeLibraryDir`; `AssetExtractor` removido.

## Fase 0
- Ver `docs/fases/FASE-00-DESBLOQUEAR-BUILD-E-CI.md`.
