# Fase 1 — Binário FFmpeg executável e licenciado

**Status: implementada (4 de 4 submódulos). Gate pendente: teste instrumentado em aparelho arm64.**

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 1.1 | Empacotar ffmpeg/ffprobe como `jniLibs` (estratégia A) e executar de `nativeLibraryDir` | concluído (sem verificação em aparelho) |
| 1.2 | Fechar conjunto de codecs (vídeo/áudio) e registrar `configuration` | concluído (sem verificação em aparelho) |
| 1.3 | Procedência, versão, licença e checksum do binário | concluído (procedência real segue desconhecida) |
| 1.4 | Teste instrumentado arm64 (`-version`, `-encoders`, `-filters`, clipe de 2 s) | concluído (escrito; não executado) |

## 1.1 — O que mudou
- `app/src/main/assets/{ffmpeg,ffprobe}` → `app/src/main/jniLibs/arm64-v8a/{libffmpeg.so,libffprobe.so}` (bytes idênticos).
- `app/build.gradle.kts`: `packaging.jniLibs.useLegacyPackaging = true` (extrai os `.so` para `nativeLibraryDir` na instalação) e `keepDebugSymbols` para os dois arquivos (AGP não reescreve os binários).
- `FfmpegVideoEngine`: `ffmpegPath()`/`ffprobePath()` usam apenas `nativeLibraryDir/libffmpeg.so` e `libffprobe.so`; falha explícita se ausentes.
- `core/AssetExtractor.kt` removido (nenhuma outra referência).

SHA-256 (registrado para 1.3):
- `libffmpeg.so` `206e84cd597408bbfaf51e27c14b17085590e639e40d568c28735077a6b708b3`
- `libffprobe.so` `42d18430d4a8b5e6efaf135faa4122a13087ac4a2d20933b81a2b781da07d9d0`

## 1.2 — O que mudou
- `video-engine/.../FfmpegCodecs.kt` (novo): vídeo `h264_mediacodec`, áudio `aac`, análise em `.m4a` mono 64k.
- `FfmpegVideoEngine`: `libx264`/`-preset` e `libmp3lame` substituídos; `splitAudio` gera `audio_chunk_%03d.m4a`.
- `MediaPipeline`: áudio temporário `.m4a`. `OpenAiTranscriptionService`: MIME `audio/mp4`.
- Registro da `configuration` e do conjunto de codecs: `tools/ffmpeg-binary/CODECS-E-CONFIGURACAO.md`.
- Não verificado: presença de `h264_mediacodec`/`aac` em `-encoders` (aceite do 1.4). `drawtext` segue ausente (Fase 2).

## 1.3 — O que mudou
- `tools/ffmpeg-binary/PROCEDENCIA-E-LICENCA.md`: FFmpeg 8.0.1, LGPL-2.1-or-later, origem do build desconhecida, obrigações LGPL e como atualizar.
- `tools/ffmpeg-binary/SHA256SUMS` + passo `sha256sum -c` no job `unit-build-lint` do CI.
- `THIRD_PARTY_NOTICES.md` (aviso LGPL).
- Referência ao caminho pessoal removida da documentação; o caminho continua dentro dos binários (só some com recompilação).
- **Pendência de release:** origem/código-fonte do build desconhecidos; recompilar a partir de tag conhecida com script versionado.

## 1.4 — O que mudou
- `app/src/androidTest/.../FfmpegBinaryInstrumentedTest.kt` (novo, 5 testes): `ffmpeg -version`, `ffprobe -version`, encoders `h264_mediacodec` e `aac` em `-encoders`, filtros `crop`/`scale`/`fps` em `-filters`, e clipe de 2 s (`testsrc`+`sine` → `h264_mediacodec`+`aac`) validado com ffprobe (codecs, 720x1280, duração 1,8–2,3 s).
- `assumeTrue(arm64-v8a)`: em emulador x86_64 os testes são ignorados. O job `instrumented` do CI roda só `:data` e não executa este teste.
- Como rodar (aparelho arm64 conectado ou device farm): `./gradlew :app:connectedDebugAndroidTest`.

## Resultado da validação da fase (ambiente offline)
- Gradle indisponível (wrapper não baixa a distribuição): `:domain:test`, `testDebugUnitTest`, lint e assemble **não executados**.
- Executado: `tools/ffmpeg-validation/validate_crop.sh` (5/5 OK, FFmpeg 6.1.1 local); `sha256sum -c` dos binários (OK); `ci.yml` YAML válido com 2 jobs; busca de referências inválidas: nenhuma a `AssetExtractor`, `ffmpeg_bin`, `assets/ffmpeg`, `libx264` ou `libmp3lame` no código.
- Riscos a confirmar no primeiro run em aparelho: `lavfi`/`testsrc` presentes no build; `h264_mediacodec` aceitar entrada software; `aac` e `h264_mediacodec` listados em `-encoders`.

## Gate para concluir a Fase 1
1. `./gradlew :app:connectedDebugAndroidTest` verde em aparelho arm64.
2. Fase 0 verde no CI (pendente desde a Fase 0).
3. Recompilação/origem do binário (ver `tools/ffmpeg-binary/PROCEDENCIA-E-LICENCA.md`) é bloqueio de release, não deste gate.

## Não verificado
- Compilação Gradle e execução em aparelho arm64 (sem SDK/rede neste ambiente).
- Pendências conhecidas, tratadas em 1.2: o build é `--disable-gpl`, sem `libx264`/`drawtext`; o código ainda usa `libx264`, `libmp3lame` e `drawtext`.
- Em release, R8/AGP não alteram os `.so`; confirmar no APK final.
