# Fase 10 — QA, observabilidade e release

**Status: implementação integrada; `:domain:test` passou localmente (139 testes, 0 falhas/erros) e as 5 verificações de crop com FFmpeg real passaram.** A validação Android completa depende da nova CI; execução do APK release em aparelho arm64 permanece pendente.

**Integração atual:** commit `89eb92f`, em revisão no [PR #3](https://github.com/Kyra2214/ShortsFactory/pull/3).

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 10.1 | Backup: `allowBackup` desligado + regras de extração (chaves seguras fora de backup/transferência) | concluído |
| 10.2 | Logs: não logar linhas completas do ffmpeg em release; `stderr` guardado no erro limitado a ~2 KB | concluído |
| 10.3 | R8/release: regras para Room, Hilt, kotlinx.serialization, ML Kit, WorkManager; `lintRelease` | concluído |
| 10.4 | Cobertura restante (`ShortsProcessingManager`, Room/DAOs) + alinhamento final de README/Roadmap ao comportamento real | concluído |

## 10.1 — O que mudou
- `AndroidManifest.xml`: `allowBackup="false"` e `dataExtractionRules="@xml/data_extraction_rules"`.
- `res/xml/data_extraction_rules.xml` (novo): exclui `root`, `file`, `database`, `sharedpref` e `external` em `cloud-backup` e `device-transfer`.
- Motivo: `EncryptedSharedPreferences` (`secure_prefs`, com chaves de IA e configurações) depende do Keystore, que não migra; após restore ficaria ilegível. Banco e vídeos também não são restaurados de forma coerente (caminhos locais, arquivos grandes).

ASSUMINDO: perder backup/transferência automática de projetos é aceitável; opção mais conservadora que excluir só `secure_prefs`.

## 10.2 — O que mudou
- `FfmpegVideoEngine.runFfmpeg`: `Log.d` de cada linha do ffmpeg agora só ocorre com `FLAG_DEBUGGABLE` (`logFfmpegOutput`); em release nenhuma saída do ffmpeg vai ao logcat. Linhas de progresso (`time=`) continuam alimentando o progresso normalmente.
- `stderr` no erro: já limitado a ~2 KB por `FfmpegFailedException.MAX_TAIL_CHARS` + `OutputTail` (últimas linhas, linha única truncada, sem linhas de progresso); verificado, sem mudança necessária.

ASSUMINDO: `Log.w(..., e)` com `FfmpegFailedException` continua válido em release, pois a mensagem já é limitada a ~2 KB e não contém o log completo.

## 10.3 — O que mudou
- `app/proguard-rules.pro`: mantidos `entity.**` e `domain.model.**`; adicionados atributos para stack trace (`SourceFile`, `LineNumberTable`) e `renamesourcefileattribute`; `RoomDatabase` (instanciado por reflexão); regras oficiais de `kotlinx.serialization` para `com.shortsfactory.**$$serializer`/`Companion`/`serializer()`; construtor `(Context, WorkerParameters)` de `ListenableWorker` (WorkManager + Hilt); campos de `GeneratedMessageLite` do Tink (security-crypto) e `dontwarn` de anotações `errorprone`/`javax.annotation`.
- Hilt, ML Kit e OkHttp: sem regra extra (usam as regras de consumidor dos artefatos).
- CI e README: o passo de lint passou a rodar `lintDebug lintRelease`.

ASSUMINDO: as regras de consumidor dos artefatos (Hilt, Room, ML Kit, WorkManager) são suficientes; nenhuma regra extra foi inventada para eles. Sem baseline de lint: nenhum erro de lint foi observado (não executado). **Pendente de validação:** R8 não executado aqui; o APK release precisa rodar de ponta a ponta num aparelho arm64 (importar → analisar → editar → exportar → reabrir).

## 10.4 — O que mudou
- `feature-export/src/test/.../ShortsProcessingManagerTest` (4 testes, DAOs falsos + `FakeEngine`): 3 candidatos com o 2º falhando → `done/failed/done`, lote `partial`, sem `.part`; reexecução com mesmos parâmetros reaproveita exports válidos (sem novo `processClip`); editar o intervalo gera novo export; cancelar no meio preserva o `done`, marca o item `cancelled`, lote `cancelled`, sem `.part`, 3º item intocado.
- `feature-export/build.gradle.kts`: `unitTests.isReturnDefaultValues = true`.
- Room/DAOs: cobertura pelos testes instrumentados existentes (`ShortsDatabaseMigrationTest`, que cria SQLite legado e abre a cadeia real do Room, e `ProjectStoreInstrumentedTest`) e pelos testes JVM de repositórios; nenhum teste novo nesta subetapa.
- README e Roadmap alinhados: seção de importação, trends, release/backup/logs e lista de testes atualizada; progresso final registrado.

ASSUMINDO: o `ShortsProcessingManager` roda em JVM com `ContextWrapper(null)` + `isReturnDefaultValues` (só usa `filesDir`/`Log`); os fakes de DAO replicam a semântica dos `UPDATE`/`COALESCE` reais, mas não substituem o teste Room real.

## Validação e correções do workflow

- A execução [37387066187](https://github.com/Kyra2214/ShortsFactory/actions/runs/37387066187), anterior à integração desta fase, passou no job principal. O emulador API 35 ainda falhou antes dos testes: mesmo com `disk-size: 6G`, pediu `7372.80 MB` com `7085.46 MB` disponíveis.
- Ajustado o AVD para `disk-size: 4G`, com margem de espaço maior, e o job agora inclui `lintRelease` além de `lintDebug`.
- Corrigido o teste de migração `migrateV1ToV4`: a cadeia inclui `MIGRATION_4_5` e o banco declara versão 5; o nome e a asserção agora verificam v5.
- A validação da fase 10 completa e o teste instrumentado permanecem pendentes da nova execução do Actions.
- Evidência local após a integração: `:domain:test` — 139 testes, 22 suítes, 0 falhas/erros/ignorados; `bash tools/ffmpeg-validation/validate_crop.sh` — 5/5; checksums FFmpeg — OK.
- Este sandbox não tem Android SDK configurado; `testDebugUnitTest`, lint/R8, APKs e Room instrumentado devem ser confirmados pelo workflow do GitHub.
