Os arquivos <versão>.json do Room são gerados no primeiro build (`./gradlew :data:kspDebugKotlin`) e DEVEM ser versionados.
Nunca edite à mão. O JSON da versão 3 não existe (exportSchema era false); a migração 3 → 4 é validada abrindo um banco v3 real no teste instrumentado.
