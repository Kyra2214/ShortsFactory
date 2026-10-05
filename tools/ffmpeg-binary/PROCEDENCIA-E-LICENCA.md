# Binários FFmpeg — procedência, versão, licença e checksum

| Item | Valor |
|------|-------|
| Arquivos | `app/src/main/jniLibs/arm64-v8a/libffmpeg.so`, `libffprobe.so` |
| Versão (string embutida) | FFmpeg 8.0.1 (libavformat 62.3.100) |
| Plataforma | Android, aarch64 (armv8-a), executável PIE estático, `--enable-small` |
| Licença declarada pelas libs | LGPL 2.1 ou posterior (`--disable-gpl --disable-nonfree`; sem x264, sem libs externas habilitadas) |
| Checksums | `tools/ffmpeg-binary/SHA256SUMS` (verificar com `sha256sum -c`; roda no CI) |
| Build | Compilação cruzada com NDK 28.2 (r28) em máquina de terceiro. A `configuration` está em `CODECS-E-CONFIGURACAO.md` |

## Procedência
- **Origem de distribuição desconhecida.** Os binários chegaram ao repositório sem registro de quem os compilou, de qual tag/commit do FFmpeg e de onde o código-fonte e os scripts de build estão publicados. Nenhum dado desse tipo existe no repositório ou nos binários além da string de `configuration`.
- Os binários ainda contêm, internamente, caminhos da máquina de build original (5 ocorrências em `libffmpeg.so`, na `configuration` e nos `--cc/--sysroot`). Isso só some com recompilação; não foi alterado para preservar o checksum e o comportamento.

## Obrigações LGPL (resumo, não é aconselhamento jurídico)
- Distribuir o app com estes binários exige: aviso de licença LGPL incluído (ver `THIRD_PARTY_NOTICES.md`), acesso ao código-fonte correspondente da versão usada e à configuração de build, e permitir que o usuário substitua a biblioteca. Executar `libffmpeg.so` como processo separado e manter o arquivo substituível ajuda, mas a oferta de código-fonte só é possível se a origem do build for conhecida.
- **Pendência bloqueante para release (Fase 10 / release):** recompilar a partir de um tag FFmpeg conhecido com script versionado neste repositório (ou documentar a fonte exata do binário atual), e então atualizar este arquivo e `SHA256SUMS`. A recompilação também resolverá `drawtext`/libass para a Fase 2.

## Como atualizar o binário
1. Substituir os dois `.so`.
2. Regenerar: `sha256sum app/src/main/jniLibs/arm64-v8a/*.so > tools/ffmpeg-binary/SHA256SUMS`.
3. Atualizar versão, `configuration` e procedência nesta pasta.
