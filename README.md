# ShortsFactory

ShortsFactory é um aplicativo Android modular em Kotlin e Jetpack Compose para transformar vídeos longos em candidatos a Shorts. A análise, o tracking facial, a geração de legendas e a exportação vertical são executados localmente sempre que possível; integrações externas ficam limitadas aos provedores de IA explicitamente configurados pelo usuário.

## Estado atual

A base atual está estruturada para uma evolução próxima de produção. O app usa Hilt para composição de dependências, Room para persistência, WorkManager para tarefas longas e uma separação explícita entre domínio, dados, engine de vídeo e features de UI. Projetos, candidatos, estados de análise e exportações sobrevivem ao encerramento da Activity e podem ser reidratados ao abrir o projeto novamente.

A análise é agendada como trabalho único por projeto. Seu estado persistente pode ser `idle`, `queued`, `running`, `done`, `failed` ou `cancelled`, com progresso, mensagem de erro, timestamp de atualização e retry controlado. A exportação mantém estado por item, progresso, tentativas, arquivo de saída e timestamps; o arquivo parcial é removido quando uma execução falha ou é cancelada.

As Fases 11 e 12 estão na branch `feature/phase12-video-preview` e no [PR #4](https://github.com/Kyra2214/ShortsFactory/pull/4), validadas pelo [workflow 37530672897](https://github.com/Kyra2214/ShortsFactory/actions/runs/37530672897) no commit `6dd5565`. Até o merge do PR, o ZIP gerado por **Code → Download ZIP** sem selecionar uma branch corresponde à branch padrão `main` e não contém essas duas fases; para obter a entrega completa, selecione `feature/phase12-video-preview` no GitHub.

## Arquitetura

| Módulo | Responsabilidade |
| --- | --- |
| `app` | Activity, composição Hilt, workers do WorkManager e navegação Compose |
| `core` | Armazenamento seguro das chaves |
| `domain` | Modelos, contratos, validação de mídia e pipeline independente de Android |
| `data` | Room versão 5, migrações, DAOs, repositórios, importação e transcrição |
| `video-engine` | FFmpeg/ffprobe local, extração/divisão de áudio, filtros e tracking facial |
| `feature-projects` | Lista de projetos, análise, estados, progresso, cancelamento e reprodução dos cortes exportados |
| `feature-editor` | Edição de intervalos e metadata dos candidatos, com prévia rápida do trecho |
| `feature-player` | Leitor de vídeo interno (Media3 ExoPlayer): `SfVideoPlayer`, `PlayablePath` |
| `feature-export` | Fila de exportação, progresso e estados por plataforma; prévia fiel (`ClipPreviewManager`) |
| `feature-ai` | Configuração do provedor Grok e da chave OpenAI de transcrição |
| `feature-settings` e `feature-trends` | Preferências e tendências da aplicação; cada card de tendência traz origem (`OFFICIAL_API`, `AI_INFERENCE`, `LINK_ONLY`) e métricas só aparecem com origem oficial |

A ordem de dependências evita ciclos: `app` compõe os módulos, `data` depende de `domain`, `core` e `video-engine`, e as features dependem somente dos contratos e serviços necessários para suas responsabilidades.

## Transcrição real opcional

A transcrição real é implementada por `OpenAiTranscriptionService`, injetado pelo contrato `TranscriptionService`. O adapter chama o endpoint oficial `/v1/audio/transcriptions` com o modelo `whisper-1`, resposta `verbose_json` e granularidade de timestamps por segmento. A chave é guardada no `EncryptedSharedPreferences` através de `SecureKeyStore`; ela não é persistida em código, logs, `local.properties`, artefatos ou histórico Git.

Para habilitar a integração, abra a tela de configurações de IA, informe a chave OpenAI e salve-a. Sem uma chave configurada, o app não realiza chamada externa e a análise falha com uma mensagem orientando a configuração. O áudio é enviado somente durante uma análise solicitada pelo usuário, diretamente para a API oficial configurada no código.

A API aceita arquivos de até 25 MB. Para arquivos maiores, o app extrai o áudio localmente e cria fragmentos inicialmente de dez minutos. Cada fragmento é verificado; se ainda ultrapassar 25 MB, a duração é reduzida progressivamente até o limite mínimo configurado. Os timestamps dos fragmentos são recompostos usando a duração real observada por `ffprobe`, e os temporários são removidos ao final, inclusive quando há erro.

> A integração OpenAI exige uma chave válida e conexão de rede. Nenhuma chave real é necessária para compilar ou executar os testes automatizados. Custos, retenção e políticas de dados da API devem ser avaliados pelo proprietário da chave conforme a documentação do provedor.

## Vídeo, validação e foco facial

Antes da análise, o app valida existência e tamanho do arquivo, duração, dimensões, presença de vídeo e presença de áudio quando a transcrição é necessária. O processamento usa FFmpeg/ffprobe local, com verificações de intervalo, resolução e FPS.

O `FfmpegVideoEngine` extrai frames em intervalos de um segundo e usa ML Kit Face Detection para selecionar o maior rosto detectado. O bounding box é normalizado, suavizado temporalmente e aplicado à expressão de crop vertical; quando não há rosto confiável, o pipeline usa um foco central seguro. As legendas são renderizadas como PNG transparente (o binário embutido não tem `drawtext`/libass) e aplicadas com `overlay` em tempo relativo ao clipe; a cadeia de filtros é montada por `FfmpegFilterBuilder` no módulo `domain`. `ProcessRunner` vincula cada FFmpeg/ffprobe ao coroutine que o iniciou e destrói esse processo em cancelamento ou timeout, sem estado global compartilhado.

## Execução em segundo plano

`AnalysisWorker` e `ExportWorker` são `CoroutineWorker`s configurados com `HiltWorkerFactory`. O `ShortsWorkScheduler` usa trabalhos únicos por projeto para impedir duplicação de análise ou exportação. Falhas transitórias podem retornar `Result.retry()` dentro do limite de tentativas; falhas permanentes são gravadas com erro e expostas à UI. O progresso do WorkManager e o progresso persistido no Room são atualizados separadamente, permitindo observar o trabalho após recriação da tela.

Cada export grava em `exports/<projectId>/<shortId>_<platform>_<quality>_<res>_<fps>_<fingerprint>.mp4` via arquivo `.part` e rename atômico; falha ou cancelamento removem apenas o `.part`. A exportação reaproveita um resultado concluído quando a combinação de projeto, candidato, plataforma, qualidade, resolução e FPS é a mesma. Itens pendentes, enfileirados, em execução ou falhos permanecem consultáveis para retomada controlada. Atualizações de progresso não apagam mensagens de erro por acidente; a limpeza do erro ocorre apenas em uma transição explícita para execução ou conclusão.

## Requisitos e configuração local

Para compilar, use JDK 17, Android SDK com a plataforma 35, Build Tools 35.0.0 e o Gradle Wrapper. O app suporta Android API 26 ou superior e o workflow de CI usa um emulador API 35. Os binários `ffmpeg` e `ffprobe` (arm64-v8a) ficam em `app/src/main/jniLibs/arm64-v8a/libffmpeg.so` e `libffprobe.so` e são executados de `applicationInfo.nativeLibraryDir` (`useLegacyPackaging = true`).

Crie `local.properties` apontando para o SDK local, por exemplo `sdk.dir=/caminho/para/Android/Sdk`, e não versione esse arquivo. Chaves de IA devem ser inseridas somente dentro do app ou fornecidas por um mecanismo seguro de distribuição; nunca coloque credenciais reais no código, nos testes ou no Git.

## Validação local

Os comandos principais são:

```bash
./gradlew :domain:test testDebugUnitTest --stacktrace --no-daemon --max-workers=1
./gradlew lintDebug lintRelease --stacktrace --no-daemon --max-workers=1
./gradlew assembleDebug assembleRelease --stacktrace --no-daemon --max-workers=1
./gradlew :domain:test test lint assembleDebug assembleRelease --stacktrace --no-daemon --max-workers=1
```

A suíte JVM cobre importação (políticas de espaço/Range/extensão e download com `MockWebServer`), seleção de candidatos, sucesso/falha/cancelamento do pipeline, codec e parser de transcript, parser de `ffprobe`, validação de mídia, repositórios, transições persistentes de exportação e o `ShortsProcessingManager` (falha parcial, reuso, intervalo editado e cancelamento, com DAOs falsos). O teste instrumentado `ShortsDatabaseMigrationTest` verifica as migrações Room `1 → 2 → 3 → 4 → 5` (FKs, órfãos e cascata), defaults e preservação de registros em SQLite real:

```bash
./gradlew :data:connectedDebugAndroidTest --stacktrace --no-daemon --max-workers=1
```

Os testes automatizados não fazem chamadas ao Grok ou à OpenAI. Testes com FFmpeg real, detecção facial em vídeo real e API externa dependem de mídia, binários, rede e/ou dispositivo disponíveis e devem ser executados em uma etapa de homologação separada.

## Integração contínua

O workflow `.github/workflows/ci.yml` executa em pushes para `main`/`master` e em pull requests. O job principal configura JDK 17 e Android SDK, valida o Gradle Wrapper e os hashes FFmpeg, executa os testes JVM (incluindo `:domain:test`), `lintDebug` + `lintRelease` e builds debug/release/test. Os uploads auxiliares de relatórios/APKs são tentados mesmo quando uma etapa falha, mas são não bloqueantes: se a quota do GitHub Actions estiver cheia, o gate de código continua avaliável, embora os artefatos não sejam armazenados.

O segundo job inicializa um emulador API 35 x86_64 com userdata limitada a 4 GB para caber no disco do runner e executa `:data:connectedDebugAndroidTest`, incluindo o teste de migração Room até a versão 5. Isso não valida execução do binário FFmpeg arm64 em aparelho real. A etapa de dependency review não está habilitada neste workflow e não deve ser tratada como um gate executável. A concorrência cancela uma execução antiga da mesma referência quando uma nova alteração é enviada.

A CI não recebe nem exige chaves de IA. A publicação automática em lojas, autenticação OAuth de provedores externos e distribuição de segredos de produção não fazem parte deste repositório; devem ser adicionadas posteriormente em um ambiente de release seguro.

## Importação, tendências e release

- **Importação:** limite de 8 GiB, checagem de espaço livre, timeouts de rede, retomada com `Range`/`If-Range` (parcial em `filesDir` com validador) e `rename` atômico; a extensão vem do `Content-Type`/MIME e o arquivo só vira projeto depois de `probe` + `MediaValidator`. DRM, login e paywall não são contornados.
- **Tendências:** cada card informa a origem (`OFFICIAL_API`, `AI_INFERENCE`, `LINK_ONLY`); visualizações e engajamento só aparecem com origem oficial. Hoje nenhum provedor tem API oficial integrada: o Grok devolve inferência da IA (rotulada) e os demais, apenas links de busca.
- **Release:** `minify` + `shrinkResources` com regras em `app/proguard-rules.pro` (R8 ainda não validado em aparelho). `allowBackup` está desligado e `data_extraction_rules.xml` exclui tudo de backup/transferência (chaves no Keystore não migram). A saída do ffmpeg só vai ao logcat em build debuggable; o `stderr` guardado no erro é limitado a ~2 KB.

## Privacidade e limitações

O processamento de vídeo, FFmpeg e tracking facial são locais. A transcrição OpenAI e a análise Grok são opt-in e enviam somente os dados necessários à operação solicitada. O app não inclui backend próprio, sincronização em nuvem ou publicação automática. A qualidade do tracking depende da visibilidade do rosto, do frame rate e da mídia; na ausência de detecção, o fallback central evita bloquear a exportação.

O binário FFmpeg precisa estar presente para que análise e exportação reais funcionem em um APK instalado. A disponibilidade do codec, o espaço livre, a duração do vídeo e o consumo de bateria podem limitar operações longas em dispositivos reais. O próximo ciclo pode adicionar testes de UI Compose, métricas estruturadas, telemetria opt-in, políticas de retenção de temporários e uma camada de abstração para provedores de transcrição além da OpenAI.

## Repositório

O projeto é mantido no repositório público [Kyra2214/ShortsFactory](https://github.com/Kyra2214/ShortsFactory).
