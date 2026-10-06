# Fase 7 — Export e Batch robustos

**Status: implementada (7.1 a 7.8). Validado localmente: SQL da migração 4 → 5 em SQLite real e verificações estáticas. NÃO compilado nem executado: Gradle/Android SDK/Kotlin indisponíveis offline. Gates pendentes: build, `:domain:test`, instrumentado e aparelho arm64.**

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 7.1 | Nome estável `exports/<projectId>/<shortId>_<platform>_<quality>_<res>_<fps>_<fingerprint>.mp4`, escrita em `.part` + rename atômico; falha/cancelamento apagam só o `.part` do próprio export | concluído |
| 7.2 | Reuso de `done` só com `fingerprint` igual **e** arquivo validado por `probe` | concluído |
| 7.3 | Export usa `focusTrackJson` persistido; recalcula só se ausente | concluído |
| 7.4 | Export usa `subtitlesJson` persistido; fallback para o transcript só se ausente | concluído |
| 7.5 | Estilo de legenda respeitado + preset `ORIGINAL` removido da UI | concluído |
| 7.6 | Lote persistido: `export_batches` + migração 4 → 5 + progresso por item (throttle ~1 s) e do lote + retomada via `getResumableByProject` | concluído |
| 7.7 | `ExportWorker` fiel (failure/partial/success, mensagens) + `setForeground` + permissões no manifest | concluído |
| 7.8 | Cancelamento: remover flag `cancelled` remanescente + token por lote | concluído |

## 7.1 — O que mudou
- `ExportFileNaming` (domain, puro): `directory`, `fingerprint` (FNV-1a de intervalo + `intervalVersion` + plataforma + qualidade + resolução + fps; 10 hex), `fileName`, `partName`.
- `ShortsProcessingManager`: saída em `filesDir/exports/<projectId>/`; o FFmpeg grava em `<nome>.mp4.part`; `probe` valida o `.part`; `Files.move(ATOMIC_MOVE)` promove ao nome final; `.part` residual do próprio export é apagado antes de iniciar. Falha e cancelamento apagam somente o `.part` (antes `outputFile.delete()` podia apagar um `done` anterior).
- `FfmpegVideoEngine.clipArgs`: `-f mp4` explícito (a extensão `.part` não permite inferir o formato).

ASSUMINDO: o fingerprint de 7.1 cobre intervalo + parâmetros; estilo de legenda e foco entram no 7.2/7.3.

## 7.2 — O que mudou
- `ExportOutputValidator` (domain, puro): resolução, duração (tolerância 1 s) e áudio; usado no export novo e no reuso.
- Reuso exige: linha `done`, `outputPath` igual ao caminho com o fingerprint atual, arquivo existente e `probe` válido. Probe com erro ou arquivo inválido: apaga o arquivo (nome próprio) e regera. Cancelamento no probe é propagado.

ASSUMINDO: fingerprint comparado pelo nome do arquivo (sem nova coluna/migração Room).

## 7.3 — O que mudou
- `ShortsProcessingManager`: `CandidateArtifactsCodec.decodeFocusTrack(candidate.focusTrackJson)` primeiro; `detectFocusTrack` só quando ausente/corrompido (`null`). Falha na detecção continua virando foco central; cancelamento propagado.

## 7.4 — O que mudou
- Legendas vêm de `CandidateArtifactsCodec.decodeSubtitles(subtitlesJson)`; `[]` é resultado válido (sem fala). Só `null` (ausente/corrompido) recalcula do transcript via `SubtitleTiming.fromTranscript`.
- Removida a leitura da tabela legada `subtitles` e a dependência `SubtitleRepository` do manager (injeção Hilt, sem outros construtores).

## 7.5 — O que mudou (estilo de legenda)
- Estilo de legenda (`SecureKeyStore.subtitleStyle()`) → `ExportViewModel` → `ShortsWorkScheduler.enqueueExport(subtitleStyle)` → `WorkKeys.SUBTITLE_STYLE` → `ExportWorker` → `exportBatch(subtitleStyle)`; antes o manager fixava `"creator"`.
- O estilo entra no fingerprint (nome do arquivo): trocar o estilo gera novo export e não reaproveita o antigo. `ExportFileNaming.fingerprint` ganhou o parâmetro `subtitleStyle` (default vazio).
- `ExportViewModel` agora injeta `SecureKeyStore` (já provido por `DataModule`).

ASSUMINDO: `ExistingWorkPolicy.KEEP` do export mantém o estilo da execução em andamento.

- **`ORIGINAL` removido:** `ResolutionPreset.ORIGINAL` (0×0) caía silenciosamente em 1080×1920, rótulo enganoso. Removido do modelo e da lista da UI (export e configurações); o manager usa largura/altura do preset diretamente. Preferência salva com o rótulo antigo cai em `FULL_HD` (fallback já existente).

## 7.6 — O que mudou
- **`export_batches`** (`ExportBatchEntity`, FK CASCADE, índice em `projectId`): `total`, `completed`, `failed`, `cancelled`, `state` (`queued|running|done|partial|failed|cancelled`). Room versão 5, `MIGRATION_4_5` (registrada no `DataModule`), `ExportBatchDao`, `ExportBatchRepository` (`start` retoma o último lote aberto; contadores recomeçam).
- `ExportBatchState.resolve(total, done, failed)` (domain, puro): todos ok → `done`; mistura → `partial`; nenhum ok → `failed`.
- `ShortsProcessingManager`: grava o lote após cada item; cancelamento do usuário → `cancelled` (restantes em `cancelled`), parada do sistema → `queued`, exceção fora do item → `failed`. Gravação em `NonCancellable`.
- **Progresso por item**: `exports.progress` gravado no máximo a cada ~1 s (canal conflated + escritor dentro de `coroutineScope`; o callback do engine não suspende).
- **Retomada**: `ExportViewModel.load` mostra o último lote persistido e, se ele está aberto sem trabalho ativo no WorkManager, reenfileira com plataforma/qualidade/resolução/fps de `getResumableByProject` e o estilo atual das configurações.

ASSUMINDO: uma exportação por projeto por vez (já garantido pelo trabalho único `export:<projectId>`); retomada reaproveita os parâmetros do primeiro export pendente.

## 7.7 — O que mudou
- `ShortsProcessingManager.exportBatch` retorna `ExportBatchResult(total, done, failed, error)`; projeto ou candidatos ausentes viram `error` (antes `return` silencioso = "sucesso").
- `ExportWorker`: `error` → `Result.failure` com mensagem; todos falharam → `failure` ("Todas as N exportações falharam.") com contagens; parcial/total → `success` com `TOTAL`/`DONE_COUNT`/`FAILED_COUNT` em `outputData`. Exceções inesperadas seguem retry (máx. 2) e depois `failure`. A UI mostra "X de N exportações falharam." quando há falhas.
- Foreground: `getForegroundInfo` + `setForeground` no início (notificação em canal `export_progress`, `ExportNotification`); `IllegalStateException` (início de serviço não permitido) segue sem foreground. Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` e `SystemForegroundService` com `foregroundServiceType="dataSync"` (`tools:node="merge"`).

ASSUMINDO: tipo `dataSync` (em vez de `mediaProcessing`, API 35+) por compatibilidade com API 29–34; ícone da notificação reaproveita `ic_launcher`. `POST_NOTIFICATIONS` não é solicitado em runtime (sem a permissão a notificação fica oculta, mas o serviço roda).

## 7.8 — O que mudou
- Flag `cancelled` de instância: já removida na Fase 3; verificado por busca (nenhuma `var cancelled` em código de produção).
- **Token por lote:** o `id` do lote (`export_batches`) é o token; `ExportBatchRepository.record` só grava se o lote ainda é o atual. `cancelOpen(projectId)` fecha o lote aberto quando o usuário cancela (`ExportViewModel.cancel`), inclusive se o worker ainda estava na fila; sem isso a retomada do 7.6 reenfileiraria um lote cancelado. Cancelar mata o ffmpeg do item (Fase 3) e preserva os `done`.

## Fechamento da fase — validação
- **Gradle/Kotlin/SDK indisponíveis (offline):** `:domain:test`, `:data:testDebugUnitTest`, lint e builds NÃO foram executados.
- **Executado:** SQL de `MIGRATION_4_5` em SQLite real (colunas, FK `CASCADE`, índice, órfão barrado, cascata); balanceamento de chaves/parênteses em todos os `.kt` (3 arquivos acusados — `GrokProvider`, `ShortRangeValidator`, `FocusCropExpression` — não foram tocados; falso positivo do verificador); conferência de imports/símbolos novos; diff contra o ZIP da Fase 6 (só arquivos esperados).
- **Testes escritos, não executados:** `ExportFileNamingTest`, `ExportOutputValidatorTest`, `ExportBatchStateTest` (domain).
- Regressão por leitura: `RepositoriesTest` usa fake de `ExportDao` (interface não alterada); `ShortsDatabaseMigrationTest` ajustado para incluir `MIGRATION_4_5`; nenhum teste referenciava `ResolutionPreset.ORIGINAL`.

## Gate para concluir a Fase 7
1. `./gradlew :domain:test :data:testDebugUnitTest` verde; compilar `:data`, `:feature-export`, `:app`; versionar `data/schemas/5.json`.
2. `:data:connectedDebugAndroidTest` (migrações 1→5) em emulador.
3. Aparelho arm64: 3 candidatos com o 2º falhando → 1º e 3º `done`, 2º `failed`, lote `partial`; reabrir mostra o mesmo progresso; cancelar no meio mata o ffmpeg e preserva os `done`; notificação de foreground visível (API 34+).

## Pendências
- `data/schemas/5.json` será gerado no primeiro build (KSP) e deve ser versionado; teste de migração 4 → 5 não escrito (regra: sem testes novos até fechar a fase).
- Não compilado (sem Android SDK). Risco principal: `Files.move` com `ATOMIC_MOVE` (API 26+, ok com minSdk 26).
- Arquivos legados com nome por título não são removidos (limpeza prevista com o `.part`/`localPath` nos próximos submódulos).
