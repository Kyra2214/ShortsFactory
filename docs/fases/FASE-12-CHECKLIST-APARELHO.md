# Fase 12 — Checklist de validação em aparelho

Aparelho arm64 real (API 26+ e, se possível, API 34+). Marcar após a CI compilar a fase.

## Leitor (`SfVideoPlayer`)
- [ ] Reproduz/pausa pelo botão e por toque no vídeo; barra de progresso faz seek; tempo atualiza.
- [ ] Pausa ao enviar o app para segundo plano; sem vazamento ao sair da tela (ExoPlayer liberado).
- [ ] Arquivo fora de `filesDir`/`cacheDir` ou inexistente mostra "Arquivo de vídeo indisponível.".
- [ ] Vídeo corrompido mostra a mensagem de erro sem fechar o app.

## Prévia rápida (Editor)
- [ ] Toca somente o trecho, em loop, em moldura 9:16 (corte central).
- [ ] Soltar o `RangeSlider` recarrega o trecho e reinicia no início do trecho.
- [ ] Trecho no fim do vídeo e trecho de 1 s funcionam.

## Prévia fiel (Editor)
- [ ] "Prévia fiel" desabilitado com trecho não salvo; habilitado após salvar.
- [ ] Mostra progresso; "Cancelar" interrompe o FFmpeg e não deixa `.part` em `cache/preview/`.
- [ ] Enquadramento, legendas e tempos coincidem com o export (comparar o mesmo corte).
- [ ] Segunda abertura, sem alterações, usa o cache (sem nova renderização).
- [ ] Alterar o intervalo e salvar gera nova prévia e remove a antiga do mesmo Short.
- [ ] A prévia rápida fica pausada enquanto o diálogo está aberto.

## Assistir exportados (Projeto)
- [ ] Botão aparece só nos cortes concluídos; abre o arquivo final exportado.
- [ ] Editar o intervalo remove o botão; o diálogo aberto fecha sozinho.
- [ ] Apagar o arquivo exportado remove o botão após recarregar a lista.

## Regressão
- [ ] Exportação em lote (progresso, cancelamento, reuso de resultado) segue igual após a extração de `assembleClipSpec`.
