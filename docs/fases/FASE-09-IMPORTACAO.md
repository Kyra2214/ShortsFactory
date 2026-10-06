# Fase 9 — Importação

**Status: implementada (9.1 a 9.4). NÃO compilado nem executado: Gradle/Android SDK/Kotlin indisponíveis neste ambiente. Gates pendentes: build, `:domain:test`, `:data:testDebugUnitTest`, lint e CI.**

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 9.1 | Espaço livre antes de copiar/baixar, limite de tamanho no SAF, timeouts explícitos, limpeza de parcial e `CancellationException` propagada | concluído |
| 9.2 | `rename` em vez de `copyTo` (sem pico de 2× disco); validar `Content-Range`/`ETag` (`If-Range`) na retomada | concluído |
| 9.3 | Extensão a partir de `Content-Type`/`ffprobe`; validar com `MediaValidator` + `probe` antes de criar o projeto | concluído |
| 9.4 | Testes JVM/`MockWebServer`: HTML, 0 bytes, 206 com retomada, limite de tamanho, queda no meio | concluído |

## 9.1 — O que mudou
- `ImportSpacePolicy` (domain, puro): `MAX_IMPORT_BYTES` (8 GiB), margem de 100 MiB e `check(requiredBytes, totalBytes, freeBytes)` → mensagem de erro ou `null`.
- `VideoImporter.downloadFromUrl`: `OkHttpClient` com timeouts (conexão 15 s, leitura/escrita 30 s; sem `callTimeout`, downloads longos continuam permitidos); checagem de espaço usa o menor `usableSpace` entre `cacheDir` e `filesDir` e conta `.part` + arquivo final (o `copyTo` atual ainda duplica; removido em 9.2).
- `VideoImporter.importFromUri`: tamanho declarado via `OpenableColumns.SIZE` checado contra limite/espaço; cópia com contagem de bytes e limite; arquivo parcial apagado em falha/limite excedido.
- `catch (Exception)` não engole mais `CancellationException` (download e SAF).

ASSUMINDO: tamanho desconhecido (`SIZE` nulo / sem `Content-Length`) não bloqueia a importação; o limite continua sendo imposto durante a cópia, mas o espaço livre só é checado quando o tamanho é conhecido.

## 9.2 — O que mudou
- `HttpResume` (domain, puro): `parseContentRange` (`bytes a-b/total|*`, rejeita faixa incoerente), `ifRangeValidator` (ETag forte, senão `Last-Modified`; ETag fraco ignorado), `validateResume` (início = offset e total dentro do limite).
- `VideoImporter.downloadFromUrl`: parcial agora em `filesDir` (`video_<key>.part` + `.part.meta` com o validador); parcial legado em `cacheDir` apagado. Retomada envia `Range` + `If-Range`; sem validador salvo recomeça do zero; 200 reinicia e regrava o validador; 206 exige `Content-Range` válido; 416 descarta o parcial.
- `copyTo` + `delete` substituídos por `Files.move(ATOMIC_MOVE)` (mesmo diretório); checagem de espaço considera só os bytes restantes.
- Download que termina com tamanho diferente do esperado mantém o parcial e falha com mensagem de retomada.

ASSUMINDO: validador de retomada = ETag forte ou `Last-Modified` da resposta que iniciou o parcial; servidores sem nenhum dos dois não permitem retomada (recomeça do zero).

## 9.3 — O que mudou
- `ImportExtension` (domain, puro): `resolve(mime, nome)` = MIME (`Content-Type` / `ContentResolver.getType`) → nome/caminho com extensão de vídeo conhecida → `mp4`. Query string e fragmento da URL são ignorados.
- `VideoImporter`: extensão do download definida após a resposta (Content-Type; caminho da URL só como fallback); SAF usa `getType` e `OpenableColumns.DISPLAY_NAME` em vez de `uri.toString()`. `guessExtension` removido.
- `ProjectViewModel.setSource`: antes de `projectRepository.insert`, `probe` + `MediaValidator.validate` (mesma regra do pipeline, com áudio). Arquivo inválido ou `probe` com erro: apaga o arquivo importado, mostra erro e não cria projeto.

ASSUMINDO: o `ffprobe` valida o conteúdo real (probe falho = não é vídeo); não foi adicionado campo de formato de contêiner ao `VideoEngine`/`InputVideoInfo` para derivar a extensão, evitando alterar o contrato do engine. A extensão não afeta a leitura pelo ffmpeg.

## 9.4 — O que mudou
- `ImportPoliciesTest` (domain, 5 testes): limite/margem de espaço, `Content-Range`, validador `If-Range`, `validateResume`, extensão por MIME.
- `VideoImporterDownloadTest` (data, `MockWebServer`, 9 testes): HTML, corpo vazio, download completo (extensão por `Content-Type`, `.part`/`.meta` limpos), acima do limite, 403, queda no meio + retomada com `Range`/`If-Range`, `Content-Range` incompatível, parcial sem validador (recomeça), 416.
- `data/build.gradle.kts`: `testImplementation` `okhttp3:mockwebserver:4.12.0` e `unitTests.isReturnDefaultValues = true` (para `Log`/`ContextWrapper`).

ASSUMINDO: `ContextWrapper(null)` com `isReturnDefaultValues` basta para o `VideoImporter` nos testes JVM (só usa `filesDir`/`cacheDir` no download); sem Robolectric. Não exercitados: caminho SAF (`importFromUri`) e a validação do `ProjectViewModel` — exigem `ContentResolver`/aparelho.
