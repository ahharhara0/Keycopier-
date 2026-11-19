package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [KeyRecordEntity::class, KeyAttachmentEntity::class],
    version = 2,
    exportSchema = false
)
abstract class KeyCopierDatabase : RoomDatabase() {
    abstract fun keyCopierDao(): KeyCopierDao

    companion object {
        private const val DB_NAME = "keycopier_vault_encrypted.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE key_records ADD COLUMN isDoubleSided INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context, passphrase: ByteArray): KeyCopierDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                KeyCopierDatabase::class.java,
                DB_NAME
            )
            // Load native SQLCipher library and bind SupportOpenHelperFactory on Android device
            runCatching {
                System.loadLibrary("sqlcipher")
                val factory = SupportOpenHelperFactory(passphrase)
                builder.openHelperFactory(factory)
            }
            return builder
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(false)
                .build()
        }
    }
}
