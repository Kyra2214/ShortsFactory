# Fase 14 — Exportação por plataforma e metadados

**Status: implementada (7/7). Suíte Gradle não executada no ambiente (sem Gradle e sem rede para o wrapper); validação pela CI e checklist em aparelho pendentes.**

Objetivo: hoje marcar várias plataformas gera um único arquivo com os mesmos parâmetros (a plataforma só entra no nome e no fingerprint). Esta fase gera um arquivo por perfil de plataforma e faz a IA preparar título, descrição e hashtags de cada uma. O app prepara arquivo e textos, mas não publica (publicar exigiria as APIs oficiais, login e aprovação de app).

Decisões:
- Parâmetros de codificação saem de uma tabela de perfis (YouTube Shorts, Instagram Reels, TikTok, Facebook Reels) conferida nas documentações oficiais na hora de implementar, com a fonte registrada; nada escrito de memória.
- A IA (Fase 13) escolhe plataformas por corte, com justificativa curta, e gera os textos; não decide parâmetros de codificação.
- Sem chave configurada: só a tabela de perfis e o título/gancho do corte, sem inventar texto.
- Planejador, gerador de metadados e escolha de plataformas são casos de uso independentes da tela, para o fluxo 100% automático futuro (o worker de análise poderá chamá-los em sequência).

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 14.1 | Perfis por plataforma no `domain` (resolução, fps, bitrate, duração máxima, margem de legenda, limites de texto) | concluído |
| 14.2 | `ExportPlanner` puro (sem ampliar a origem, aviso de duração, perfis iguais compartilham arquivo) | concluído |
| 14.3 | Exportação por plataforma no `ShortsProcessingManager` (arquivo e fingerprint por perfil, lote por perfil) | concluído |
| 14.4 | Metadados por plataforma (gerador via roteador, validação e limite de tamanho, Room v6 aditivo) | concluído |
| 14.5 | Tela: modo Automático por padrão, perfil e textos por plataforma, copiar/compartilhar, modo manual mantido | concluído |
| 14.6 | Escolha de plataformas pela IA com justificativa | concluído |
| 14.7 | Fechamento: testes, docs, checklist, suíte completa e ZIP | concluído |

## 14.1 — O que mudou
- `domain/export/PlatformProfile.kt`: `PlatformTextLimits`, `PlatformProfile` (resolução, fps, bitrate, duração máxima, margem inferior de legenda, limites de texto, `sourceNote`, `verified`, `encodingKey` para perfis iguais compartilharem arquivo) e `PlatformProfiles` (`forPlatform`, `forKey`, `all`).
- ASSUMINDO: ambiente sem rede; valores de referência NÃO conferidos nas documentações oficiais. Todos os perfis têm `verified = false` e `sourceNote` indicando isso. Pendente: conferir cada plataforma na fonte oficial, registrar a fonte e só então marcar `verified = true`.
- Nenhum consumidor ainda (planner no 14.2, exportação no 14.3). Não compilado/testado.

## 14.2 — O que mudou
- `domain/export/ExportPlanner.kt`: `ExportPlanRequest`, `PlatformExportPlan`, `ExportFileGroup`, `ExportPlan` e `ExportPlanner.plan`, puro (sem Android/IO).
- Sem ampliar a origem: altura da saída = min(perfil, altura do recorte 9:16 possível da origem), par; largura derivada em 9:16; fps limitado ao da origem quando informado; `reducedBySource` sinaliza a redução.
- `exceedsMaxDuration` avisa quando o corte passa da duração máxima do perfil (não bloqueia). Plataformas com a mesma chave (resolução|fps|bitrate) caem no mesmo `ExportFileGroup`.
- ASSUMINDO: o recorte vertical de origem paisagem usa a altura inteira da origem (ex.: 1920x1080 → 606x1080). Sem consumidor ainda (14.3). Não compilado/testado.

## 14.3 — O que mudou
- `ShortsProcessingManager.exportBatch` ganha `perPlatformProfiles: Boolean = false`. Com `true`, o `ExportPlanner` gera um job por grupo de arquivo (plataformas com a mesma codificação compartilham o arquivo) e o lote percorre candidato × job; `total` = candidatos × jobs. Com `false` (padrão), o comportamento manual anterior permanece (um job com resolução/qualidade/fps escolhidos).
- Arquivo, fingerprint, reuso e registro em `exports` passam a usar os valores do job: `platform` = chaves do grupo (ex.: `yt,tt`), `quality` = `Perfil`, resolução e fps do plano, bitrate do perfil; saída validada contra a resolução do job.
- ASSUMINDO: origem com dimensões inválidas (<= 0) não limita a resolução. `ShortEntity` guarda um único caminho exportado: com vários arquivos por corte, o último concluído vale; a lista por plataforma fica para a tela (14.5). `ExportWorker`/UI ainda chamam com o padrão `false` (ligação no 14.5). Não compilado/testado.

## 14.4 — O que mudou
- `domain/export/PlatformMetadata.kt`: `PlatformMetadata`, `PlatformMetadataRequest`, `PlatformMetadataValidator` (limites do perfil: título, descrição descontando as hashtags, quantidade e formato das hashtags, sem duplicadas), `PlatformMetadataParser` (JSON da IA; itens inválidos ou de plataforma não pedida são ignorados) e `PlatformMetadataGenerator` (caso de uso independente de tela; falha ou resposta sem item válido lança `AiException`, sem texto inventado; o fallback "só título/gancho" fica para quem chama, 14.5).
- Roteador: `AIProvider.generateText(prompt)` (padrão lança `AiException`), implementado em `MultiAIProvider` (fallback/estatística), `GrokProvider` (e `FreeApiProvider` por herança) e `FreeApisAIProvider`.
- Room v6 aditivo: tabela `short_platform_metadata` (FK com cascata para `shorts`, índice único `shortId+platform`, hashtags em JSON), `ShortPlatformMetadataDao`, `MIGRATION_5_6`, `PlatformMetadataRepository` e DI. `ShortsDatabaseMigrationTest` existente ajustado para a cadeia até a v6.
- Sem consumidor ainda (tela e chamada no 14.5; escolha de plataformas no 14.6). Não compilado/testado; o schema JSON da v6 é gerado no build.

## 14.5 — O que mudou
- `ExportScreen`: seletor Modo (Automático por padrão, Manual mantido). Automático mostra o resumo do perfil de cada plataforma marcada (resolução, fps, Mbps, duração máxima, aviso de valores não conferidos) no lugar de qualidade/resolução/fps; Manual é a tela anterior. Nova seção "Textos por plataforma": botão de gerar e, por Short, cartões por plataforma com Copiar (área de transferência) e Compartilhar (`ACTION_SEND`).
- Ligação do automático: `onExport(..., automatic)` → `ExportViewModel.startExport` → `ShortsWorkScheduler.enqueueExport(perPlatformProfiles)` → `WorkKeys.PER_PLATFORM` → `ExportWorker` → `exportBatch(perPlatformProfiles)`. Retomada ao reabrir o app reconhece o lote automático pela qualidade `Perfil` e retoma com as plataformas de todos os exports pendentes.
- `ExportViewModel.generateMetadata`: usa `PlatformMetadataGenerator` via `AIProvider` (roteador) por Short e persiste o resultado em `short_platform_metadata`; sem IA/falha, `PlatformMetadataValidator.fallback` mostra só título e gancho (não persistido) com aviso. Textos salvos são carregados ao abrir a tela.
- ASSUMINDO: a margem de legenda do perfil (`subtitleBottomMarginFraction`) ainda não é aplicada à posição da legenda no export (fora do escopo listado; avaliar no 14.7). Não é possível editar os textos na tela. Não compilado/testado.

## 14.6 — O que mudou
- `domain/export/PlatformSelector.kt`: `PlatformSuggestion`, `PlatformSuggestionRequest`, `PlatformSuggestionParser` e `PlatformSelector` (caso de uso via `AIProvider.generateText`, independente de tela). A saída da IA só vale para plataformas candidatas, sem repetição, com justificativa não vazia (até 160 caracteres) e cuja duração máxima comporta o corte. A IA não decide parâmetros de codificação. Sem IA ou sem sugestão válida lança `AiException`; nada é inventado.
- `ExportViewModel.suggestPlatforms` (um pedido por Short, duração do corte no prompt) e, em `ExportScreen`, botão "Sugerir plataformas com IA", cartões com plataforma e justificativa por Short e "Aplicar sugestão às plataformas" (marca a união das sugeridas). Sem IA: aviso para escolher manualmente. Sugestões não são persistidas (sem mudança de Room).
- Não compilado/testado.

## 14.7 — O que mudou
- Testes JVM novos no `domain` (escritos, não executados): `PlatformProfilesTest` (5), `ExportPlannerTest` (8), `PlatformMetadataTest` (12) e `PlatformSelectorTest` (6), com `FakeTextAi` e o auxiliar `failsWith` para exceções em testes suspensos.
- `docs/fases/FASE-14-CHECKLIST-APARELHO.md`, README, Roadmap e Changelog atualizados.
- Verificação estática local: balanceamento de delimitadores em todos os `.kt` alterados, ausência de `kotlin.test` nos testes novos (o módulo só tem JUnit/coroutines-test) e `AIProvider.generateText` com implementação padrão (fakes existentes continuam válidos).
- Suíte completa (`./gradlew :domain:test test lint assembleDebug assembleRelease`) e `:data:connectedDebugAndroidTest` **não foram executados**: o ambiente não tem Gradle nem acesso à rede para baixar o wrapper. Rodar na CI antes do merge; o primeiro erro de compilação provável está nos módulos Android (`feature-export`, `app`, `data`), que nunca foram compilados nesta fase.
- Pendências conhecidas: perfis com `verified = false` (valores não conferidos nas fontes oficiais); margem de legenda do perfil não aplicada ao export; `ShortEntity` guarda um único caminho exportado por corte.
