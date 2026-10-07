# Correções: Google Trends e fallback de IA

## Escopo

Esta alteração liga o Caçador de Tendências a duas fontes de forma sequencial: primeiro tenta o feed RSS público do Google Trends, sem chave; se o país não tiver feed, a rede falhar ou nenhum item combinar com o nicho, consulta o provedor de IA configurado. Também remove uma exceção que fazia cada provedor do catálogo gratuito falhar imediatamente na busca de tendências.

## Comportamento do feed

- Endpoint: `https://trends.google.com/trending/rss?geo=<GEO>`.
- GEOs habilitados: Brasil (`BR`), Estados Unidos (`US`), Japão (`JP`) e Coreia do Sul (`KR`). Global e China não estão mapeados.
- Limites de conexão e leitura: 8 segundos cada; no máximo 15 cards são exibidos.
- O filtro do nicho procura palavras inteiras, sem diferenciar maiúsculas/minúsculas, no título da tendência ou nos títulos de notícias associados. Nicho vazio não filtra.
- País não suportado, resposta de rede inválida ou feed sem correspondências retorna lista vazia e aciona o fallback por IA. O cancelamento do trabalho é propagado ao chamador, não convertido em lista vazia.
- Os cards do feed têm origem `LINK_ONLY`, não informam visualizações/engajamento e abrem uma busca Google pelo título; o tráfego aproximado do RSS pode aparecer como texto do título, não como métrica validada de vídeo.

## Fallback e proveniência

`FreeApiProvider` herda a busca em linguagem natural compatível com OpenAI implementada por `GrokProvider`. Assim, os provedores gratuitos com chave salva podem participar do encadeamento de `MultiAIProvider` e do roteamento estatístico já existente, em vez de rejeitar `searchTrends` antes da chamada. Conteúdo criado pelo modelo continua sendo inferência: não equivale a dados oficiais ou a uma API de tendências ao vivo, e deve manter a origem/métricas rotuladas de acordo com essa distinção.

## Limitações conhecidas

- O RSS é uma lista de pesquisas em alta do país; a seleção de período da tela não altera a janela do feed. O período é incluído na consulta de IA somente no fallback.
- A correspondência textual é deliberadamente simples: exige pelo menos uma palavra inteira do nicho no título ou nas notícias, não usa similaridade semântica.
- O comportamento depende da disponibilidade e do formato público do RSS do Google Trends. Falhas de rede, HTTP ou parse seguem para o fallback de IA.
- As APIs gratuitas ainda enviam o prompt da busca ao provedor escolhido. A integração não publica conteúdo nas plataformas.

## Validação

`GoogleTrendsSourceTest` cobre nicho vazio, correspondência sem distinção de caixa no título/notícias e limite de palavra inteira. O workflow principal também executa testes JVM, lint, builds debug/release e testes instrumentados Room; consulte o run vinculado ao pull request para o resultado desta revisão.
