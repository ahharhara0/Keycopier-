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
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE key_records ADD COLUMN flatWidthsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE key_records ADD COLUMN leftSlopesJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE key_records ADD COLUMN rightSlopesJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE key_records ADD COLUMN tipStyle TEXT NOT NULL DEFAULT 'STANDARD_TAPER'")
                db.execSQL("ALTER TABLE key_records ADD COLUMN tipLengthMm REAL NOT NULL DEFAULT 2.6")
                db.execSQL("ALTER TABLE key_records ADD COLUMN tipOffsetMm REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE key_records ADD COLUMN bowScale REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE key_records ADD COLUMN customContourJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE key_records ADD COLUMN useExactPhotoContour INTEGER NOT NULL DEFAULT 0")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(false)
                .build()
        }
    }
}
