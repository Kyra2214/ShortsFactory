# Regras específicas do aplicativo (release: minify + shrinkResources).
# Hilt, Room, WorkManager, ML Kit e OkHttp trazem regras próprias via seus artefatos;
# abaixo ficam apenas as regras explícitas do projeto. Validar com o APK release em aparelho.

# Stack traces legíveis a partir do mapping.
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,EnclosingMethod
-renamesourcefileattribute SourceFile

# Modelos persistidos/inspecionados por Room e serialização.
-keep class com.shortsfactory.data.local.entity.** { *; }
-keep class com.shortsfactory.domain.model.** { *; }

# Room: o banco é instanciado por reflexão (<Database>_Impl).
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# kotlinx.serialization: serializers gerados dos @Serializable do projeto.
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.shortsfactory.**$$serializer { *; }
-keepclassmembers class com.shortsfactory.** { *** Companion; }
-keepclasseswithmembers class com.shortsfactory.** { kotlinx.serialization.KSerializer serializer(...); }

# WorkManager + HiltWorkerFactory: workers criados por construtor (Context, WorkerParameters).
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# security-crypto (Tink): protobuf-lite lê campos por reflexão; anotações ausentes são só de compilação.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
