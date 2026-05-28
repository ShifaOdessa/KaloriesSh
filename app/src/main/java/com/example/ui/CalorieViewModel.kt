package com.example.ui

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettings
import com.example.data.CalorieEntry
import com.example.data.CalorieRepository
import com.example.network.FoodAnalysisResult
import com.example.network.GenerateContentRequest
import com.example.network.Content
import com.example.network.Part
import com.example.network.InlineData
import com.example.network.GenerationConfig
import com.example.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class CalorieViewModel(private val repository: CalorieRepository) : ViewModel() {

    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val entries: StateFlow<List<CalorieEntry>> = repository.entries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearAnalysisError() {
        _analysisError.value = null
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            repository.saveApiKey(key.trim())
            _snackbarMessage.value = "API-ключ сохранен!"
        }
    }

    fun updateThemeMode(themeMode: String) {
        viewModelScope.launch {
            repository.saveThemeMode(themeMode)
            _snackbarMessage.value = "Тема изменена!"
        }
    }

    fun saveProfile(
        name: String,
        age: Int,
        height: Double,
        weight: Double,
        activityLevel: String
    ) {
        viewModelScope.launch {
            val (bmr, limit) = calculateBmrAndLimit(weight, height, age, activityLevel)
            repository.saveProfile(
                name = name.trim(),
                age = age,
                height = height,
                weight = weight,
                activityLevel = activityLevel,
                bmr = bmr,
                dailyLimit = limit
            )
            _snackbarMessage.value = "Профиль успешно создан!"
        }
    }

    fun deleteEntry(id: Int) {
        viewModelScope.launch {
            repository.deleteEntry(id)
            _snackbarMessage.value = "Запись удалена"
        }
    }

    fun resetDay() {
        viewModelScope.launch {
            repository.resetDay()
            _snackbarMessage.value = "Дневник очищен!"
        }
    }

    private fun calculateBmrAndLimit(
        weight: Double,
        height: Double,
        age: Int,
        activityLevel: String
    ): Pair<Int, Int> {
        val bmr = (10.0 * weight + 6.25 * height - 5.0 * age + 5.0).toInt()
        val coef = when (activityLevel) {
            "Сидячая работа" -> 1.2
            "Умеренная активность" -> 1.375
            "Средняя активность" -> 1.55
            else -> 1.2
        }
        val target = (bmr * coef * 0.85).toInt()
        val limit = maxOf(bmr, target)
        return Pair(bmr, limit)
    }

    fun analyzeAndAddFood(bitmap: Bitmap) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null

            val currentSettings = settings.value
            val apiKey = currentSettings?.geminiApiKey

            if (apiKey.isNullOrBlank()) {
                _analysisError.value = "API-ключ отсутствует. Перейдите в настройки и введите ключ."
                _isAnalyzing.value = false
                return@launch
            }

            try {
                // Compress bitmap to base64
                val base64Image = compressBitmapToBase64(bitmap)

                // Build Gemini query
                val prompt = "Ты - эксперт по питанию. Проанализируй фото еды и верни ответ СТРОГО в формате JSON без какого-либо дополнительного текста. Формат JSON: { \"food_name\": \"Определенное блюдо\", \"calories\": примерная_калорийность_в_ккал }. Если определить не удалось, верни error."

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(
                                    inlineData = InlineData(
                                        mimeType = "image/jpeg",
                                        data = base64Image
                                    )
                                )
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.2
                    )
                )

                val response = RetrofitClient.service.analyzeImage(apiKey, request)
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (rawText.isNullOrBlank()) {
                    _analysisError.value = "Не удалось распознать еду. Попробуйте сделать более четкое фото."
                } else {
                    val cleanText = cleanJsonString(rawText)
                    val adapter = RetrofitClient.moshiInstance.adapter(FoodAnalysisResult::class.java)
                    val parsed = try {
                        adapter.fromJson(cleanText)
                    } catch (e: Exception) {
                        null
                    }

                    if (parsed == null || !parsed.error.isNullOrBlank() || parsed.foodName.isNullOrBlank() || parsed.calories == null) {
                        _analysisError.value = "Не удалось распознать еду. Попробуйте сделать более четкое фото."
                    } else {
                        // Success! Save the base64 thumbnail to show in history list
                        val compressedThumb = compressBitmapToThumbBase64(bitmap)
                        repository.addEntry(
                            foodName = parsed.foodName,
                            calories = parsed.calories,
                            photoBase64 = compressedThumb
                        )
                        _snackbarMessage.value = "Добавлено: ${parsed.foodName} (+${parsed.calories} ккал)"
                    }
                }
            } catch (e: Exception) {
                Log.e("CalorieViewModel", "Gemini analysis failed", e)
                _analysisError.value = "Ошибка при запросе к AI. Проверьте подключение к сети и валидность API-ключа."
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun compressBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Resize first to avoid massive payloads while maintaining readability
        val resized = if (bitmap.width > 1024 || bitmap.height > 1024) {
            val aspectRatio = bitmap.width.toDouble() / bitmap.height.toDouble()
            val newWidth = if (aspectRatio > 1) 1024 else (1024 * aspectRatio).toInt()
            val newHeight = if (aspectRatio > 1) (1024 / aspectRatio).toInt() else 1024
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        resized.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun compressBitmapToThumbBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Thumbnail size for UI display
        val thumb = Bitmap.createScaledBitmap(bitmap, 120, 120, true)
        thumb.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun cleanJsonString(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```json")) {
            text = text.substring(7)
        } else if (text.startsWith("```")) {
            text = text.substring(3)
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length - 3)
        }
        return text.trim()
    }
}

class CalorieViewModelFactory(private val repository: CalorieRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalorieViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalorieViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
