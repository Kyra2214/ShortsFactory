# Roadmap de implementação — 12 fases

> **Estado atual:** PR #3 foi mesclado em `main` no commit `dae752f`. As Fases 11–12 estão na branch `feature/phase12-video-preview`, no PR #4, e foram validadas pelo workflow [37530672897](https://github.com/Kyra2214/ShortsFactory/actions/runs/37530672897) no commit `6dd5565`: testes JVM, lint debug/release, builds APK e testes instrumentados Room no API 35 passaram. O checklist funcional em aparelho arm64 real permanece pendente.
>
> **Fonte operacional:** `docs/VALIDACAO-INTEGRACAO-FASE-02.md` e os relatórios em `docs/fases/`. `docs/AUDITORIA-E-FASES.md` é uma auditoria estática histórica de um snapshot anterior; não representa sozinha o estado atual.

Este documento é a referência operacional do ciclo. Uma fase só é considerada concluída quando o código executa o caminho real, os componentes faltantes são implementados e os testes/CI que cobrem o contrato passam.

## Fase 1 — Pipeline real
- Entrada validada antes de iniciar processamento.
- Áudio extraído localmente.
- Transcrição executada pelo serviço configurado.
- Análise IA executada.
- Seleção determinística executada.
- Legendas derivadas da transcrição.
- Focus track calculado pelo engine quando disponível, com fallback central determinístico.
- Cancelamento/erro refletem a etapa real.

## Fase 2 — Legendas reais
- Segmentos de transcript convertidos em segmentos de legenda.
- Timestamps recortados ao intervalo do candidato.
- O Android renderiza cada segmento em PNG transparente e o FFmpeg aplica os overlays no intervalo relativo do clipe; não depende de `drawtext`/libass.
- Exportação usa transcript como fallback quando não houver artefato de legenda persistido.

## Fase 3 — Focus tracking
- Engine amostra frames.
- ML Kit detecta o maior rosto.
- Bounding box é normalizado e suavizado.
- Crop vertical usa a trilha.
- Ausência de rosto usa foco central seguro.

## Fase 4 — Seleção/ranking
- IA fornece score inicial.
- Score determinístico combina score IA, qualidade do hook, densidade de fala e adequação de duração.
- CandidateSelector remove intervalos inválidos, longos e sobrepostos.

## Fase 5 — Importação
- Arquivo local via SAF.
- URL HTTP/HTTPS.
- Download em arquivo parcial.
- Retomada via Range quando suportada.
- Rejeição de HTML/resposta inválida.
- Limite de tamanho e arquivo vazio tratados.
- DRM/login/paywall não são contornados.

## Fase 6 — IA/fallback
- Provider abstrato.
- OpenAI e xAI/Grok plugáveis.
- Fallback entre providers.
- Fallback entre chaves/modelos no provider.
- Erros não são convertidos silenciosamente em resultado falso.

## Fase 7 — Editor
- Intervalo e metadata do candidato são persistidos.
- Alterações respeitam intervalo válido.
- Exportação usa o estado persistido do candidato.

## Fase 8 — Exportação
- FFmpeg local.
- 9:16 configurável.
- FPS/bitrate configuráveis.
- Legenda e foco aplicados.
- Arquivo final validado.
- Estados e tentativas persistidos.
- Resultado concluído pode ser reutilizado.

## Fase 9 — Trends
- Radar por região/plataforma/nicho.
- Dados externos permanecem distinguíveis de inferências da IA.
- Providers sem API real não são apresentados como métricas oficiais.

## Fase 10 — Batch
- Vários candidatos processados em fila.
- Falha de um item não mascara os demais.
- Cancelamento destrói o processo FFmpeg.
- Progresso agregado persistido/emitido.

## Fase 11 — QA
- Testes JVM.
- Testes de pipeline.
- Testes de seleção/ranking.
- Testes de parser.
- Testes Room/migração.
- Lint e builds debug/release.
- Instrumentação Android.

## Fase 12 — Release
- APK debug/release reproduzível.
- CI sem segredo de IA.
- Chaves somente em armazenamento seguro do dispositivo.
- Binários FFmpeg/ffprobe incluídos no artefato.
- Documentação alinhada ao comportamento real.

## Fase 11 — Redesign da interface (extra, fora do plano 0–10)
Detalhes e submódulos em `docs/fases/FASE-11-REDESIGN-UI.md`. Progresso: 7/7 (11.1 a 11.7 implementados; CI aprovada no run 37530672897).

## Fase 12 — Leitor de vídeo interno (extra)
Detalhes em `docs/fases/FASE-12-LEITOR-DE-VIDEO.md`. Progresso: 5/5 (12.1 a 12.5 implementados; CI aprovada; checklist em aparelho pendente).

## Fase 13 — APIs gratuitas de IA (extra)
Detalhes em `docs/fases/FASE-13-APIS-GRATUITAS.md`. Progresso: 6/6 (13.1 a 13.6 implementados; CI e checklist em aparelho pendentes).

## Fase 14 — Exportação por plataforma e metadados (extra, depende da Fase 13)
Detalhes em `docs/fases/FASE-14-EXPORT-POR-PLATAFORMA.md`. Progresso: 7/7 (14.1 a 14.7 implementados; CI e checklist em aparelho pendentes).

## Progresso (plano corrigido)
Fases 0–10: implementação integrada em `main`; 139 testes de `:domain` e 5 verificações com FFmpeg real passaram localmente, e o workflow pós-merge está verde. Fases 11–12: implementação integrada na branch de entrega e validada pela CI; a reprodução em aparelho real ainda depende do checklist manual. Não declarar a validação de aparelho concluída sem essa execução.

Fase 11 (redesign de UI): 7/7 submódulos (100%), CI aprovada; validação em aparelho pendente.

Fase 14 (exportação por plataforma e metadados): 7/7 submódulos (100%); 31 testes JVM novos escritos e não executados; CI e validação em aparelho pendentes.

## Regra de encerramento

Não marcar uma fase como concluída por existir uma classe ou comentário que descreva a funcionalidade. A evidência é o caminho executável no código + teste/validação correspondente.
