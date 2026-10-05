# Integração e validação — Fase 2

**Data:** 2026-10-05

**Branch de validação:** `fix/ci-phase12-continuation` (PR #2)

**Base:** `b50e851511dd71f2f3a1bf71745cc9bec19cc28d`

## Escopo integrado

- Importado o snapshot `ShortsFactory-fase2.zip`, preservando os ajustes já existentes no PR para análise, importação e edição.
- Centralizados filtros de crop/scale/FPS, conversão de timestamps de legenda para o tempo relativo ao clipe e tracking de foco testável em Kotlin puro.
- Renderizadas legendas como PNG transparente no Android e compostas com `overlay` do FFmpeg, pois o binário não inclui `drawtext`/libass.
- Movidos `ffmpeg` e `ffprobe` de `assets/` para `jniLibs/arm64-v8a/` e execução via `nativeLibraryDir`; os hashes dos binários no snapshot são idênticos aos dos arquivos já versionados.
- Alinhados encoder/áudio ao build FFmpeg disponível (`h264_mediacodec`, AAC e `.m4a`) e adicionados checksum, avisos de terceiros e documentação da procedência/licença.
- Atualizado o workflow para instalar explicitamente o Android SDK, validar os hashes, executar testes de `:domain` além dos testes Android, lint, builds debug/release e migração Room instrumentada.

## Erro detectado e correção

A primeira execução local de `:domain:test` apontou incompatibilidade de tipo em `MediaPipeline`: `cancelPendingStages` recebe uma função suspendida, mas o código passava a chamada local `update` em vez da referência. As chamadas foram corrigidas para `::update`. Também foi mantida a limpeza do áudio temporário no `finally`, alinhando extensão e MIME para `.m4a`.

## Evidências locais

| Verificação | Resultado |
|---|---|
| `./gradlew :domain:test --stacktrace --no-daemon --max-workers=1` | **PASSOU:** 47 testes, 0 falhas, 0 erros, 0 skips; inclui 17 casos novos da fase 2 |
| `bash tools/ffmpeg-validation/validate_crop.sh` | **PASSOU:** 5 de 5 verificações com FFmpeg real |
| `sha256sum -c tools/ffmpeg-binary/SHA256SUMS` | **PASSOU:** ambos os binários conferem |
| `git diff --check` | **PASSOU:** sem problemas de whitespace após normalização |

O Gradle reportou apenas um aviso de uso de construtor `Locale` obsoleto em `FocusCropExpressionTest`; não afeta o resultado dos testes.

## GitHub Actions

As execuções anteriores do PR terminaram como canceladas antes de iniciar qualquer step e não continham logs de compilação:

- [Run 37366097762](https://github.com/Kyra2214/ShortsFactory/actions/runs/37366097762) — job unitário cancelado, instrumentação ignorada.
- [Run 37366082783](https://github.com/Kyra2214/ShortsFactory/actions/runs/37366082783) — job unitário cancelado, instrumentação ignorada.

- [Run 37380784369](https://github.com/Kyra2214/ShortsFactory/actions/runs/37380784369) — testes JVM, lint e builds debug/release/Android test **passaram**. A execução ficou vermelha somente porque `actions/upload-artifact@v4` não conseguiu criar o artefato: a quota de armazenamento do GitHub Actions estava esgotada. Como o job principal depende do upload, o teste instrumentado foi ignorado.
- Correção aplicada ao workflow: os uploads de relatórios/APKs são auxiliares e agora usam `continue-on-error: true`, tanto no job principal quanto no instrumentado. Assim, a quota não mascara nem interrompe os gates de compilação/teste; os relatórios podem não ser armazenados enquanto a quota estiver cheia.
- [Run 37382745467](https://github.com/Kyra2214/ShortsFactory/actions/runs/37382745467) — job principal **passou** (JVM, lint e APKs). O job instrumentado não chegou a executar testes: o emulador falhou ao criar a partição userdata por espaço do runner (`7085.39 MB` disponíveis, `7372.80 MB` exigidos). Isso é uma limitação do disco do runner, não uma falha da migração Room.
- Correção aplicada: definir `disk-size: 6G` no AVD API 35, suficiente para os testes Room e abaixo do espaço livre observado. Essa entrada é suportada pelo [android-emulator-runner](https://github.com/ReactiveCircus/android-emulator-runner#configurations); reduzir para `6G` também é o workaround relatado em [android-emulator-runner#455](https://github.com/ReactiveCircus/android-emulator-runner/issues/455).

**Nova validação após tornar o upload não bloqueante e reduzir a partição do AVD:** pendente; será registrada após o workflow atualizado concluir.

## Limites e pendências

- O workflow valida o build e a migração Room num emulador x86_64; isso não substitui uma execução do FFmpeg arm64 num aparelho real. A validação instrumentada do binário foi adicionada, mas requer dispositivo arm64 para não ser ignorada.
- A documentação do FFmpeg registra origem de compilação desconhecida para os binários de terceiros. Recompilar a partir de uma versão/tag rastreável e concluir a oferta do código-fonte correspondente continuam pendências antes de release/distribuição pública.
