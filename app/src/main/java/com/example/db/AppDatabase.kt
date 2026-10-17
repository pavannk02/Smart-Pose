package com.example.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Room Entity ---

@Entity(tableName = "saved_poses")
data class SavedPose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poseName: String,
    val detectedEnvironment: String,
    val detectedOutfit: String,
    val photographerTips: String,
    val wireframeStyle: String,
    val locationName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAO Interface ---

@Dao
interface SavedPoseDao {
    @Query("SELECT * FROM saved_poses ORDER BY timestamp DESC")
    fun getAllSavedPoses(): Flow<List<SavedPose>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPose(savedPose: SavedPose): Long

    @Delete
    suspend fun deletePose(savedPose: SavedPose)

    @Query("DELETE FROM saved_poses WHERE id = :id")
    suspend fun deletePoseById(id: Long)

    @Query("SELECT COUNT(*) FROM saved_poses WHERE poseName = :name")
    suspend fun isPoseSaved(name: String): Int
}

// --- App Database Configuration ---

@Database(entities = [SavedPose::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedPoseDao(): SavedPoseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_pose_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
