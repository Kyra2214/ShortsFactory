package com.shortsfactory.data.di

import android.content.Context
import androidx.room.Room
import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.domain.pipeline.VideoEngine
import com.shortsfactory.video.FfmpegVideoEngine
import com.shortsfactory.data.local.dao.*
import com.shortsfactory.data.local.db.ShortsDatabase
import com.shortsfactory.data.repository.*
import com.shortsfactory.data.transcription.OpenAiTranscriptionService
import com.shortsfactory.domain.pipeline.TranscriptionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun providePlainContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShortsDatabase =
        Room.databaseBuilder(context, ShortsDatabase::class.java, "shorts_factory.db")
            .addMigrations(
                ShortsDatabase.MIGRATION_1_2,
                ShortsDatabase.MIGRATION_2_3,
                ShortsDatabase.MIGRATION_3_4,
                ShortsDatabase.MIGRATION_4_5,
                ShortsDatabase.MIGRATION_5_6
            )
            .build()

    @Provides fun projectDao(db: ShortsDatabase): ProjectDao = db.projectDao()
    @Provides fun shortDao(db: ShortsDatabase): ShortDao = db.shortDao()
    @Provides fun transcriptDao(db: ShortsDatabase): TranscriptDao = db.transcriptDao()
    @Provides fun aiAnalysisDao(db: ShortsDatabase): AIAnalysisDao = db.aiAnalysisDao()
    @Provides fun subtitleDao(db: ShortsDatabase): SubtitleDao = db.subtitleDao()
    @Provides fun exportDao(db: ShortsDatabase): ExportDao = db.exportDao()
    @Provides fun exportBatchDao(db: ShortsDatabase): ExportBatchDao = db.exportBatchDao()
    @Provides fun shortPlatformMetadataDao(db: ShortsDatabase): ShortPlatformMetadataDao = db.shortPlatformMetadataDao()

    @Provides @Singleton fun projectRepository(dao: ProjectDao) = ProjectRepository(dao)
    @Provides @Singleton fun shortRepository(dao: ShortDao) = ShortRepository(dao)
    @Provides @Singleton fun transcriptRepository(dao: TranscriptDao) = TranscriptRepository(dao)
    @Provides @Singleton fun aiAnalysisRepository(dao: AIAnalysisDao) = AIAnalysisRepository(dao)
    @Provides @Singleton fun subtitleRepository(dao: SubtitleDao) = SubtitleRepository(dao)
    @Provides @Singleton fun exportRepository(dao: ExportDao) = ExportRepository(dao)
    @Provides @Singleton fun exportBatchRepository(dao: ExportBatchDao) = ExportBatchRepository(dao)
    @Provides @Singleton fun platformMetadataRepository(dao: ShortPlatformMetadataDao) = PlatformMetadataRepository(dao)

    @Provides @Singleton fun projectStore(db: ShortsDatabase) = ProjectStore(db)

    @Provides @Singleton
    fun videoImporter(@ApplicationContext context: Context) = VideoImporter(context)

    @Provides @Singleton
    fun secureKeyStore(@ApplicationContext context: Context) = SecureKeyStore(context)

    @Provides @Singleton
    fun videoEngine(@ApplicationContext context: Context): VideoEngine = FfmpegVideoEngine(context)

    @Provides @Singleton
    fun transcriptionService(
        secureKeyStore: SecureKeyStore,
        videoEngine: VideoEngine
    ): TranscriptionService = OpenAiTranscriptionService(secureKeyStore, videoEngine)
}
