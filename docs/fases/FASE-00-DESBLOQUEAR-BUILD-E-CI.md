# Fase 0 — Desbloquear build e CI

**Status: implementada. Gate de CI pendente.**
O gate desta fase é "Gradle + CI verdes no mesmo commit". Não consegui rodar Gradle neste ambiente (Maven Central bloqueado pela política de rede e sem Android SDK), então a fase **não deve ser marcada como concluída** até a primeira run verde do workflow. Veja "O que foi e o que não foi verificado".

## O que foi encontrado

| # | Problema | Efeito |
|---|----------|--------|
| 1 | `FfmpegVideoEngine` usava `$t1`, variável inexistente | Erro de compilação de `video-engine` (e de tudo que depende dele: `data`, `app`) |
| 2 | A expressão de crop estava errada além do `$t1`: tempos absolutos misturados com `t` relativo, normalização morta, posição `(iw-cw)*cx` em vez de centralizar o rosto | Mesmo compilando, o recorte não seguiria o rosto |
| 3 | `./gradlew testDebugUnitTest` não existe em `:domain` (módulo Kotlin/JVM; lá a task é `test`) | Os testes de pipeline, seleção, score, validação e codec **nunca rodaram no CI** |
| 4 | `MediaAnalysisPipelineTest` chamava `analyze("video.mp4")` com arquivo inexistente; a pipeline valida o arquivo | Os 3 testes falhariam assim que `:domain:test` entrasse no CI |
| 5 | `ci.yml` chamava `sdkmanager` sem instalar/expor o SDK | `sdkmanager: command not found` |
| 6 | `private suspend fun run(...) = suspendCancellableCoroutine { ... }` sem tipo de retorno, com nome igual ao `run {}` do stdlib | Risco de erro de inferência de tipo e de ambiguidade |
| 7 | `catch (_: SerializationException)` em `TranscriptCodec` | Parâmetro `_` em `catch` depende da versão da linguagem; trocado por nome explícito |
| 8 | Heap do Gradle em 1,5 GB | Risco de OOM em `assembleRelease` (R8) com Compose, Hilt e ML Kit |

## O que mudou

**Código**
- `domain/.../pipeline/FocusCropExpression.kt` (novo, Kotlin puro): gera o filtro `crop` completo.
  - Entrada: trilha de foco com tempos absolutos, início e duração do clipe.
  - Saída: `crop=w=..:h=..:x=..:y=..` com vírgulas escapadas.
  - Posição: `max(0, min(quadro - recorte, centro * quadro - recorte / 2))`, ou seja, rosto centralizado e recorte sempre dentro do quadro.
  - Interpolação linear por trechos sobre `t` relativo ao clipe; mantém o primeiro valor até o primeiro ponto e o último valor depois do último.
  - Pontos fora do clipe, não finitos e tempos repetidos são descartados. Valores são limitados a 0..1.
  - No máximo 40 âncoras (decimação mantendo primeira e última).
  - Números independentes de locale, sem notação científica.
  - Sem trilha utilizável: recorte centralizado.
- `video-engine/.../FfmpegVideoEngine.kt`: removidos `buildCropAndScale`, `buildInterpolatedXExpression` e `buildInterpolatedYExpression`; `processClip` chama `FocusCropExpression.cropFilter`. `run` renomeado para `runFfmpeg` e com tipo `Unit` explícito.
- `domain/.../model/TranscriptCodec.kt`: parâmetros de `catch` nomeados.

**Testes**
- `domain/src/test/.../FocusCropExpressionTest.kt` (novo, 11 testes): goldens exatos (sem trilha, 2 pontos, inclinação negativa), tempos relativos ao início do clipe, pontos fora do clipe, clamp 0..1 e NaN, ordenação/duplicados, limite de âncoras e parênteses balanceados, independência de locale (`pt-BR`), escape de todas as vírgulas, argumentos inválidos.
- `MediaAnalysisPipelineTest`: passa a criar um arquivo temporário real (`TemporaryFolder`).

**CI e build**
- `.github/workflows/ci.yml`:
  - `android-actions/setup-android@v3` com `platform-tools`, `platforms;android-35`, `build-tools;35.0.0` antes de qualquer uso do SDK, nos dois jobs; passo extra `sdkmanager --list_installed` no primeiro job para diagnóstico.
  - Removida a instalação manual de system image: o `android-emulator-runner` instala emulador e imagem.
  - Testes JVM: `./gradlew :domain:test testDebugUnitTest`.
  - `workflow_dispatch` adicionado para permitir rodar à mão.
- `gradle.properties`: `-Xmx3g` (Gradle) e `-Xmx1g` (Kotlin daemon).

**Ferramenta de validação**
- `tools/ffmpeg-validation/validate_crop.sh` + `crop_expression_mirror.py`: espelho em Python da expressão e um script que a executa num FFmpeg real e compara frames.

## O que foi e o que não foi verificado

Verificado:
- As expressões geradas (mesma lógica do Kotlin, espelhada em Python) rodam sem erro num **FFmpeg 6.1.1 real** e produzem a posição esperada, comparada **frame a frame** com `crop=608:1080:X:0` fixo:

  | Caso | x esperado | Resultado |
  |------|-----------|-----------|
  | sem trilha | 656 | idêntico |
  | t=0, centro 0,2 | 80 | idêntico |
  | t=1,0 interpolado (0,5) | 656 | idêntico |
  | t=2,5, após o último ponto (0,8) | 1232 | idêntico |
  | t=0, centro 0,9 | 1312 (limitado à borda) | idêntico |

  Isso confirma: `ow`/`oh` válidos em `x`/`y`, `t` relativo ao clipe, escape `\,`, clamp.
- `ci.yml` é YAML válido e tem os dois jobs com os passos esperados.
- Revisão manual das 6 classes de teste que passam a rodar em `:domain:test` contra o código atual: passam, exceto `MediaAnalysisPipelineTest` (corrigido).

**Não verificado (precisa do CI ou de uma máquina com SDK):**
- Compilação Kotlin de qualquer módulo. Nenhum `kotlinc`/Gradle rodou aqui. O Kotlin novo foi escrito e relido com cuidado, mas não compilado.
- Que a saída do Kotlin é byte a byte igual à do espelho Python (os goldens do teste Kotlin foram copiados da saída do Python). Se um golden falhar, o primeiro suspeito é arredondamento de `Float` para `Double`.
- `lintDebug`, `assembleDebug/Release`, `assemble*AndroidTest` e a instrumentação (migração Room).
- Que `android-actions/setup-android@v3` aceita `packages` no formato usado (é o que a documentação da action descreve; não pude consultar a página).
- Outros erros de compilação em arquivos que não li linha a linha (telas Compose, ViewModels de projeto/home, `GrokProvider`/telas de IA).

## Como fechar a Fase 0
1. Rodar localmente: `./gradlew :domain:test testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest`.
2. Rodar `tools/ffmpeg-validation/validate_crop.sh` (precisa de ffmpeg no PATH).
3. Push; os dois jobs do CI devem ficar verdes **no mesmo commit**.
4. Qualquer falha nova de compilação em arquivo não revisado entra como correção desta fase (não adiar para a Fase 1).
5. Só então trocar o status acima para "concluída".

## Impacto nas fases seguintes
- **Fase 2** (filtros e legendas) deve reaproveitar o padrão: lógica pura em `domain` + goldens validados num FFmpeg real. A parte de legendas (`buildSubtitleDraw`) **não foi tocada** e continua com os problemas descritos na auditoria.
- A partir de agora os testes de `:domain` rodam no CI. Qualquer falha neles é real e antiga, não regressão desta fase.
