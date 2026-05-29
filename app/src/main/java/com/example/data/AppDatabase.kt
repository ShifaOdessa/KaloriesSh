package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val geminiApiKey: String? = null,
    val name: String = "",
    val age: Int = 0,
    val height: Double = 0.0,
    val weight: Double = 0.0,
    val activityLevel: String = "Сидячая работа",
    val bmr: Int = 0,
    val dailyLimit: Int = 0,
    val isOnboarded: Boolean = false,
    val themeMode: String = "SYSTEM",
    val isGoogleLoggedIn: Boolean = false,
    val googleEmail: String? = null,
    val googleProfilePic: String? = null,
    val languageCode: String = ""
)

@Entity(tableName = "calorie_entries")
data class CalorieEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val foodName: String,
    val calories: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val photoBase64: String? = null
)

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings)
}

@Dao
interface CalorieEntryDao {
    @Query("SELECT * FROM calorie_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<CalorieEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CalorieEntry)

    @Query("DELETE FROM calorie_entries")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM calorie_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Int)
}

@Database(entities = [AppSettings::class, CalorieEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun calorieEntryDao(): CalorieEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calorie_tracker_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
