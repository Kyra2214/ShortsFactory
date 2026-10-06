# Fase 13 — APIs gratuitas de IA

**Status: implementada (6/6). Suíte Gradle não executada no ambiente (sem acesso à rede para o wrapper); validação pela CI e checklist em aparelho pendentes.**

Objetivo: usar os provedores de IA com plano gratuito (catálogo vindo do BrainCode) como provedores de análise, com cadastro de chaves nos Ajustes e roteamento com fallback. Do BrainCode entram só o catálogo, a descoberta dinâmica de modelos, o roteamento por estatística e a tela de cadastro; o restante (secretário, memória, planner, contas, sandbox) fica de fora.

Decisões:
- Todos os 10 provedores do catálogo (qwen, moonshot, volcengine, siliconflow, modelscope, openrouter, groq, gemini, mistral, zai), inclusive os chineses.
- Provedores desligados até o usuário salvar uma chave; aviso de que a transcrição do vídeo sai do aparelho (reforçado nos chineses).
- Os IDs de modelo do catálogo são só ponto de partida; a lista real vem da descoberta em tempo de execução (sem números de limite fixos).
- `GrokProvider` e `OpenAiProvider` atuais não mudam; a camada de chat é reaproveitada, não copiada do BrainCode.
- Provedores gratuitos não têm busca ao vivo: `searchTrends` fica não suportado, e o `MultiAIProvider` cai para o próximo.

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 13.1 | Catálogo: asset JSON, modelo e parser no `domain`, testes | concluído |
| 13.2 | Chaves por provedor no `SecureKeyStore` | concluído |
| 13.3 | Tela de cadastro (cadastrar-se, docs, salvar, testar) nos Ajustes | concluído |
| 13.4 | `FreeApiProvider` com descoberta dinâmica de modelos gratuitos | concluído |
| 13.5 | Roteamento por estatística no `MultiAIProvider` | concluído |
| 13.6 | Fechamento: testes, docs, checklist, suíte completa e ZIP | concluído |

## 13.1 — O que mudou
- `feature-ai/src/main/assets/ai_api_catalog.json`: catálogo dos 10 provedores (versão 2026-09-23.1), copiado do BrainCode sem alterações.
- `domain/ai/catalog/ApiCatalog.kt`: `ApiAccess`, `ApiRegion`, `ApiModelSeed`, `ApiProviderEntry` (`chatBaseUrl`, `chatCompletionsUrl`, `modelsEndpoint` declarado ou `<base>/models`), `ApiCatalog` e `ApiCatalogParser`.
- O parser aceita só modelos `FREE_TIER`/`FREE_PERMANENT`, URLs HTTPS e IDs únicos; entradas inválidas são descartadas e listadas em `skipped`, sem derrubar o catálogo. JSON malformado ou sem `providers` lança `IllegalArgumentException`.
- `ApiCatalogParserTest` (11 testes JVM, incluindo a conferência do catálogo embutido). A leitura do asset por `Context` entra no 13.2/13.3. Escritos, não executados.

## 13.2 — O que mudou
- `core/SecureKeyStore`: `getProviderKeys`, `saveProviderKeys`, `hasProviderKey`, `clearProviderKeys` e `configuredProviderIds`, com lista de chaves por provedor (ordem de tentativa, sem duplicação) em `EncryptedSharedPreferences` sob o prefixo `free_api_keys_<id>`.
- ID do provedor normalizado (minúsculas, `[a-z0-9_-]`, até 40 caracteres); ID inválido é ignorado. Lista vazia remove a entrada, o que mantém o provedor desligado.
- Chaves do Grok (`xai_api_keys`) e da transcrição OpenAI não mudam. Nenhum consumidor novo ainda (UI no 13.3, uso no 13.4). Não compilado/testado.

## 13.3 — O que mudou
- `feature-ai`: `ApiCatalogLoader` (lê o asset por `Context`, com cache), `FreeApiKeyTester` (GET no `modelsEndpoint` com `Bearer`; 2xx válido, 401/403 recusada, demais erros sem salvar) e `FreeApiSettingsScreen` (um cartão por provedor: estado ligado/desligado, Cadastrar-se, Documentação, chave mascarada, Testar e salvar, Remover chaves).
- Aviso de que a transcrição sai do aparelho na tela, com destaque extra para provedores de região `CHINA`. A chave só é salva após validação; remover chaves desliga o provedor.
- Navegação: rota `free_api_settings` e cartão "APIs gratuitas" em Ajustes (`SettingsScreen.onOpenFreeApis`).
- O teste valida só a chave no endpoint de modelos (sem enviar conteúdo). Uma chamada de chat real fica para o 13.4. Provedores ainda não são usados na análise. Não compilado/testado.

## 13.4 — O que mudou
- `domain/ai/catalog/FreeModelDiscovery`: `parse` (aceita `data`/`models`, remove prefixo `models/`, lê `pricing`) e `select` (descarta modelos não-texto; com preço informado só gratuitos; sem preço, nomes de porte leve primeiro; no máximo 5 por chave).
- `feature-ai/FreeApiProvider`: estende a camada de chat do `GrokProvider` (descoberta de modelos, fallback chave/modelo) via `ChatProviderConfig.free(entry)` e novo `modelResolver`; `searchTrends` não suportado (o `MultiAIProvider` cai para o próximo). IDs de partida com "bootstrap" nunca são enviados.
- `FreeApisAIProvider` monta a cada chamada os provedores com chave salva (ordem do catálogo) e entra no `AiModule` depois de OpenAI e Grok. Ordem por estatística fica no 13.5.
- `GrokProvider` e `OpenAiProvider` mantêm o comportamento (o resolvedor é opcional). Não compilado/testado.

## 13.5 — O que mudou
- `domain/ai/routing/ProviderRouting.kt`: `ProviderRouting` (ordenar/registrar) e `ProviderStatsTracker`. Pontuação = taxa de sucesso suavizada × leve fator de latência; quarentena temporária (pontuação ×0,2) após falhas seguidas, com espera crescente; sem histórico fica no meio; empates preservam a ordem do catálogo. Histórico recente (contadores reduzidos à metade acima de 200 amostras) e serialização JSON.
- `MultiAIProvider`: parâmetro opcional `routing` (padrão nulo = comportamento anterior, usado por OpenAI/Grok). Com ele, reordena os provedores e registra sucesso/falha e latência de cada tentativa. `searchTrends` não registra (não suportado nos gratuitos não conta como falha).
- `FreeApisAIProvider` persiste as estatísticas em `SharedPreferences` (`free_api_routing_stats`, sem dados sensíveis) e as usa no roteamento entre os provedores gratuitos.
- ASSUMINDO: nome do provedor (`providerName`) identifica a estatística. Não compilado/testado.

## 13.6 — Fechamento
- Testes JVM novos no `:domain`: `FreeModelDiscoveryTest` (7) e `ProviderStatsTrackerTest` (8), somando-se aos 11 de `ApiCatalogParserTest`. Escritos, não executados: `./gradlew` não pôde baixar a distribuição Gradle (sem rede).
- Verificação estática local: referências, imports e assinaturas dos arquivos da fase conferidos; nenhuma alteração fora do escopo da fase.
- README com a seção "APIs gratuitas de IA"; checklist em `FASE-13-CHECKLIST-APARELHO.md`.
- Pendente: rodar `./gradlew :domain:test test lint assembleDebug assembleRelease` na CI e o checklist em aparelho.
