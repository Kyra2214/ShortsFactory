# Visibilidade e proteção do repositório

Atualizado em 2026-10-06.

## Visibilidade

`Kyra2214/ShortsFactory` está **público**. A alteração foi feita após confirmação explícita do proprietário para habilitar proteção de branch no plano GitHub disponível: a API do GitHub recusou branch protection e rulesets quando o repositório era privado, informando que era necessário GitHub Pro ou tornar o repositório público.

Ao ficar público, o código, o histórico Git e o histórico/logs do GitHub Actions podem ser vistos por qualquer pessoa. A decisão foi confirmada pelo proprietário.

## Regras de `main`

A API do GitHub confirmou `protected: true` para `main`, com estas regras:

- Force-push: **bloqueado**.
- Exclusão da branch: **bloqueada**.
- Checks obrigatórios antes do merge:
  - `Unit tests, lint and release validation`
  - `Instrumented Android tests`
- Aplicação das regras a administradores: **ativada**.
- A branch não precisa estar atualizada com a base para satisfazer a regra de checks (`strict: false`).
- Aprovações de revisão humana: **não exigidas** (`required_pull_request_reviews: null`).
- Restrições específicas de quem pode enviar commits: **não configuradas**.

A última validação antes destas regras foi aprovada no [run 37439712583](https://github.com/Kyra2214/ShortsFactory/actions/runs/37439712583), no commit `372137f`; incluindo 9 testes instrumentados Room no API 35. As regras tornam esses dois checks obrigatórios para merges em `main`.
