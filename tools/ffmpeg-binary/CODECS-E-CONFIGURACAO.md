# Binário FFmpeg — configuração e conjunto de codecs

Arquivos: `app/src/main/jniLibs/arm64-v8a/libffmpeg.so`, `libffprobe.so` (aarch64, Android, estático, `--enable-small`).

## `configuration` registrada (trecho relevante; caminhos da máquina de build omitidos)
`--disable-gpl --disable-nonfree --enable-static --disable-shared --enable-small --enable-ffmpeg --enable-ffprobe --enable-avfilter --enable-network --enable-encoders --enable-decoders --enable-muxers --enable-demuxers --enable-parsers --enable-bsfs --enable-protocols --enable-filters --enable-iconv --enable-jni --enable-mediacodec --enable-decoder=h264_mediacodec --enable-decoder=hevc_mediacodec --enable-decoder=mpeg4_mediacodec --target-os=android --arch=aarch64 --cpu=armv8-a`

Ausentes: `--enable-gpl`, `--enable-libx264`, `--enable-libmp3lame`, `--enable-libfreetype`, `--enable-libass`.

## Conjunto fechado (`FfmpegCodecs`)
| Uso | Antes | Agora | Motivo |
|-----|-------|-------|--------|
| Vídeo exportado | `libx264` | `h264_mediacodec` | libx264 exige build GPL; MediaCodec está habilitado no build |
| Áudio exportado | `aac` | `aac` (128k) | encoder nativo, sem dependência externa |
| Áudio de análise | `libmp3lame` (.mp3) | `aac` mono 64k em `.m4a` | sem libmp3lame; OpenAI aceita m4a (MIME `audio/mp4`) |

`-preset` removido (não existe em `h264_mediacodec`); bitrate via `-b:v`.

## Status de verificação
- **Não verificado em execução** (sem aparelho arm64): presença do encoder `h264_mediacodec` e `aac` em `ffmpeg -encoders`. Inferido da `configuration` (`--enable-encoders` + `--enable-mediacodec`). A confirmação é o aceite do submódulo 1.4.
- `drawtext` **não existe** no binário (sem libfreetype); `subtitles`/`ass` exigem libass, também ausente. Legendas: resolvidas na Fase 2 (2.3) com PNG + `overlay`, sem depender de `drawtext`/libass.
