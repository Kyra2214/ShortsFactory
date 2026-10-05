# Fase 2 — Filtros e legendas corretos

**Status: implementada (5 de 5 submódulos). `:domain:test` passou localmente; CI completo e clipe real em aparelho arm64 permanecem pendentes.**

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 2.1 | `FfmpegFilterBuilder` (crop/foco, scale, fps) em `domain` e engine delegando | concluído |
| 2.2 | Legendas em tempo relativo ao clipe (`toClipRelative`) em `buildSubtitles` (pipeline), fallback do export e filtro; `y` fixo; escape correto | concluído |
| 2.3 | Renderização de legendas compatível com o binário (sem `drawtext`/libass): decisão e implementação | concluído (PNG + `overlay`) |
| 2.4 | `detectFocusTrack` com uma chamada `fps=1`, tempo correto, `FACE_TRACKING` por detecções, fechar `faceDetector` | concluído |
| 2.5 | Testes golden JVM (`FfmpegFilterBuilder`, legendas) e validação da fase | concluído (17 casos novos; suíte JVM de domínio executada) |

> Regra da sessão: testes novos só no fim da fase (2.5).

## 2.1 — O que mudou
- `domain/.../pipeline/FfmpegFilterBuilder.kt` (novo): `videoFilter(spec, extraFilters)` monta `crop` (via `FocusCropExpression`) → `scale` → `fps` → filtros extras; valida resolução, FPS (1..120) e intervalo.
- `FfmpegVideoEngine.processClip` usa o builder; a montagem manual da cadeia foi removida.
- Comportamento do filtro de vídeo inalterado (mesma cadeia da Fase 0). Legendas ainda passam como filtro extra vindo de `buildSubtitleDraw` (corrigido em 2.2/2.3).

## 2.2 — O que mudou
- `domain/.../pipeline/SubtitleTiming.kt` (novo): `fromTranscript` (recorte ao intervalo, tempo absoluto) e `toClipRelative` (recorta, subtrai `clipStartMs`, descarta vazios, ordena).
- Convenção: `SubtitleSegment`/`TranscriptSegment` ficam em tempo **absoluto**; a conversão para relativo acontece uma única vez, em `FfmpegFilterBuilder.subtitleFilter`.
- `MediaPipeline.buildSubtitles` e o fallback de `ShortsProcessingManager` usam `SubtitleTiming.fromTranscript` (código duplicado removido).
- `FfmpegFilterBuilder.subtitleFilter(spec)` substitui `buildSubtitleDraw` do engine: tempos relativos, `y=(h*pos/100)-text_h/2` fixo (sem `n`), `expansion=none`, `enable='between(t\,ini\,fim)'`, apóstrofo → `’`, controles → espaço.
- Validado em FFmpeg 6.1.1 real (com drawtext): `enable` com uma barra dentro das aspas funciona e com duas falha; `:` e `,` não precisam de escape; `expansion=none` torna `%`/`\` literais; dois drawtext sequenciais aparecem nos segundos corretos, com `y` ≈ 78% da altura.
- A parte `drawtext` deste item foi substituída no 2.3 (o binário não tem `drawtext`).

## 2.3 — O que mudou
ASSUMINDO: sem decisão do usuário entre recompilar o FFmpeg (freetype/libass) e renderizar no Kotlin, foi adotado PNG + `overlay` (não exige novo binário; `overlay` existe no build).
- `FfmpegFilterBuilder.filterGraph(spec)` (substitui `subtitleFilter`/`escapeDrawtextText`, removidos): `-filter_complex` com `[0:v]crop,scale,fps[b0]` e um `overlay` por legenda (`x=(W-w)/2`, `y=min(H-h,H*pos/100-h/2)`, `enable='between(t,ini,fim)'`, tempos relativos ao clipe). Retorna `FilterGraph(filterComplex, outputLabel, subtitles)`; sem legendas, o engine usa `-vf` como antes.
- `video-engine/.../SubtitleBitmapRenderer.kt` (novo): PNG transparente por legenda (`StaticLayout` centralizado, quebra de linha em 90% da largura, contorno preto exceto estilo `minimal`, `fontSizePx` escalado por `targetWidth/1080`).
- `FfmpegVideoEngine.processClip`: renderiza os PNGs em diretório temporário (apagado em `finally`), adiciona cada um como `-i`, usa `-filter_complex` + `-map [saída] -map 0:a?`. Argumentos montados em `clipArgs`.
- Validado em FFmpeg 6.1.1 real: grafo com 2 overlays; legenda 1 em 0,5–1,5 s e legenda 2 em 2,0–3,0 s (frames exatos), centro vertical ≈ 78%.
- Não verificado: compilação e render Android (`StaticLayout`/`Canvas`); desempenho com muitas legendas (uma entrada `-i` por legenda).

## 2.4 — O que mudou
- `domain/.../pipeline/FocusTrackBuilder.kt` (novo, Kotlin puro): `build(startMs, detections)` — índice `i` ↔ tempo `startMs + i*1000`; amostra sem rosto usa o foco central seguro **com o tempo da própria amostra** (bug antigo: `default` reutilizado com tempo errado); suavização movida do engine; `FACE_TRACKING` somente com ≥ `MIN_FACE_DETECTIONS` (1) rostos detectados (antes: comparação frágil com 0,5/0,42 após suavizar).
- `FfmpegVideoEngine.detectFocusTrack`: **uma** chamada `ffmpeg -ss -i -t -vf fps=1,scale=640:-2 frame_%04d.jpg` (antes: um processo por segundo, até 90); ML Kit analisa cada frame; falha na extração → trilha central estática; `CancellationException` é relançada.
- `FaceDetector` deixou de ser propriedade preguiçosa nunca fechada: é criado por chamada e fechado em `finally`, junto com a remoção do diretório temporário.
- Validado em FFmpeg 6.1.1 real: `-t 2.5 -vf fps=1,scale=640:-2` gera 3 frames (0..2), 640 de largura.
- Não verificado: compilação, ML Kit em aparelho, tempo total de análise.

## 2.5 — Testes e validação da fase
Testes novos em `domain/src/test/.../pipeline/` (rodam em `./gradlew :domain:test`):
- `FfmpegFilterBuilderTest` (8): golden da cadeia crop/scale/fps, filtros extras, argumentos inválidos, sem legendas → sem grafo, legendas fora do clipe, 1 e 2 overlays (rótulos, ordem, tempos relativos), separador decimal independente de locale.
- `SubtitleTimingTest` (4): `fromTranscript` (recorte, texto vazio, segmentos que apenas tocam o intervalo) e `toClipRelative` (subtração do início, recorte nas duas pontas, descarte e ordenação).
- `FocusTrackBuilderTest` (5): sem amostras, sem rostos (tempo de cada amostra), uma detecção → `FACE_TRACKING` com suavização, limites 0..1, passo inválido.

Resultado desta rodada (sandbox; Gradle 8.9):
- **Passou:** `./gradlew :domain:test --stacktrace --no-daemon --max-workers=1` — 47 testes, 0 falhas/erros/skips, incluindo os 17 casos novos. Foi observado somente um aviso de API `Locale` obsoleta em `FocusCropExpressionTest`.
- **Passou:** `tools/ffmpeg-validation/validate_crop.sh` (5/5); `sha256sum -c tools/ffmpeg-binary/SHA256SUMS`; grafo golden com dois overlays em FFmpeg 6.1.1 real (1080x1920, 90 frames), conforme validação registrada para esta fase.
- **Corrigido durante a integração:** `MediaPipeline` passava a função local `update` em vez da referência suspendida para `cancelPendingStages`; Kotlin apontou incompatibilidade de tipo. As chamadas agora usam `::update`. Também foi alinhada a extensão do áudio temporário a `.m4a` e sua remoção no `finally`.
- **Ainda pendente:** CI completo (testes Android, lint, APKs debug/release e teste instrumentado Room) e validação do binário/exportação em aparelho arm64. As execuções anteriores do PR foram canceladas antes de iniciarem steps; elas não forneceram resultado de compilação.

## Gate para concluir a Fase 2
1. `./gradlew :domain:test` verde (inclui os 3 arquivos novos e os 6 anteriores).
2. Compilação de `:video-engine` e `:feature-export` verde no CI.
3. Em aparelho arm64 (junto do gate da Fase 1): exportar um clipe com legenda e conferir o segundo em que aparece, e o foco acompanhando um rosto.

## Não verificado
- Execução ponta a ponta em aparelho arm64 (tracking ML Kit e exportação real com legendas). O CI instrumentado usa emulador x86_64 e não comprova essa execução do binário arm64.
- `drawtext` está ausente do binário; por isso a solução renderiza PNG + `overlay`, sem depender de `drawtext`/libass.
