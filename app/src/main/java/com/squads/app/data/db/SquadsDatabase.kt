package com.squads.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatConversationEntity::class,
        ChatMessageEntity::class,
        MailMessageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SquadsDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    abstract fun mailDao(): MailDao
}
