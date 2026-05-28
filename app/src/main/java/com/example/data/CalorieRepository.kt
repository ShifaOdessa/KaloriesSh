package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class CalorieRepository(private val db: AppDatabase) {

    val settings: Flow<AppSettings?> = db.appSettingsDao().getSettings()
    val entries: Flow<List<CalorieEntry>> = db.calorieEntryDao().getAllEntries()

    suspend fun saveApiKey(apiKey: String) {
        val current = db.appSettingsDao().getSettingsDirect() ?: AppSettings()
        db.appSettingsDao().insertSettings(
            current.copy(geminiApiKey = apiKey)
        )
    }

    suspend fun saveThemeMode(themeMode: String) {
        val current = db.appSettingsDao().getSettingsDirect() ?: AppSettings()
        db.appSettingsDao().insertSettings(
            current.copy(themeMode = themeMode)
        )
    }

    suspend fun saveProfile(
        name: String,
        age: Int,
        height: Double,
        weight: Double,
        activityLevel: String,
        bmr: Int,
        dailyLimit: Int
    ) {
        val current = db.appSettingsDao().getSettingsDirect() ?: AppSettings()
        db.appSettingsDao().insertSettings(
            current.copy(
                name = name,
                age = age,
                height = height,
                weight = weight,
                activityLevel = activityLevel,
                bmr = bmr,
                dailyLimit = dailyLimit,
                isOnboarded = true
            )
        )
    }

    suspend fun addEntry(foodName: String, calories: Int, photoBase64: String? = null) {
        db.calorieEntryDao().insertEntry(
            CalorieEntry(
                foodName = foodName,
                calories = calories,
                photoBase64 = photoBase64
            )
        )
    }

    suspend fun resetDay() {
        db.calorieEntryDao().deleteAllEntries()
    }

    suspend fun deleteEntry(id: Int) {
        db.calorieEntryDao().deleteEntryById(id)
    }
}
