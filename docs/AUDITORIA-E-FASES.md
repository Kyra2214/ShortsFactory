# ShortsFactory — Auditoria e fases de implementação

> **Nota de escopo:** este documento é uma auditoria estática histórica do snapshot citado abaixo. Suas constatações descrevem aquela base e não devem ser tratadas como fotografia da branch atual; consulte `docs/VALIDACAO-INTEGRACAO-FASE-02.md` e `docs/fases/` para o estado e as evidências mais recentes.
> Base auditada: `ShortsFactory-main.zip` (~7.000 linhas Kotlin, 12 módulos).
> **Fonte da verdade:** apenas o conteúdo do zip. Nada de PRs, branches ou execuções de CI externas foi considerado.
> Método: leitura estática do código. Nada foi compilado nem executado (sem Android SDK no ambiente). Itens marcados **[confirmado]** são evidentes no código ou no binário. Itens marcados **[verificar]** dependem de rodar.

---

## 1. Resumo executivo

| # | Achado | Gravidade |
|---|--------|-----------|
| 1 | `FfmpegVideoEngine.kt:266` usa `$t1`, que não existe. **Erro de compilação** do módulo `video-engine`. | P0 |
| 2 | O ffmpeg **não executa em aparelho real** (targetSdk 35): o binário é extraído para `cacheDir` e executado de lá, e o Android 10+ bloqueia isso. O ramo `nativeLibraryDir` procura `ffmpeg`, mas nada empacota `libffmpeg.so`. | P0 |
| 3 | O binário embutido é `--disable-gpl` e **não tem libx264 nem drawtext/freetype**. O código usa `-c:v libx264` e `drawtext`. Exportação e legendas falhariam. | P0 |
| 4 | Legendas com tempo errado: usam tempo **absoluto** do vídeo-fonte, mas o filtro roda em tempo **relativo** ao clipe (`-ss` antes de `-i`). Além disso `y = ... - n*size/8` faz o texto subir a cada frame. | P0 |
| 5 | Cancelamento é engolido: o pipeline captura `CancellationException` e chama funções `suspend` (Room) no coroutine já cancelado. O estado `cancelled` pode nunca ser gravado. `runCatching` em volta de `detectFocusTrack` também engole cancelamento. | P0 |
| 6 | `engine.cancel()` mata o processo e `run()` lança `RuntimeException`, que vira `FAILED` se a flag `cancelled` não estiver ligada. A flag só existe no export. A análise não tem equivalente. | P1 |
| 7 | Seleção: com preset `"ai"` o `CandidateSelector` devolve `sorted.take(limit)` **sem remover sobreposição**. Não há validação contra a duração real do vídeo nem duração mínima. | P1 |
| 8 | Export: o nome do arquivo (`título_índice.mp4`) não inclui plataforma, qualidade, resolução nem intervalo. Exportações diferentes se sobrescrevem, e uma falha **apaga um arquivo de outra exportação já concluída**. | P1 |
| 9 | O reuso de export `done` ignora o intervalo. Se o usuário editar o corte no editor, o app reaproveita o vídeo antigo. O editor também não invalida `localPath` nem `status`. | P1 |
| 10 | Progresso agregado do export vai só para `setProgressAsync` (WorkManager). **Nada é persistido no Room** durante a execução. `ExportRepository.updateProgress` só é chamado em teste. | P1 |
| 11 | Re-análise duplica candidatos (`insertCandidates` sem limpar os antigos). Não há FK, índice nem cascade, então deletar projeto deixa órfãos. Transcript, análise e candidatos são gravados sem transação. | P1 |
| 12 | Trends: `GrokTrendProvider.isAvailable() = true`. `views` e `engagement` vindos do LLM aparecem na UI como "Visualizações" e "Engajamento", e a checagem `verifiedSource` só confere o que o próprio modelo declarou. | P1 |
| 13 | Export longo roda em `CoroutineWorker` sem `setForeground` e sem permissões/serviço de foreground. O SO pode matar jobs de vários minutos. | P1 |
| 14 | Migração Room: `exportSchema = false`, sem `room.schemaLocation`. O teste de migração é manual (sem `MigrationTestHelper`), então não valida o schema final contra o que o Room espera. | P2 |
| 16 | `testDebugUnitTest` não existe no módulo JVM `:domain`; seus testes nunca rodaram no CI. `MediaAnalysisPipelineTest` usava `"video.mp4"` inexistente e falharia. (Tratado na Fase 0.) | P0 |
| 15 | A pipeline deixa o `.mp3` temporário no disco (a README diz que é removido). `probe()` usa `runBlocking` e não cancela. OkHttp `execute()` não é cancelável. | P2 |

**Conclusão:** pelo código do zip, nenhuma fase do `docs/ROADMAP-12-FASES.md` pode ser considerada concluída. Os itens 1 a 5 impedem o app de compilar ou de produzir um Short correto. O item 1 deve quebrar `assembleDebug`, `lintDebug` e `testDebugUnitTest` dos módulos que dependem de `video-engine`.

---

## 2. Auditoria por área do checklist

### 2.1 CI (`.github/workflows/ci.yml`)
- **[confirmado]** O `ci.yml` chama `sdkmanager` direto, sem `android-actions/setup-android` e sem garantir o `cmdline-tools` no PATH. Isso é frágil. O workflow deve instalar o SDK de forma explícita (action `setup-android` com tag publicada e verificada) antes de qualquer `sdkmanager`.
- **[confirmado]** O workflow tem dois jobs (`unit-build-lint`, `instrumented`). Cada um instala o SDK sozinho. `instrumented` depende de `unit-build-lint`.
- **[confirmado]** O emulador no CI é `x86_64`. O ffmpeg embutido é `aarch64`. **Nenhum teste do CI executa o ffmpeg real.** Isso explica como os itens 2, 3 e 4 passam despercebidos.
- **[confirmado]** `permissions: contents: read` e `concurrency: cancel-in-progress: true` estão corretos. Um push novo cancela a run anterior; isso é esperado, mas pode parecer falha.
- **Regra para esta etapa:** não declarar fase validada enquanto os quatro gates não passarem juntos: JVM, lint, assemble (debug, release, androidTest) e instrumentation.

### 2.2 `MediaPipeline`
Cada estágio executa trabalho real (probe, extração, transcrição, IA, seleção, legendas, foco). Isso está certo e há teste para isso. Problemas:
- `validateInput` só checa existência do arquivo e chama `probe`. Não usa `MediaValidator` (duração, dimensões, áudio). Quem valida é o `AnalysisWorker`. Quem chamar a pipeline direto pula a validação.
- `audioPath = videoPath.replaceLast("video","audio") + ".mp3"` (linha 103) depende do nome do arquivo e não usa diretório temporário. O arquivo nunca é apagado.
- Linhas 145 a 150: `catch (CancellationException)` chama `update(...)` e `cancelPendingStages(...)`. O `onStageUpdate` do worker faz `projectRepository.updateAnalysisState` (suspend, Room). Em coroutine cancelado, isso lança `CancellationException` de novo. O estado `cancelled` não chega ao banco. O teste atual passa porque o callback não suspende.
- O resultado da IA não é validado: `parseAnalysis` aceita `startMs/endMs` ausentes como `0`. Não há checagem contra a duração do vídeo nem contra os limites da transcrição.
- A pipeline e o `CandidateSelector` tratam preset desconhecido de forma diferente: `else -> null` na pipeline e `else -> 30s` no selector.

### 2.3 `FfmpegVideoEngine`
**Comandos e filtros**
- **[confirmado]** Linha 266: `if(lt(t\\,$t1)...` com `$t1` indefinido. Não compila. O mesmo trecho tem `c1` não usado.
- A expressão de interpolação trata o primeiro segmento como constante (`c0`) até `t1`. Isso não é interpolação linear.
- A normalização `it.timeMs / lastT * durationSec` usa tempos **absolutos** do vídeo-fonte e, depois, os `anchors` ignoram o resultado e usam espaçamento uniforme por índice. O código de normalização é morto e errado.
- O crop `(iw-cw)*centerX` mapeia 0..1 para os extremos do vídeo. O correto é `centerX*iw - cw/2`, limitado a `[0, iw-cw]`. O rosto não fica centrado no recorte.
- Em `detectFocusTrack`, o `default` é criado com `timeMs = startMs` e reaproveitado nos pontos de falha. Esses pontos ficam com tempo errado.
- Cada segundo de amostragem abre **um processo ffmpeg** (até 90 por candidato), todos serializados pelo `mutex`. O correto é uma única chamada `-vf fps=1` gerando todos os frames.
- A detecção de `FACE_TRACKING` compara com `0.5/0.42` depois da suavização. Isso é frágil. Deveria vir de "pelo menos N rostos detectados".
- `faceDetector` nunca é fechado.

**Legendas**
- `buildSubtitleDraw` usa `seg.startMs/1000` direto. `buildSubtitles` (pipeline) e o fallback do export também cortam em tempo absoluto. Com `-ss` antes de `-i`, `t` começa em 0 no filtro. Para qualquer clipe que não comece em 0, as legendas aparecem fora de hora ou nunca. Falta subtrair `candidate.startMs`.
- `y=(h*pos/100)-n*size/8`: `n` é o número do frame. O texto sobe a cada frame.
- O escape de `'` (`\\\\'` em Kotlin vira `\\'`) não funciona dentro de string entre aspas simples do filtergraph. `%` e `\` não são escapados. **[verificar]** com ffmpeg real.
- **[confirmado]** A string `drawtext` ocorre 0 vezes no binário: o filtro `drawtext` não foi compilado (exige libfreetype, ausente da `configuration`). Todas as legendas por `drawtext` falhariam. Também não há `fontfile=` e o Android não tem fontconfig.
- Dezenas ou centenas de `drawtext` encadeados em um único `-vf` estouram o tamanho da linha de comando em vídeos longos. O ideal é usar arquivo de filtro (`-filter_script:v`) ou legendas ASS.

**Binário (`app/src/main/assets/ffmpeg`, `ffprobe`)**
- **[confirmado]** ELF aarch64, `--disable-gpl --disable-nonfree --enable-mediacodec`, decoders `h264/hevc/mpeg4_mediacodec`. Não há `--enable-libx264`.
- O código usa `-c:v libx264` e `-c:a aac`. **[verificar]** o encoder nativo `aac` existe (normalmente sim). `libx264` não existe nesse build. Trocar por `h264_mediacodec` (encoder) exige o build com encoder habilitado. Hoje só os decoders foram habilitados.
- Também há `libmp3lame` em `extractAudio` e `splitAudio`. **[confirmado]** a string `libmp3lame` aparece no binário, mas o `configure` não tem `--enable-libmp3lame`. **[verificar]** se o encoder realmente está presente (`ffmpeg -encoders`). Se não estiver, a extração de áudio falha. Alternativa segura: WAV/PCM ou AAC/M4A (a API da OpenAI aceita m4a e wav).
- **[confirmado]** Execução a partir de `cacheDir/ffmpeg_bin` (`AssetExtractor`). Em targetSdk ≥ 29 o SELinux nega `execute` em `app_data_file`. O ramo `nativeLibraryDir/ffmpeg` nunca acha o arquivo, porque só binários chamados `lib*.so` em `jniLibs/arm64-v8a` são extraídos para lá (e só com `useLegacyPackaging = true`). Não existe `jniLibs` no projeto.
- 30 MB de binários versionados diretamente. Sem checksum, sem origem rastreável, sem arquivo de licença (build de terceiros; caminho de máquina pessoal embutido no próprio binário, ver `tools/ffmpeg-binary/PROCEDENCIA-E-LICENCA.md`). Para distribuir o app é preciso resolver licença e procedência.

**Execução e cancelamento (`run`, `cancel`)**
- `cancel()` faz `runningProcess?.destroy()`. O `waitFor` volta com exit ≠ 0 e `run` lança `RuntimeException("FFmpeg falhou")`. O chamador não sabe que foi cancelamento.
- `cancel()` é global no singleton do engine. Cancelar exportação pode matar o processo de uma análise em andamento, e o inverso também.
- `CoroutineScope(Dispatchers.IO).launch` dentro de `suspendCancellableCoroutine` cria um escopo não estruturado. Funciona porque `invokeOnCancellation` destrói o processo, mas fica difícil de raciocinar. Melhor `withContext(Dispatchers.IO)` com `try/finally { process.destroyForcibly() }`.
- O `mutex` serializa tudo no engine inteiro, inclusive o `detectFocusTrack` de uma análise contra o export. Isso é aceitável, mas precisa ser decisão consciente.
- `probe()` não é `suspend` e usa `runBlocking`. Bloqueia a thread do worker e não responde a cancelamento.
- Não há `-nostdin`. **[verificar]** se o ffmpeg bloqueia esperando entrada.

**Validação do resultado:** o `ShortsProcessingManager` faz `probe` do arquivo final (resolução, duração, áudio). Isso está certo e deve ser mantido.

### 2.4 `AnalysisWorker`
- O worker valida a mídia **antes** de rodar a IA. Isso está certo e cumpre "não executar IA se o input já estiver inválido".
- O `catch (CancellationException)` do worker grava `cancelled` com `updateAnalysisState`, que é `suspend`. Em coroutine cancelado isso precisa de `withContext(NonCancellable)`.
- O `AnalysisOutcome.Cancelled` vira `Result.failure(...)`. Funciona, mas não distingue "cancelado pelo usuário" de "parado pelo sistema" (`isStopped`). Parada do sistema deveria reagendar.
- `isTransient` decide retry por substring na mensagem (`"timeout"`, `"http 503"`...). Frágil. Deveria ser exceção tipada.
- `aiAnalysisRepository.save(projectId, "configured", ...)`: o provider real (qual dos fallbacks respondeu) não é registrado.
- Salvar transcript, análise e candidatos não é atômico. Se `insertCandidates` falhar no meio, o estado fica parcial, mas `analysisStatus` pode ficar `running` para sempre.

### 2.5 Seleção (`CandidateSelector`, `CandidateScorer`)
- Bom: filtra `start < 0`, `end <= start`, duração acima do preset e sobreposição (quando há preset). `CandidateScorer` mistura IA 45%, hook 20%, densidade de fala 20%, adequação de duração 15%.
- Ruim: preset `"ai"` → `return sorted.take(limit)` sem checar sobreposição nem duração máxima nem mínima.
- Não há validação de `endMs <= duração do vídeo`.
- Sem desempate determinístico quando o score é igual (ordenar por `startMs`).
- `durationFit` dá `0f` acima do máximo, mas o selector já descartou esses candidatos. O código é redundante.
- Sem duração mínima (um candidato de 200 ms passa).

### 2.6 Editor
- `updateMetadata` só exige `end > start`. O `EditorViewModel` faz `coerceAtLeast`, mas **não limita ao fim do vídeo**, nem à duração máxima, nem checa sobreposição com outros candidatos.
- `hook` é carregado no ViewModel, mas `updateMetadata` não persiste `hook`.
- O intervalo persiste (Room) e é relido ao reabrir. Isso funciona.
- Editar não zera `status`, `localPath` nem `exportProgress`. Combinado com o item 9, o export reaproveita um arquivo desatualizado.

### 2.7 Export (`ShortsProcessingManager`)
- Bom: valida o intervalo, valida o arquivo final com `probe`, reaproveita export `done` quando o arquivo existe, falha de um item não derruba os outros (try/catch dentro do loop).
- Ruim:
  - Arquivo `"${safeTitle}_${index+1}.mp4"`: colisão entre plataformas, qualidades e resoluções. `index` depende da ordem por score, que muda após re-análise.
  - Falha ou cancelamento chama `outputFile.delete()` mesmo quando o arquivo pertencia a outro export `done`.
  - Reuso ignora o intervalo atual do candidato (ver 2.6).
  - `focusTrack` é recalculado em cada export (até 90 processos ffmpeg) e `runCatching { }.getOrNull()` engole `CancellationException`.
  - Legendas: `subtitleRepository.getSegments(...)` nunca é preenchido (nada chama `SubtitleRepository.save`). O caminho real é sempre o fallback do transcript. O fallback também usa tempo absoluto.
  - `project == null` ou `candidates.isEmpty()` fazem `return` silencioso. O worker devolve `Result.success`.
  - Se todos os itens falham, o worker ainda devolve `Result.success`. O usuário vê "concluído".
  - `@Volatile var cancelled` é estado de instância compartilhado. Se o manager for singleton, uma execução pode herdar a flag de outra.
  - `ResolutionPreset.ORIGINAL` (0×0) cai para 1080×1920 sem avisar.
  - `resolveSubtitleStyle("creator")` fixo. A escolha do usuário é ignorada.

### 2.8 Batch
- Falha de um item **não** cancela os demais. Isso está certo.
- O cancelamento depende de `cancelled` + `engine.cancel()` + cancelamento do coroutine. Não há teste que prove que o processo morre.
- Progresso agregado vai só para `setProgressAsync`. Ao recriar a tela ou reiniciar o processo, o progresso do lote se perde. Só `shorts.exportProgress` (por item, quase sempre 0 ou 1) e `exports.status` ficam no banco.
- `getResumableByProject` existe no DAO, mas nada o usa. Não há retomada real de lote interrompido.
- Sem `setForeground` (ver achado 13).

### 2.9 Trends
- O que é métrica externa: **nada**. Os provedores de plataforma (`YouTube`, `Instagram`, `TikTok`, …) devolvem um único card "Explorar…" com `views = null`, `engagement = null` e link de busca. Isso é honesto.
- O que é inferência: tudo que vem de `GrokProvider.searchTrends`. O prompt pede "estimativa honesta de visualizações" e o parser repassa `views` e `engagement` para o `TrendCard` quando o modelo diz `openable=true` e `sourceUrl` começa com `http`. A UI mostra `"Visualizações: $it"`. Isso apresenta número gerado por LLM como métrica.
- `TrendCard` não tem campo de origem (`source`/`confidence`). Não dá para a UI separar "dado de API" de "inferência de IA".
- `GrokTrendProvider.isAvailable() = true` e o comentário do contrato diz "disponível via API oficial". Grok não é API oficial de nenhuma plataforma. O contrato deveria diferenciar `OFFICIAL_API`, `AI_INFERENCE`, `LINK_ONLY`.
- Nenhum chamador usa `isAvailable()`.
- `TrendSearchRepositoryImpl.search`, para `platform == "grok"`, chama a IA duas vezes (uma no `aiResults`, outra no provider).
- `hunterSearch` calcula `relevanceScore = (size - index) * 10`. Isso é só a ordem de chegada, apresentada como relevância.

### 2.10 Room
- `ShortDao.updateExportState` usa `localPath = COALESCE(:localPath, localPath)`. Atualização parcial **não** apaga `localPath`. **Esse item do checklist está OK.** Efeito colateral: depois de falha ou cancelamento o `localPath` antigo continua apontando para arquivo que o manager acabou de apagar.
- `ShortRepository.updateExportProgress` passa `error` direto. Um progresso sem erro limpa o `exportError`. A README diz o contrário. O teste `export progress preserves error...` cobre `ExportRepository`, não `ShortRepository`.
- `ExportDao.updateState` usa `COALESCE` em `startedAtMs`, `completedAtMs` e `outputPath`. Mas `markRunning` não zera `completedAtMs`. Um retry mostra `completedAtMs` da tentativa anterior.
- Sem `ForeignKey`, sem `@Index` (`projectId`, `shortId`), sem `onDelete = CASCADE`. `ProjectDao.delete` deixa shorts, transcripts, subtitles e exports órfãos. `OnConflictStrategy.REPLACE` com PK `autoGenerate` é um risco se FKs forem adicionadas depois (REPLACE apaga e reinsere).
- `exportSchema = false`: sem JSON de schema versionado.
- `DataModule` não usa `fallbackToDestructiveMigration` (correto), mas também não há migração para a v3 → v4 prevista.
- `allowBackup=true` com `EncryptedSharedPreferences`: após restore as chaves ficam ilegíveis (Keystore não migra).

### 2.11 Testes
Existem testes JVM para: pipeline (`MediaPipelineExecutionTest`, `MediaAnalysisPipelineTest`), seleção (`CandidateSelectorTest`), score, validação, codec de transcript, parser OpenAI, repositórios (com DAOs falsos, sem Room real), `FfprobeParser`. Existe 1 teste instrumentado de migração.
Lacunas:
- Nenhum teste do filtro ffmpeg (string do `-vf`), nem do `buildInterpolatedXExpression`. Um teste de unidade teria pego o `$t1` e a normalização errada.
- Nenhum teste de cancelamento com callback que suspende.
- Nenhum teste de `ShortsProcessingManager` (colisão de arquivo, reuso após edição, lote com falha parcial).
- Nenhum teste de trends (separação métrica/inferência).
- Nenhum teste com o binário real.

---

## 3. Fases de implementação

Cada fase tem **entrega**, **critério de aceite** e **gate**. Uma fase só vira "concluída" quando o gate passa em CI e quando os testes novos da fase existem.

### Fase 0 — Desbloquear build e CI (P0)
**Status: implementada, gate de CI pendente.** Detalhes, evidências e o que não foi verificado: [`docs/fases/FASE-00-DESBLOQUEAR-BUILD-E-CI.md`](fases/FASE-00-DESBLOQUEAR-BUILD-E-CI.md).

**Objetivo:** o projeto compila e o CI roda de verdade.
- [x] Corrigir `$t1` e substituir a geração de expressão de crop por `FocusCropExpression` (Kotlin puro, 11 testes, validada num FFmpeg real).
- [x] `ci.yml`: `setup-android` antes de qualquer uso do SDK; `:domain:test` incluído (a task `testDebugUnitTest` não existe no módulo JVM `domain`, então esses testes nunca rodavam).
- [x] Corrigir `MediaAnalysisPipelineTest` (usava arquivo inexistente; falharia ao entrar no CI).
- [x] Hardening: `run` → `runFfmpeg` com tipo explícito; `catch` sem `_`; heap do Gradle 3 GB.
- [ ] Rodar localmente `./gradlew :domain:test testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest`.
- [ ] Primeira run verde do CI (dois jobs, mesmo commit).
**Aceite:** os 4 gates verdes (JVM, lint, assemble, instrumentation) no mesmo commit.
**Gate:** o CI mostra os dois jobs verdes.

### Fase 1 — Binário FFmpeg executável e licenciado (P0)
**Status: implementada (1.1 a 1.4). Gate pendente: teste arm64 em aparelho.** Detalhes: [`docs/fases/FASE-01-BINARIO-FFMPEG.md`](fases/FASE-01-BINARIO-FFMPEG.md).
**Objetivo:** o app executa ffmpeg num aparelho real.
- [x] 1.1 Decidir a estratégia (A adotada):
  - **A (recomendada):** empacotar como `jniLibs/arm64-v8a/libffmpeg.so` e `libffprobe.so`, com `packaging { jniLibs { useLegacyPackaging = true } }`, e executar de `applicationInfo.nativeLibraryDir`. Remover `AssetExtractor` e os 30 MB de `assets/`.
  - **B:** usar uma biblioteca com JNI (ex.: ffmpeg-kit ou equivalente mantido). Não executa binário.
- [x] 1.2 Fechar o conjunto de codecs: decidir vídeo (`libx264` exige build GPL, `h264_mediacodec` encoder exige build com encoder) e áudio (mp3 vs m4a/wav). Registrar a `configuration` do build no repositório.
- [x] 1.3 Documentar procedência, versão, licença (LGPL/GPL) e checksum do binário. Remover referência a caminhos de máquina pessoal.
- [x] 1.4 Teste instrumentado em arm64 (escrito; execução pendente) (Firebase Test Lab ou device farm) que roda `ffmpeg -version`, `-encoders`, `-filters`, e gera um clipe de 2 s. O emulador x86_64 do CI não serve para isso. O CI precisa de um job separado ou de um passo manual documentado.
**Aceite:** `ffmpeg -encoders` lista os encoders usados pelo código; um clipe de 2 s é gerado em aparelho arm64.
**Gate:** teste instrumentado em arm64 verde.

### Fase 2 — Filtros e legendas corretos (P0)
**Status: implementada (2.1 a 2.5). Gate pendente: `:domain:test` no CI e clipe real em arm64.** Detalhes: [`docs/fases/FASE-02-FILTROS-E-LEGENDAS.md`](fases/FASE-02-FILTROS-E-LEGENDAS.md).
**Objetivo:** o vídeo exportado tem crop, foco e legendas corretos.
- Mover a construção de filtros para um objeto puro `FfmpegFilterBuilder` com testes JVM (golden strings):
  - crop 9:16 sem `focusTrack`;
  - crop com foco: `x = clamp(cx*iw - cw/2, 0, iw-cw)`, interpolação linear por trechos, tempos **relativos ao clipe**;
  - legendas: tempos `seg.start - candidate.start`, escape correto de `' : , % \`, `y` fixo (sem `n`).
- Preferir **ASS** (`subtitles=`/`ass=`) ou `-filter_script:v` com arquivo temporário, em vez de N `drawtext` na linha de comando. Se mantiver `drawtext`, definir `fontfile=` (arquivo empacotado em `assets` e copiado para `filesDir`) e confirmar `--enable-libfreetype` no build.
- Corrigir `buildSubtitles` (pipeline) e o fallback do export para o mesmo conversor `toClipRelative(...)`.
- `detectFocusTrack`: uma chamada `ffmpeg -vf fps=1 frame_%04d.jpg`, tempo do ponto = índice do frame, `default` com tempo correto, `FACE_TRACKING` só quando houver detecções, fechar `faceDetector`.
**Aceite:** testes golden passam; clipe de teste com legenda visível no segundo esperado; foco acompanha o rosto num vídeo de teste.
**Gate:** JVM (golden) + teste arm64 da Fase 1 com um clipe real.

### Fase 3 — Cancelamento e estados corretos (P0/P1)
**Status: implementada (3.1 a 3.6). Gate pendente: `:domain:test` e compilação Android no CI; teste instrumentado de cancelamento em arm64.** Detalhes: [`docs/fases/FASE-03-CANCELAMENTO-E-ESTADOS.md`](fases/FASE-03-CANCELAMENTO-E-ESTADOS.md).
**Objetivo:** cancelar significa cancelar, e o estado gravado reflete a etapa real.
- Criar `sealed class EngineResult`/exceção `FfmpegCancelledException` distinta de `FfmpegFailedException(exit, stderrTail)`. `run()` deve lançar `CancellationException` quando o processo morre por `cancel()` ou por cancelamento do coroutine.
- `run()`: `withContext(Dispatchers.IO)` + `try/finally { process.destroyForcibly() }`. Remover `CoroutineScope(...).launch` solto.
- `cancel()` só atinge o processo do dono (token por job), não o processo global.
- Pipeline e workers: gravar estado final dentro de `withContext(NonCancellable) { ... }`.
- Trocar `runCatching { ... }` por `try/catch` que relança `CancellationException` (focus track no export; qualquer outro `runCatching` em código `suspend`).
- `probe` vira `suspend` (`withContext(IO)`) e passa a respeitar timeout/cancelamento.
- `ExportWorker`/`AnalysisWorker`: diferenciar `isStopped` (reagendar) de cancelamento do usuário.
- Teste JVM com `FakeVideoEngine` cujo `processClip` suspende e checa `isActive`; callback `onStageUpdate` que também suspende (`delay(1)`). Verificar que o estado final é `CANCELLED`, não `FAILED`, e que o status é gravado.
**Aceite:** o teste de cancelamento no meio de cada estágio passa; o processo ffmpeg morre (teste instrumentado: PID some em < 2 s).
**Gate:** JVM + instrumentado.

### Fase 4 — Pipeline e validação de entrada (P1)
**Status: implementada (4.1 a 4.7). Gate pendente: `:domain:test` e compilação Android no CI.** Detalhes: [`docs/fases/FASE-04-PIPELINE-E-VALIDACAO.md`](fases/FASE-04-PIPELINE-E-VALIDACAO.md).
**Objetivo:** nenhum estágio roda com entrada inválida, e a saída da IA é saneada.
- Mover `MediaValidator` para dentro de `MediaAnalysisPipeline.validateInput` (o worker passa a só delegar).
- Áudio temporário em `cacheDir/analysis/<projectId>/` com `try/finally` para apagar. Sem `replaceLast`.
- Escolher um formato de áudio aceito pela OpenAI e suportado pelo build (Fase 1).
- Sanear a resposta da IA: descartar candidatos sem `title`, com `start/end` ausentes, fora de `[0, duração]` ou fora da região coberta pela transcrição; registrar quantos foram descartados e por quê.
- Retry por exceção tipada (`TransientAiException`, `HttpException(code)`), nunca por substring.
- Transcrição: `Call.cancel()` ao cancelar o coroutine (`suspendCancellableCoroutine` + `invokeOnCancellation`).
- Registrar no banco o `provider`/modelo que realmente respondeu.
**Aceite:** testes de pipeline cobrem cada estágio falhando, entrada sem áudio, duração 0, candidatos fora do vídeo.
**Gate:** JVM.

### Fase 5 — Seleção determinística (P1)
**Status: implementada (5.1 a 5.5). Gate pendente: `:domain:test` e compilação Android no CI.** Detalhes: [`docs/fases/FASE-05-SELECAO-DETERMINISTICA.md`](fases/FASE-05-SELECAO-DETERMINISTICA.md).
- `CandidateSelector`: aplicar o mesmo pipeline de regras para **todos** os presets, inclusive `"ai"`: faixa válida dentro da duração do vídeo, duração mínima (ex.: 3 s) e máxima, remoção de sobreposição por maior score, limite, desempate por `startMs`.
- Unificar o mapa de preset → duração em um único lugar (`DurationPreset.fromKey`). Preset desconhecido deve falhar com erro claro ou usar um default **único**.
- Definir o comportamento para `"ai"`: teto = `suggestedDurationMs` ou um máximo global (ex.: 90 s).
- Testes: sobreposição com `"ai"`, candidato acima do fim do vídeo, empate de score, `maxCandidates = 0`.
**Aceite:** nenhum candidato impossível ou sobreposto sai do selector, para qualquer preset.
**Gate:** JVM.

### Fase 6 — Persistência, Editor e Room (P1)
**Status: implementada (6.1 a 6.7). Gate pendente: compilação Android, schemas JSON e testes instrumentados.** Detalhes: [`docs/fases/FASE-06-PERSISTENCIA-EDITOR-ROOM.md`](fases/FASE-06-PERSISTENCIA-EDITOR-ROOM.md).
- Entidades: adicionar `@ForeignKey(onDelete = CASCADE)` e `@Index` em `shorts.projectId`, `subtitles.shortId`, `exports.projectId/shortId`, `transcripts.projectId`. Trocar `REPLACE` por `ABORT`/`IGNORE` + `@Update` explícito.
- **Migração 3 → 4**, `exportSchema = true`, `room.schemaLocation`, schemas versionados no repositório, e `MigrationTestHelper` no teste instrumentado (hoje o teste é manual).
- `ShortEntity`: adicionar `intervalVersion`/`exportFingerprint`, `focusTrackJson` (opcional) e `subtitlesJson` persistidos pela análise.
- `ShortRepository.replaceCandidates(projectId, list)` numa `@Transaction`: apaga os antigos (e subtitles/exports dependentes) e insere os novos. Salvar transcript + análise + candidatos na mesma transação.
- Editor: validar `0 <= start < end <= videoDuration`, duração mínima/máxima, sobreposição; persistir `hook`; ao mudar intervalo, zerar `localPath`, `status = "pending"`, `exportProgress = 0`.
- `ExportDao.updateState`: `completedAtMs` zerado em `markRunning`.
- `ShortRepository.updateExportProgress`: não limpar `exportError` sem transição explícita, conforme a README promete (ou ajustar a README).
**Aceite:** deletar projeto limpa todas as tabelas; re-análise não duplica; editar o intervalo e reabrir mantém o novo intervalo e invalida o export anterior.
**Gate:** JVM (Room in-memory via Robolectric ou teste instrumentado) + migração.

### Fase 7 — Export e Batch robustos (P1)
**Status: implementada (7.1 a 7.8). Gate pendente: build, `:domain:test`, instrumentado e aparelho arm64.** Detalhes: [`docs/fases/FASE-07-EXPORT-E-BATCH.md`](fases/FASE-07-EXPORT-E-BATCH.md).
- [x] 7.1 Nome estável + `.part` + rename atômico + falha/cancelamento apagam só o `.part`.
- [x] 7.2 Reuso por fingerprint + `probe`.
- [x] 7.3 Foco persistido.
- [x] 7.4 Legendas persistidas.
- [x] 7.5 Estilo de legenda respeitado + `ORIGINAL` removido.
- [x] 7.6 Lote persistido (`export_batches`, migração 4→5, progresso, retomada).
- [x] 7.7 Worker fiel + foreground/manifest.
- [x] 7.8 Cancelamento (flag `cancelled`, token por lote).
- Nome de arquivo estável e único: `exports/<projectId>/<shortId>_<platform>_<quality>_<res>_<fps>_<fingerprint>.mp4`, em arquivo `.part` e `rename` atômico no final.
- Falha ou cancelamento apagam **só** o `.part` do próprio export. Nunca arquivo `done` de outra exportação.
- Reuso de `done` só quando `fingerprint` (intervalo + estilo + foco + parâmetros) coincide **e** o arquivo passa pela validação (`probe`).
- Usar o `focusTrack` e as legendas **persistidos** (Fase 6). Recalcular apenas se ausentes.
- Persistir progresso agregado: `exports.progress` por item (throttle ~1 s) e um registro de lote (`export_batches`: total, concluídos, falhos, cancelados, estado).
- `ExportWorker`: resultado reflete a verdade — todos falharam → `Result.failure`; parcial → `success` com contagem de falhas em `outputData`; projeto ou candidatos ausentes → `failure` com mensagem.
- `setForeground(ForegroundInfo)` com notificação + `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_DATA_SYNC`/`MEDIA_PROCESSING` no manifest (API 34+ exige tipo).
- Remover a flag `cancelled` de instância. Cancelamento por `CancellationException` + token por lote.
- Respeitar a escolha de estilo de legenda e o preset `ORIGINAL` (ou removê-lo da UI).
- Retomada: usar `getResumableByProject` de fato ao reabrir o app.
**Aceite:** 3 candidatos, o segundo falha: o 1º e o 3º ficam `done`, o 2º `failed` com mensagem, e o lote termina `partial`. Reabrir o app mostra o mesmo progresso. Cancelar no meio mata o ffmpeg e preserva os `done`.
**Gate:** JVM com fakes + instrumentado em arm64.

### Fase 8 — Trends: separar dado de inferência (P1)
**Status: implementada (8.1 a 8.4). Gate pendente: `:domain:test` e compilação Android no CI.** Detalhes: [`docs/fases/FASE-08-TRENDS.md`](fases/FASE-08-TRENDS.md).
- `TrendCard` ganha `origin: TrendOrigin { OFFICIAL_API, AI_INFERENCE, LINK_ONLY }` e `metricsVerified: Boolean`. `views`/`engagement` só podem ser preenchidos quando `origin == OFFICIAL_API`.
- `GrokProvider.parseTrends`: nunca copiar `views`/`engagement` do LLM para campos de métrica. Se quiser exibir, vão em `aiEstimate` com rótulo "estimativa da IA" na UI.
- `TrendProvider.isAvailable()` → `capability: ProviderCapability`. `GrokTrendProvider` = `AI_INFERENCE`. Os demais = `LINK_ONLY` até existir integração real.
- UI (`ContentRadarScreen`, `TrendHunterScreen`): selo visível por origem; "Visualizações" só com origem oficial.
- Remover `relevanceScore` baseado em índice, ou renomear para "ordem". Corrigir a dupla chamada de IA quando `platform == "grok"`.
- Testes: um JSON do Grok com `views: "2M"` e `openable: true` **não** produz `views` no card; a UI não exibe "Visualizações" para `AI_INFERENCE`.
**Aceite:** nenhum número vindo de LLM aparece rotulado como métrica oficial.
**Gate:** JVM.

### Fase 9 — Importação (P2)
**Status: implementada (9.1 a 9.4). Gate pendente: `:domain:test`, `:data:testDebugUnitTest` e CI.** Detalhes: [`docs/fases/FASE-09-IMPORTACAO.md`](fases/FASE-09-IMPORTACAO.md).
- Hoje: SAF copia sem limite nem checagem de espaço; download usa `OkHttpClient` sem timeout; `partialFile.copyTo` duplica o arquivo (pico de 2× disco); `Range` não valida `Content-Range`.
- Checar espaço livre antes de copiar/baixar; timeouts explícitos; `rename` em vez de `copyTo`; validar `Content-Range`/`ETag` na retomada; validar o resultado com `MediaValidator` + `probe` **antes** de criar o projeto; extensão a partir de `Content-Type`/`ffprobe`, não da URL.
- Testes com `MockWebServer`: HTML, 0 bytes, 206 com retomada, limite de tamanho, queda no meio.

### Fase 10 — QA, observabilidade e release (P2)
**Status histórico:** detalhes de planejamento e evidências da integração atual estão em [`docs/fases/FASE-10-QA-E-RELEASE.md`](fases/FASE-10-QA-E-RELEASE.md) e no relatório de validação.
- Cobertura: `FfmpegFilterBuilder` (golden), cancelamento, `ShortsProcessingManager`, trends, Room (DAOs + migração com SQLite legado aberto pelo Room), importador.
- Lint: `lintDebug` e `lintRelease` sem erros novos; baseline versionado se necessário.
- Release: validar regras de R8 (`minify` + `shrinkResources` ligados): Room, Hilt, kotlinx.serialization, ML Kit, WorkManager. As regras atuais (`-keep ...entity.**`, `...domain.model.**`) são mínimas. Rodar o APK release num aparelho.
- `allowBackup`: desligar ou excluir `SecureKeyStore` das regras de backup.
- Logs: não logar linhas completas do ffmpeg em release; limitar `stderr` guardado a ~2 KB para o erro.
- Atualizar `docs/ROADMAP-12-FASES.md` e a README para o que realmente existe.
**Aceite:** pipeline de release gera APK que roda de ponta a ponta num aparelho arm64: importar → analisar → editar → exportar (3 clipes) → reabrir.

---

## 4. Ordem recomendada e dependências

```
Fase 0 (compila) ─► Fase 1 (binário) ─► Fase 2 (filtros/legendas) ─► Fase 3 (cancelamento)
                                                                      │
                          Fase 4 (pipeline) ◄─────────────────────────┤
                          Fase 5 (seleção)  ◄─────────────────────────┤
                                  │                                   │
                                  └────────► Fase 6 (Room/Editor) ─► Fase 7 (Export/Batch)
Fase 8 (Trends) é independente e pode rodar em paralelo a partir da Fase 0.
Fase 9 e 10 fecham o ciclo.
```

Fases 4, 5 e 8 só dependem da Fase 0. Se houver mais de uma pessoa, elas podem andar em paralelo com as Fases 1 a 3.

## 5. Regra de fechamento de fase
1. Código implementado e **executando o caminho real** (não só mock).
2. Testes novos da fase presentes e verdes localmente.
3. CI da PR com os 4 gates verdes **no mesmo commit**: JVM, lint, assemble debug/release/androidTest, instrumentation.
4. Para as Fases 1, 2, 3 e 7: execução comprovada em aparelho arm64 (o emulador x86_64 do CI não executa o ffmpeg embutido).
5. Só depois: atualizar `docs/ROADMAP-12-FASES.md`.

## 6. Pontos que não consegui confirmar
- Qual tag de `android-actions/setup-android` usar (não verifiquei).
- Se o ffmpeg embutido tem os encoders `aac` e `libmp3lame` (a `configuration` não os habilita explicitamente).
- Se alguma alternativa de legenda (filtros `subtitles`/`ass`) está compilada: exigem libass, também ausente da `configuration`. Pode ser preciso outro build.
