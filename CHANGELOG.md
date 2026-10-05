# Changelog

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
