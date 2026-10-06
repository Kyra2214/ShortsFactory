# Fase 6 — Persistência, Editor e Room

**Status: implementada (6.1 a 6.7). Validado localmente: 111 testes JVM do `:domain` e SQL da migração/invalidação em SQLite real. NÃO compilado: `:data`, `:app`, `:feature-editor` (sem Android SDK/Maven). Schemas JSON do Room e testes instrumentados ainda não gerados/executados.**

Objetivo: dados consistentes entre análise, editor e export; editar o corte invalida o export antigo.

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 6.1 | FKs `ON DELETE CASCADE` + índices; `REPLACE` → `ABORT` + `@Update` explícito | concluído |
| 6.2 | Migração 3 → 4, `exportSchema = true`, `room.schemaLocation` | concluído (schemas pendentes do 1º build) |
| 6.3 | Colunas `intervalVersion`, `focusTrackJson`, `subtitlesJson` gravadas pela análise | concluído |
| 6.4 | `ProjectStore.saveAnalysis`: transcript + análise + candidatos + `done` numa transação; re-análise substitui | concluído |
| 6.5 | Editor: validação completa, `hook` persistido, mudança de intervalo invalida o export | concluído |
| 6.6 | `exports.completedAtMs` zerado em `queued`/`running` | concluído |
| 6.7 | `shorts.exportError` só muda em transição explícita (README passa a ser verdadeira) | concluído |

## O que mudou
- **6.1** FK com CASCADE em `shorts`, `transcripts`, `ai_analyses` (→ `projects`), `subtitles` (→ `shorts`) e `exports` (→ `projects` e `shorts`), com índice em cada coluna de FK. Os `@Insert` deixaram de usar `REPLACE` (com FK, `REPLACE` apaga a linha e **cascateia os filhos**); ganhou `ProjectDao.update`.
- **6.2** `MIGRATION_3_4` recria as 5 tabelas (SQLite não adiciona FK por `ALTER`): criar nova → copiar → apagar → renomear, pais antes dos filhos. **Órfãos são descartados** na cópia (short sem projeto, export cujo short não existe etc.). As tabelas novas não têm `DEFAULT`, para casar com as entidades. `data/schemas/` recebe os JSON (ver pendências).
- **6.3** `ShortRepository.candidateToEntity` serializa legendas e foco (`CandidateArtifactsCodec`, tempos relativos ao clipe). JSON ausente/corrompido decodifica para `null` = "recalcular", nunca para lista vazia.
- **6.4** `ProjectStore.saveAnalysis` (`database.withTransaction`): apaga e regrava transcript, análise e candidatos do projeto e marca `done`. Apagar os shorts antigos remove em cascata legendas e exports deles. Falha no meio = nada gravado (antes, `analysisStatus` podia ficar inconsistente com candidatos parciais). `AnalysisWorker` só chama o store.
- **6.5** `ShortRangeValidator` (domain, puro) aplica os mesmos limites da seleção: `0 <= início < fim <= vídeo`, mínimo 3 s, teto 90 s, sem sobreposição com os outros Shorts. `ProjectStore.updateShort` valida **dentro da transação** e recusa com mensagem (`ShortUpdateResult.Rejected`); nada é "corrigido" em silêncio. O `UPDATE` grava `hook` e, se o intervalo mudou, na mesma instrução: `intervalVersion + 1`, `localPath = NULL`, `status = 'pending'`, progresso/erro zerados e foco/legendas descartados; depois apaga `exports`/`subtitles` do short. A tela mostra o erro e só volta após salvar; ganhou o campo "Gancho". Corrigi também a tela, que não atualizava os campos quando os dados chegavam depois da abertura (`remember` sem chave).
- **6.6** `ExportDao.updateState` ganhou `resetCompleted`; `markQueued`/`markRunning` o usam, então um retry não mostra o `completedAtMs` da tentativa anterior.
- **6.7** `ShortDao.updateExportState` ganhou `replaceError`. O erro é preservado em progresso, `queued` e `cancelled`; é trocado com mensagem nova e limpo ao iniciar (`processing`) ou concluir (`done`).

## Validação feita
- **SQLite real (Python 3.45, `/tmp`, reproduzível)**: o script aplica v1 → v2 → v3 do próprio teste instrumentado e depois as 26 instruções de `MIGRATION_3_4_SQL` extraídas do código. Resultado: `PRAGMA foreign_key_check` vazio; órfãos descartados (1 short, 1 transcript, 1 subtitle mantidos); FKs com `CASCADE` e índices com os nomes que o Room espera; apagar o projeto zera as 5 tabelas; inserir filho sem pai falha. O `UPDATE` de `updateMetadata`, extraído do DAO: mesmo intervalo mantém `localPath`/`status`/foco; intervalo novo zera tudo e sobe `intervalVersion`.
- **JVM**: 111 testes de `:domain` (7 novos: `ShortRangeValidatorTest`).
- **Escritos, não executados**: `RepositoriesTest` ajustado + 4 testes novos (erro preservado, `completedAtMs`, entidade com legendas/foco, codec com JSON corrompido); `ProjectStoreInstrumentedTest` (7: re-análise não duplica, dependentes somem, falha não deixa estado parcial, cascata, editar intervalo invalida, editar só metadados preserva, intervalos inválidos recusados); `ShortsDatabaseMigrationTest` (1 → 4 e novo 3 → 4 com banco v3 real e órfãos). Abrir o Room sobre o banco migrado valida o schema final contra as entidades.

## Gate para concluir a Fase 6
1. `./gradlew :domain:test :data:testDebugUnitTest` verde.
2. Compilar `:data`, `:app`, `:feature-editor`; **commitar `data/schemas/**/4.json`** gerado pelo KSP.
3. `:data:connectedDebugAndroidTest` (migração + `ProjectStoreInstrumentedTest`) num emulador.
4. Manual: editar o corte de um Short já exportado, reabrir o editor (mantém o novo intervalo) e conferir que o export antigo não é reaproveitado.

## Não verificado / limitações
- Nada de Android foi compilado. Os riscos principais são de compilação (Room/KSP, `withTransaction`) e de o Room achar diferença entre o schema migrado e as entidades. O script SQLite mostra que a estrutura é a esperada, mas só o teste instrumentado confirma o hash/validação do Room.
- Sem JSON de schema da v3, não dá para usar `MigrationTestHelper` nela; a migração 3 → 4 é testada abrindo um banco v3 criado à mão. Migrações futuras (4 → 5) já podem usar o helper.
- Re-análise apaga os `exports` e `shorts` antigos, mas **não apaga os arquivos `.mp4` já exportados** no disco (limpeza de arquivos é da Fase 7).
- `shorts.localPath` continua apontando para arquivo apagado após falha/cancelamento de export (o `COALESCE` do DAO mantém o valor); tratado na Fase 7 junto do `.part`.
- O `ShortsProcessingManager` ainda não usa `focusTrackJson`/`subtitlesJson` (Fase 7); hoje ele recalcula. Não há mais reaproveitamento de export desatualizado após editar o intervalo, porque as linhas de `exports` são apagadas.
- Mínimo (3 s) e teto (90 s) do editor são os de `SelectionRules`, não configuráveis na UI. Os sliders da tela ainda limitam só 1 s de distância; o limite real vem da validação ao salvar.
- A tabela `subtitles` continua existindo (legado) mas não é mais escrita; a fonte passou a ser `shorts.subtitlesJson`.
