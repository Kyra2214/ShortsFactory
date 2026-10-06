# Fase 12 — Leitor de vídeo interno

**Status: implementada (5/5). O workflow 37530672897 passou em testes JVM, lint, builds APK debug/release e testes instrumentados Room. O checklist funcional em aparelho arm64 permanece pendente.**

Objetivo: prévia fiel ao que será exportado e, no futuro, publicado (aprovação antes de postar). A prévia fiel usa o mesmo pipeline da exportação (`processClip`), em resolução menor; as legendas escalam por `targetWidth/1080`, então enquadramento, legendas e tempos coincidem. Pixels/bitrate diferem.

Decisões:
- Reprodução com Media3 ExoPlayer (nova dependência, aprovada), versão fixada em 1.5.1 (compatível com compileSdk 35); decodificação pelo aparelho (MediaCodec).
- Prévia rápida (só o trecho, sem legenda/foco) para ajustar o intervalo; prévia fiel por renderização para conferir o resultado. Itens de overlay de legenda e de foco foram descartados.
- Para aprovação/publicação futura, o arquivo aprovado deve ser o arquivo final exportado (mesmo fingerprint); a prévia reduzida serve só como conferência. Fora do escopo desta fase.

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 12.1 | Base: módulo `:feature-player`, Media3, `SfVideoPlayer` | concluído |
| 12.2 | Prévia rápida no Editor (trecho, moldura 9:16, loop, seek pelo `RangeSlider`) | concluído |
| 12.3 | Prévia fiel: montagem compartilhada do `ClipSpec`, render 540x960 com cache por fingerprint, progresso/cancelar, reprodução | concluído |
| 12.4 | Assistir exportados (botão nos cortes concluídos; reaproveita o arquivo final se o fingerprint bater) | concluído |
| 12.5 | Fechamento: testes JVM (limites do trecho, validação de caminho), documentação, checklist em aparelho | concluído |

## 12.1 — O que mudou
- Novo módulo `:feature-player` (incluído em `settings.gradle.kts`; `:app` depende dele para a CI compilar): `media3-exoplayer` e `media3-ui` 1.5.1, `lifecycle-runtime-compose` 2.8.7.
- `SfVideoPlayer(filePath, modifier, aspectRatio = 9/16, autoPlay, loop)`: ExoPlayer criado por arquivo e liberado ao sair; pausa em `ON_STOP`; `PlayerView` sem controlador com controles próprios (reproduzir/pausar, barra de progresso com seek, tempo); toque no vídeo alterna reprodução; erro de reprodução exibe mensagem.
- `PlayablePath.isAllowed`: só reproduz arquivos regulares dentro de `filesDir` ou `cacheDir` do app.
- Integrado ao Editor e à tela de projetos a partir dos submódulos 12.2–12.4; a CI validou a resolução das dependências Media3 e `LocalLifecycleOwner`.

## 12.2 — O que mudou
- `SfVideoPlayer` ganhou `clipStartMs`/`clipEndMs` (Media3 `ClippingConfiguration`; trocar o trecho recarrega o item no mesmo player e volta ao início) e `fillFrame` (zoom/corte central na moldura 9:16).
- `ShortEditorScreen` recebe `videoPath` e mostra a prévia em moldura 9:16 (60% da largura), com autoplay e loop do trecho; a prévia acompanha o `RangeSlider` ao soltar o controle. Sem legenda nem foco automático (a prévia fiel é o 12.3).
- `EditorViewModel.videoPath` expõe `videoUri` do projeto; `AppNavGraph` repassa à tela. `:feature-editor` passa a depender de `:feature-player`.
- A prévia rápida é distinta da prévia fiel: a primeira ajusta o trecho sem legenda/foco; a segunda renderiza o resultado do pipeline.

## 12.3 — O que mudou
- `ShortsProcessingManager.assembleClipSpec` concentra a montagem do `ClipSpec` (legendas e trilha de foco persistidas, com os mesmos fallbacks); a exportação passou a usá-lo, sem mudança de comportamento. `resolveStyle` expõe o estilo de legenda.
- Novo `ClipPreviewManager` (`:feature-export`): `processClip` em 540x960, 30 fps, 2 Mbps, com o mesmo `ClipSpec` da exportação; saída em `cache/preview/<projectId>/` (nome por fingerprint de intervalo, versão e estilo), `.part` + rename atômico, validação por `ExportOutputValidator`, reuso do cache válido e remoção de prévias antigas do mesmo Short. Cancelar o coroutine mata o FFmpeg.
- `EditorViewModel`: `previewState`, `renderPreview`, `cancelPreview`, `dismissPreview`. `ShortEditorScreen`: botão "Prévia fiel" (desabilitado com o trecho não salvo ou durante a renderização), diálogo com progresso/cancelar e reprodução em loop; a prévia rápida é pausada enquanto o diálogo está aberto.
- A prévia fiel usa o intervalo salvo e foi validada pela CI.

## 12.4 — O que mudou
- `CandidateUi.exportedPath`: preenchido por `ProjectViewModel` quando o Short está `done` e o arquivo em `localPath` existe. O DAO já zera `localPath` quando o intervalo muda, então um export vigente corresponde ao intervalo atual; o arquivo reproduzido é sempre o final exportado, nunca a prévia reduzida.
- `CandidateCard` mostra o botão "Assistir exportado" só nos cortes concluídos; `ProjectScreen` abre um diálogo com `SfVideoPlayer` (9:16) e o fecha sozinho se o export deixar de existir.
- `:feature-projects` passa a depender de `:feature-player`.
- A checagem de fingerprint do nome do arquivo não é refeita na tela; a invalidação por intervalo no DAO e a existência do arquivo são as garantias do escopo atual.

## 12.5 — O que mudou
- `PreviewClipRange` (domain): ajusta o trecho da prévia rápida ao vídeo (início >= 0, fim <= duração, mínimo 1 s; `null` se o vídeo não comporta); `ShortEditorScreen` passa a usá-lo. `SfVideoPlayer.clipEndMs` agora é opcional (`null` = até o fim), sem expor Media3 às features.
- `PlayablePath.isInside(path, roots)` extraído (puro, testável); `isAllowed` delega a ele.
- Testes JVM novos: `PreviewClipRangeTest` (6) em `:domain` e `PlayablePathTest` (6) em `:feature-player`.
- Checklist em aparelho: `docs/fases/FASE-12-CHECKLIST-APARELHO.md`.
- Suíte validada pela CI: `./gradlew :domain:test testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`, além dos testes instrumentados Room no API 35.
