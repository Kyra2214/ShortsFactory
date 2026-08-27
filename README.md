# ShortsFactory

ShortsFactory é um aplicativo Android modular para transformar vídeos longos em candidatos a Shorts, com análise de transcrição por IA, edição de metadata, tendências e exportação vertical com FFmpeg local. O processamento de mídia ocorre no dispositivo; a integração com o provedor Grok só é acionada quando o usuário configura uma chave na tela de configurações.

## Estado atual

O repositório contém a primeira rodada de saneamento técnico aplicada ao projeto enviado. O fluxo de ViewModels usa Hilt, a navegação observa estados assíncronos corretamente, a persistência de metadata foi separada dos campos originais da análise, a migração Room é não destrutiva e transcript/resultado de análise usam JSON estruturado.

A transcrição ainda é uma implementação demonstrativa que gera segmentos marcadores. Ela está isolada atrás de `TranscriptionService` para que um serviço real possa ser conectado posteriormente sem alterar a pipeline de análise.

## Arquitetura

| Módulo | Responsabilidade |
| --- | --- |
| `app` | Activity, composição Hilt e navegação Compose |
| `core` | Armazenamento seguro da chave e extração dos binários de mídia |
| `domain` | Modelos, contratos e pipeline independente de Android |
| `data` | Room, DAOs, repositórios e importação de vídeos |
| `video-engine` | Execução local de FFmpeg/ffprobe e processamento de clipes |
| `feature-*` | Telas e componentes das áreas de projetos, editor, exportação, IA, configurações e tendências |

## Requisitos

Para compilar localmente, use JDK 17, Android SDK com a plataforma 35 e Gradle Wrapper. O app suporta Android API 26 ou superior. Os binários `ffmpeg` e `ffprobe` necessários para o processamento local permanecem em `app/src/main/assets`.

Crie `local.properties` apontando para o SDK local, por exemplo `sdk.dir=/caminho/para/Android/Sdk`, e não versione esse arquivo. A chave do provedor de IA deve ser configurada dentro do app e nunca adicionada ao código, ao histórico Git ou a arquivos de configuração commitados.

## Validação

Os comandos principais são:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew test lint assembleDebug --stacktrace --no-daemon
```

A suíte cobre seleção de candidatos, pipeline de sucesso/falha/cancelamento, codec de transcript, parsing de `ffprobe` e contratos de persistência de metadata. Os testes são determinísticos e usam doubles, portanto não exigem chave de IA nem acesso de rede.

## Integração contínua

O workflow `.github/workflows/ci.yml` executa em pushes para `main`/`master` e em pull requests. Ele configura JDK 17, Android SDK 35, cache do Gradle, valida o wrapper, executa testes, lint e build debug, e publica relatórios como artefatos mesmo quando uma etapa falha.

A política de CI não executa chamadas ao Grok. Dessa forma, credenciais não são necessárias para a validação e o resultado do workflow não depende de rede externa além do download normal de dependências durante o primeiro build.

## Próximas melhorias

A próxima etapa recomendada é substituir `DemoTranscriptionService` por um adaptador de transcrição real, adicionar testes instrumentados Room com migração `1 → 2`, incluir testes de UI Compose para os principais fluxos e completar a persistência das preferências de exportação e do estilo de legenda. Também vale revisar o manifesto para adotar seletor de documentos e permissões de mídia específicas por versão do Android.
