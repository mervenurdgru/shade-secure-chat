package com.shade.app.di

import android.content.Context
import androidx.room.Room
import com.shade.app.data.local.MIGRATION_4_5
import com.shade.app.data.local.MIGRATION_5_6
import com.shade.app.data.local.MIGRATION_6_7
import com.shade.app.data.local.MIGRATION_7_8
import com.shade.app.data.local.MIGRATION_8_9
import com.shade.app.data.local.MIGRATION_9_10
import com.shade.app.data.local.ShadeDatabase
import com.shade.app.data.local.dao.ChatDao
import com.shade.app.data.local.dao.ContactDao
import com.shade.app.data.local.dao.GroupDao
import com.shade.app.data.local.dao.GroupReadReceiptDao
import com.shade.app.data.local.dao.MessageDao
import com.shade.app.data.local.dao.SenderKeyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ShadeDatabase {
        return Room.databaseBuilder(
                context,
                ShadeDatabase::class.java,
                "shade_database"
            ).addMigrations(
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChatDao(db: ShadeDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: ShadeDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideContactDao(db: ShadeDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideGroupDao(db: ShadeDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideSenderKeyDao(db: ShadeDatabase): SenderKeyDao = db.senderKeyDao()

    @Provides
    fun provideGroupReadReceiptDao(db: ShadeDatabase): GroupReadReceiptDao = db.groupReadReceiptDao()
}
