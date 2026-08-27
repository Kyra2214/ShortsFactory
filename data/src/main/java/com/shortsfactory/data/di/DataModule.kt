package com.shortsfactory.data.di

import android.content.Context
import androidx.room.Room
import com.shortsfactory.core.SecureKeyStore
import com.shortsfactory.domain.pipeline.VideoEngine
import com.shortsfactory.video.FfmpegVideoEngine
import com.shortsfactory.data.local.dao.*
import com.shortsfactory.data.local.db.ShortsDatabase
import com.shortsfactory.data.repository.*
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
            .addMigrations(ShortsDatabase.MIGRATION_1_2)
            .build()

    @Provides fun projectDao(db: ShortsDatabase): ProjectDao = db.projectDao()
    @Provides fun shortDao(db: ShortsDatabase): ShortDao = db.shortDao()
    @Provides fun transcriptDao(db: ShortsDatabase): TranscriptDao = db.transcriptDao()
    @Provides fun aiAnalysisDao(db: ShortsDatabase): AIAnalysisDao = db.aiAnalysisDao()
    @Provides fun subtitleDao(db: ShortsDatabase): SubtitleDao = db.subtitleDao()
    @Provides fun exportDao(db: ShortsDatabase): ExportDao = db.exportDao()

    @Provides @Singleton fun projectRepository(dao: ProjectDao) = ProjectRepository(dao)
    @Provides @Singleton fun shortRepository(dao: ShortDao) = ShortRepository(dao)
    @Provides @Singleton fun transcriptRepository(dao: TranscriptDao) = TranscriptRepository(dao)
    @Provides @Singleton fun aiAnalysisRepository(dao: AIAnalysisDao) = AIAnalysisRepository(dao)
    @Provides @Singleton fun subtitleRepository(dao: SubtitleDao) = SubtitleRepository(dao)
    @Provides @Singleton fun exportRepository(dao: ExportDao) = ExportRepository(dao)

    @Provides @Singleton
    fun videoImporter(@ApplicationContext context: Context) = VideoImporter(context)

    @Provides @Singleton
    fun secureKeyStore(@ApplicationContext context: Context) = SecureKeyStore(context)

    @Provides @Singleton
    fun videoEngine(@ApplicationContext context: Context): VideoEngine = FfmpegVideoEngine(context)
}
