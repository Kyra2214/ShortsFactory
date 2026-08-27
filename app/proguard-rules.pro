# Regras específicas do aplicativo.
# Hilt, Room e kotlinx.serialization fornecem as regras necessárias via seus artefatos.

# Os modelos persistidos podem ser inspecionados por Room/serialização em builds reduzidos.
-keep class com.shortsfactory.data.local.entity.** { *; }
-keep class com.shortsfactory.domain.model.** { *; }
