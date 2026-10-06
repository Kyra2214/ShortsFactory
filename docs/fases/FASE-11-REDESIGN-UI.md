# Fase 11 — Redesign da interface

**Status: submódulos 11.1 a 11.7 implementados (7/7). Não compilado/testado; validação pela CI.**

Escopo: somente composables e tema. ViewModels, rotas, domínio e dados permanecem inalterados. Sem novas dependências.

## Direção visual
- Cor: grafite azulado (`#14161B`; superfícies `#1D2027`/`#272B34`), claro/escuro pelo sistema. Acento azul-ultramar (`#4F5BFF` no claro, `#8D96FF` no escuro); âmbar reservado para nota/score.
- Tipografia: fonte do sistema, escala própria em `Type.kt`.
- Formas: raios por hierarquia (6/10/14/20/28 dp), sem raio único.
- Sem emoji em títulos, sem cards decorativos repetidos.

## Submódulos
| # | Submódulo | Estado |
|---|-----------|--------|
| 11.1 | Tema e componentes base (`core/ui`) | concluído |
| 11.2 | Navegação inferior (Início, Tendências, Ajustes) e Home | concluído |
| 11.3 | Projeto (candidatos ranqueados, score) | concluído |
| 11.4 | Editor (seleção de trecho, publicação) | concluído |
| 11.5 | Exportação | concluído |
| 11.6 | Tendências (Radar + Caçador unificados) | concluído |
| 11.7 | Ajustes e IA | concluído |

## 11.1 — O que mudou
- `core/ui`: `Color.kt` (esquemas claro/escuro), `Type.kt`, `Shape.kt`, `Theme.kt` (`ShortsFactoryTheme`), `Components.kt` (`SfSectionTitle`, `SfScoreBadge`).
- `MainActivity` usa `ShortsFactoryTheme` no lugar de `MaterialTheme` padrão.
- `themes.xml` (claro e `values-night`): janela/barras na cor de fundo, sem flash branco no modo escuro; manifesto aponta para `Theme.ShortsFactory`.

## 11.2 — O que mudou
- `AppNavGraph`: `Scaffold` com `NavigationBar` (Início, Tendências = `Routes.RADAR`, Ajustes) visível só nas três rotas de topo; troca de aba com `saveState`/`restoreState`. Rotas e ViewModels inalterados.
- `HomeScreen` reescrita: sem `TopAppBar`, painel único "Novo Short" (escolher vídeo + campo de URL com botão de envio), seção "Tendências agora" com chips de região (mesma chamada a `analyzer.analyze`, atalho para o Caçador), lista de projetos com divisores, bloco de inicial e duração `m:ss`; emoji removidos.
- Removido o card duplicado "Radar de Conteúdo" e os parâmetros `onOpenRadar`/`onOpenSettings` de `HomeScreen` (cobertos pela barra inferior).

## 11.3 — O que mudou
- `ProjectScreen` (novo projeto e projeto existente): assinaturas, `CandidateUi`, `PipelineStatusCard`, `CandidateCard` e `formatDuration` preservados.
- Barra superior na cor de fundo; lista (`LazyColumn`) com status da pipeline, erro, duração alvo, análise e cortes.
- Duração alvo em `FilterChip` com seleção pelo parâmetro `preset` (antes ignorado e com rótulo truncado em 8 caracteres). ASSUMINDO: `preset` corresponde a `DurationPreset.key`.
- Cortes ordenados por score (maior primeiro), com `SfScoreBadge`, intervalo e duração em segundos, gancho, tema e botão de editar; linha inteira clicável.
- Etapas da pipeline com ícones no lugar dos símbolos de texto; "Exportar em lote" fixo na parte inferior.

## 11.4 — O que mudou
- `ShortEditorScreen`: assinatura e callbacks preservados.
- Dois `Slider` substituídos por um `RangeSlider`, com leitura grande de Início, Duração e Fim e limite 0:00 a fim do vídeo. Mantida a regra de intervalo mínimo de 1 s entre início e fim.
- Campos agrupados em "Publicação" (descrição com 3 linhas mínimas, cantos do tema); erro em bloco `errorContainer`.
- "Visualizar" e "Salvar" fixos no rodapé, acima do teclado (`imePadding`).
- Prévia 9:16 embutida não implementada: não há player no módulo e `onPreview` segue delegado ao player externo. Mantido o escopo (sem novas dependências); item removido do 11.4.

## 11.5 — O que mudou
- `ExportScreen`: assinatura e callbacks preservados.
- Plataformas, qualidade, resolução e FPS em `FilterChip` (plataformas multi-seleção; demais seleção única), todos desabilitados durante a exportação. Corrige o FPS, que antes não mostrava a opção selecionada.
- Contagem de Shorts em destaque; erro em bloco `errorContainer`.
- Rodapé fixo: botão "Iniciar exportação"/"Exportar novamente" ou, em execução, progresso, "Processando X de Y" e "Cancelar". O progresso fica sempre visível.
- Nota de privacidade mantida como texto simples.

## 11.6 — O que mudou
- `ContentRadarScreen` (aba Tendências) agora hospeda dois modos em controle segmentado: Explorar (`RadarContent`) e Caçador (`HunterContent`). Assinaturas de `ContentRadarScreen` e `TrendHunterScreen` preservadas; a rota `HUNTER` e o atalho da Home continuam funcionando (`TrendHunterScreen` reutiliza `HunterContent`). `onBack` fica sem uso na aba.
- Região, plataforma(s) e período em `FilterChip` (o período substitui o dropdown); emoji e bandeiras removidos.
- Correção: o título da análise rápida usava a região do filtro de busca em vez da região analisada; agora usa a região clicada.
- Cards de resultado em superfícies do tema; selo de origem como etiqueta (destaque só para dado oficial); regras de exibição de métricas inalteradas.
- Novos auxiliares internos em `TrendsUi.kt`. Estado de cada modo é perdido ao alternar entre eles.

## 11.7 — O que mudou
- `SettingsScreen` (aba Ajustes): assinatura preservada; `onBack` sem uso na aba. Resolução, qualidade, FPS e duração em `FilterChip`; estilo de legenda em linhas selecionáveis com descrição; entrada "IA e chaves" navegável. Botão fixo "Salvar alterações" habilitado só quando há mudança ("Tudo salvo" caso contrário); a gravação continua explícita via `saveSettings`.
- `GrokSettingsScreen`: lógica de chaves, teste e persistência intacta; apenas apresentação (seções xAI/OpenAI, superfícies do tema, campos e botões com formas do tema, blocos de status).
- Mensagem de `GrokProvider` sem chave agora aponta para "Ajustes, IA e chaves".

## Fechamento da fase
- Gates pendentes: suíte completa (`:domain:test`, `testDebugUnitTest`, `lintDebug lintRelease`, builds e testes instrumentados) só executáveis na CI; nenhuma execução local possível (sem Gradle/SDK).
- Pontos a verificar na CI: APIs de Material 3 1.3.1 usadas (`RangeSlider`, `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `FilterChip`), ícones `Icons.AutoMirrored.Filled.TrendingUp`/`KeyboardArrowRight` e `Icons.Filled.Cancel`/`Error`, `Icons.Outlined.RadioButtonUnchecked`.
