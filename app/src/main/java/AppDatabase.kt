package com.example.offlinegpstracker

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Database(entities = [Location::class, Route::class, RoutePoint::class], version = 7, exportSchema = false) // Updated to version 6
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun routeDao(): RouteDao
    abstract fun routePointDao(): RoutePointDao

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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7) // Added MIGRATION_5_6
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE location ADD COLUMN photoPaths TEXT DEFAULT '[]'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
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
        db.execSQL("""
            INSERT INTO location_new (id, name, latitude, longitude, altitude, status, photoPaths)
            SELECT id, name, latitude, longitude, altitude, status, 
                CASE WHEN photoPath IS NOT NULL THEN json_array(photoPath) ELSE '[]' END
            FROM location
        """)
        db.execSQL("DROP TABLE location")
        db.execSQL("ALTER TABLE location_new RENAME TO location")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE Route (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                snapshotPath TEXT,
                centerLat REAL NOT NULL,
                centerLon REAL NOT NULL,
                zoom INTEGER NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE RoutePoint (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                routeId INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (routeId) REFERENCES Route(id) ON DELETE CASCADE
            )
        """)
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX index_RoutePoint_routeId ON RoutePoint(routeId)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Route ADD COLUMN averageSpeed REAL")
    }
}

@Entity
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val snapshotPath: String?,
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Int,
    val width: Int,
    val height: Int,
    val averageSpeed: Double? = null
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Route::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routeId"])]
)
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routeId: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

@Dao
interface RouteDao {
    @Insert
    suspend fun insert(route: Route): Long

    @Query("SELECT * FROM route WHERE id = :routeId")
    suspend fun getRoute(routeId: Int): Route?

    @Query("UPDATE route SET endTime = :endTime WHERE id = :routeId")
    suspend fun updateRouteEndTime(routeId: Int, endTime: Long)

    @Query("UPDATE route SET endTime = :endTime, snapshotPath = :snapshotPath WHERE id = :routeId")
    suspend fun updateRouteEndTimeAndSnapshot(routeId: Int, endTime: Long, snapshotPath: String?)

    @Query("SELECT * FROM route ORDER BY startTime DESC")
    fun getAllRoutes(): Flow<List<Route>> // Added for saved routes

    @Query("UPDATE route SET endTime = :endTime, snapshotPath = :snapshotPath, averageSpeed = :averageSpeed WHERE id = :routeId")
    suspend fun updateRouteWithSpeed(routeId: Int, endTime: Long, snapshotPath: String?, averageSpeed: Double)
}

@Dao
interface RoutePointDao {
    @Insert
    suspend fun insert(point: RoutePoint)

    @Query("SELECT * FROM routepoint WHERE routeId = :routeId ORDER BY timestamp")
    fun getPointsForRoute(routeId: Int): Flow<List<RoutePoint>>
}