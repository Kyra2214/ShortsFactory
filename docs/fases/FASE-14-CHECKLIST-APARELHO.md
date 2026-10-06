# Fase 14 — Checklist em aparelho

Execute em um aparelho arm64 real, com um vídeo longo importado e a análise concluída. Marque cada item só após ver o resultado.

## Exportação por plataforma (modo Automático)
- [ ] A tela abre em **Automático**; o resumo de perfil aparece para cada plataforma marcada (resolução, fps, Mbps, duração máxima).
- [ ] Marcar as 4 plataformas gera **um arquivo por perfil distinto** (perfis iguais compartilham o arquivo); conferir os arquivos em `exports/<projectId>/`.
- [ ] Origem menor que o perfil (ex.: 1080p paisagem) **não é ampliada**; a resolução do arquivo bate com o plano.
- [ ] Corte maior que a duração máxima de uma plataforma: o aviso aparece e a exportação não é bloqueada.
- [ ] Reexportar sem mudar nada reaproveita os arquivos (sem reprocessar).
- [ ] Cancelar durante o lote: itens ficam `cancelled`, sem arquivo `.part` sobrando.
- [ ] Fechar e reabrir o app durante o lote: o progresso é restaurado e o lote automático é retomado como automático.
- [ ] **Manual** continua gerando um único arquivo com qualidade/resolução/fps escolhidos.

## Perfis
- [ ] Conferir cada perfil (resolução, fps, bitrate, duração máxima, margem de legenda, limites de texto) na documentação oficial da plataforma, registrar a fonte em `PlatformProfile.sourceNote` e só então marcar `verified = true`. Hoje todos estão como **não verificados**.
- [ ] A margem inferior de legenda do perfil ainda **não é aplicada** à posição da legenda no export; decidir se entra.

## Textos por plataforma
- [ ] Com chave de IA configurada, "Gerar títulos, descrições e hashtags" preenche os cartões de cada Short; títulos/descrições/hashtags respeitam os limites.
- [ ] Reabrir a tela mantém os textos salvos.
- [ ] Copiar coloca o texto na área de transferência; Compartilhar abre o seletor do sistema.
- [ ] Sem chave de IA: aparece só título e gancho, com o aviso de que não veio da IA.

## Escolha de plataformas pela IA
- [ ] "Sugerir plataformas com IA" mostra plataforma + justificativa curta por Short; nenhuma plataforma sugerida tem duração máxima menor que o corte.
- [ ] "Aplicar sugestão às plataformas" marca a união das sugeridas.
- [ ] Sem chave de IA: aviso para escolher manualmente, sem sugestão inventada.

## Banco
- [ ] Atualizar de uma instalação com a versão anterior (Room v5) para a v6 sem perder projetos, cortes e exportações.
