# Fase 13 — Checklist de validação em aparelho

Aparelho real com internet. Marcar após a CI compilar a fase. Use chaves gratuitas de teste, nunca de produção.

## Ajustes → APIs gratuitas
- [ ] Os 10 provedores aparecem desligados; provedores da China exibem o aviso de envio externo.
- [ ] "Cadastrar-se" e "Documentação" abrem as páginas corretas.
- [ ] Chave inválida: mensagem de recusa e nada é salvo; sem rede: mensagem de conexão e nada é salvo.
- [ ] Chave válida: provedor passa para "Ligado"; reabrir a tela mantém o estado; o campo não mostra a chave.
- [ ] "Remover chaves" desliga o provedor.

## Análise
- [ ] Sem OpenAI/xAI e com um provedor gratuito ligado, a análise de um vídeo curto conclui e o candidato mostra provedor/modelo reais.
- [ ] Com dois provedores gratuitos, chave inválida/limite em um faz a análise cair para o outro.
- [ ] Sem nenhuma chave, a análise falha com mensagem clara (sem resultado falso).
- [ ] Tendências: provedor gratuito não é usado para busca ao vivo (cai para o próximo/links).

## Regressão
- [ ] OpenAI e xAI continuam sendo tentadas primeiro, com o mesmo comportamento.
- [ ] Tela "IA e chaves" (xAI/OpenAI) segue igual.
