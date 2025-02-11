package com.example.offlinegpstracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Location::class], version = 4, exportSchema = false) // ✅ Incremented version
@TypeConverters(Converters::class) // Register TypeConverters
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "location_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4) // ✅ Added new migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Define migration from version 2 to 3
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new column for photo paths as a TEXT type (JSON-encoded)
        db.execSQL("ALTER TABLE location ADD COLUMN photoPaths TEXT DEFAULT '[]'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Create a new temporary table with the correct schema
        db.execSQL("""
            CREATE TABLE location_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL NOT NULL,
                status TEXT NOT NULL DEFAULT 'active',
                photoPaths TEXT NOT NULL DEFAULT '[]'
            )
        """)

        // Step 2: Copy data from the old table to the new one
        db.execSQL("""
            INSERT INTO location_new (id, name, latitude, longitude, altitude, status, photoPaths)
            SELECT id, name, latitude, longitude, altitude, status, 
                CASE 
                    WHEN photoPath IS NOT NULL THEN json_array(photoPath)
                    ELSE '[]' 
                END
            FROM location
        """)

        // Step 3: Drop the old table
        db.execSQL("DROP TABLE location")

        // Step 4: Rename new table to old table name
        db.execSQL("ALTER TABLE location_new RENAME TO location")
    }
}