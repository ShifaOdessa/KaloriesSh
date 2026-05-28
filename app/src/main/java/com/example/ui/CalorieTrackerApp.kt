package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CalorieEntry
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalorieTrackerApp(viewModel: CalorieViewModel) {
    val settingsState by viewModel.settings.collectAsStateWithLifecycle()
    val entriesState by viewModel.entries.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val analysisError by viewModel.analysisError.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Show API Key Screen / Onboarding Screen / Main Screen depending on the DB state
    val settings = settingsState
    val currentTheme = settings?.themeMode ?: "SYSTEM"

    // Handle snackbar notifications
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearSnackbarMessage()
            }
        }
    }

    MyApplicationTheme(themeMode = currentTheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                if (settings == null) {
                    // Loading DB
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (settings.geminiApiKey.isNullOrBlank()) {
                    ApiKeyScreen(
                        onSave = { key -> viewModel.saveApiKey(key) }
                    )
                } else if (!settings.isOnboarded) {
                    OnboardingScreen(
                        onCalculate = { name, age, height, weight, activity ->
                            viewModel.saveProfile(name, age, height, weight, activity)
                        }
                    )
                } else {
                    MainTrackerScreen(
                        userName = settings.name,
                        dailyLimit = settings.dailyLimit,
                        entries = entriesState,
                        isAnalyzing = isAnalyzing,
                        analysisError = analysisError,
                        currentTheme = currentTheme,
                        onChangeTheme = { newTheme -> viewModel.updateThemeMode(newTheme) },
                        onAnalyzeFood = { bitmap -> viewModel.analyzeAndAddFood(bitmap) },
                        onDeleteEntry = { id -> viewModel.deleteEntry(id) },
                        onReset = { viewModel.resetDay() },
                        onClearError = { viewModel.clearAnalysisError() },
                        onEditProfile = {
                            // Allow user to reset onboarding state to re-onboard
                            viewModel.saveProfile(
                                settings.name,
                                settings.age,
                                settings.height,
                                settings.weight,
                                settings.activityLevel
                            )
                        },
                        onChangeApiKey = {
                            // Resets API key
                            viewModel.saveApiKey("")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ApiKeyScreen(onSave: (String) -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("api_key_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Aesthetic header
        Icon(
            imageVector = Icons.Default.VpnKey,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Введите API-ключ Gemini",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Для работы искусственного интеллекта требуется действующий ключ Gemini от Google AI Studio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Gemini API Key") },
            placeholder = { Text("AIzaSy...") },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(imageVector = icon, contentDescription = "Toggle visibility")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("api_key_input"),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onSave(apiKey) },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("save_key_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "Сохранить и продолжить",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    onCalculate: (String, Int, Double, Double, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ageStr by remember { mutableStateOf("") }
    var heightStr by remember { mutableStateOf("") }
    var weightStr by remember { mutableStateOf("") }
    var selectedActivity by remember { mutableStateOf("Сидячая работа") }

    val activityOptions = listOf("Сидячая работа", "Умеренная активность", "Средняя активность")

    val isFormValid = name.isNotBlank() &&
            ageStr.toIntOrNull() != null && ageStr.toInt() > 0 &&
            heightStr.toDoubleOrNull() != null && heightStr.toDouble() > 0 &&
            weightStr.toDoubleOrNull() != null && weightStr.toDouble() > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("onboarding_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Заполните профиль",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Эти данные необходимы для индивидуального расчета суточной нормы калорий",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ваше имя") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = ageStr,
                    onValueChange = { ageStr = it },
                    label = { Text("Возраст (лет)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("age_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = heightStr,
                    onValueChange = { heightStr = it },
                    label = { Text("Рост (см)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Icon(Icons.Default.Height, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("height_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = weightStr,
                    onValueChange = { weightStr = it },
                    label = { Text("Вес (кг)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Icon(Icons.Default.Scale, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weight_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Text(
                    text = "Уровень ежедневной активности:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(activityOptions) { option ->
                val isSelected = selectedActivity == option
                val cardColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }

                Card(
                    onClick = { selectedActivity = option },
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("activity_option_$option"),
                    shape = RoundedCornerShape(16.dp),
                    border = if (isSelected) BorderStroke(1.5.dp, borderColor) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (option) {
                                "Сидячая работа" -> Icons.Default.DirectionsWalk
                                "Умеренная активность" -> Icons.Default.DirectionsRun
                                else -> Icons.Default.FitnessCenter
                            },
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = option,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (option) {
                                    "Сидячая работа" -> "Малоподвижный образ жизни (x1.2)"
                                    "Умеренная активность" -> "Прогулки, легкие тренировки (x1.375)"
                                    else -> "Регулярный спорт, тяжелая работа (x1.55)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        Button(
            onClick = {
                val age = ageStr.toIntOrNull() ?: 0
                val height = heightStr.toDoubleOrNull() ?: 0.0
                val weight = weightStr.toDoubleOrNull() ?: 0.0
                onCalculate(name, age, height, weight, selectedActivity)
            },
            enabled = isFormValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(56.dp)
                .testTag("calculate_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "Рассчитать и войти",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTrackerScreen(
    userName: String,
    dailyLimit: Int,
    entries: List<CalorieEntry>,
    isAnalyzing: Boolean,
    analysisError: String?,
    currentTheme: String,
    onChangeTheme: (String) -> Unit,
    onAnalyzeFood: (Bitmap) -> Unit,
    onDeleteEntry: (Int) -> Unit,
    onReset: () -> Unit,
    onClearError: () -> Unit,
    onEditProfile: () -> Unit,
    onChangeApiKey: () -> Unit
) {
    val totalEaten = entries.sumOf { it.calories }
    val remaining = dailyLimit - totalEaten
    val isOverLimit = remaining < 0

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            onAnalyzeFood(bitmap)
        }
    }

    var showEditMenu by remember { mutableStateOf(false) }

    val isCurrentlyDark = currentTheme == "DARK" || (currentTheme == "SYSTEM" && isSystemInDarkTheme())
    val bentoBg = if (isCurrentlyDark) Color(0xFF0F0D13) else Color(0xFFFDF8FD)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bentoBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Bento Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ДНЕВНИК ПИТАНИЯ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Привет, $userName! 👋",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentlyDark) Color(0xFF25232A) else Color(0xFFF3EDF7))
                        .clickable { showEditMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bento Main Progress Card
                item {
                    val progressCardBg = if (isCurrentlyDark) Color(0xFF231C30) else Color(0xFFEADDFF)
                    val progressCardBorder = if (isCurrentlyDark) Color(0xFF4C307C) else Color(0xFFD0BCFF)
                    val progressOnCardColor = if (isCurrentlyDark) Color(0xFFF3EDF7) else Color(0xFF21005D)
                    val progressTrackBg = if (isCurrentlyDark) Color(0xFF1B1224) else Color(0xFFF3EDF7)
                    val progressBarColor = if (isCurrentlyDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)

                    val progressRatio = if (dailyLimit > 0) totalEaten.toFloat() / dailyLimit.toFloat() else 0f
                    val progressColor = if (isOverLimit) MaterialTheme.colorScheme.error else progressBarColor

                    Card(
                        modifier = Modifier
                          .fillMaxWidth()
                          .testTag("progress_card")
                          .border(1.dp, progressCardBorder, RoundedCornerShape(28.dp)),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = progressCardBg)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "Сегодня съедено",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = progressOnCardColor
                                        )
                                    )
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = String.format("%,d", totalEaten),
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 32.sp,
                                                color = progressOnCardColor
                                            )
                                        )
                                        Text(
                                            text = "/ $dailyLimit ккал",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                color = progressOnCardColor.copy(alpha = 0.7f)
                                            )
                                        )
                                    }
                                }
                                Column(
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = if (isOverLimit) "ПЕРЕБОР" else "ОСТАЛОСЬ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOverLimit) MaterialTheme.colorScheme.error else progressOnCardColor.copy(alpha = 0.6f)
                                        )
                                    )
                                    Text(
                                        text = if (isOverLimit) "${-remaining}" else "$remaining",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = if (isOverLimit) MaterialTheme.colorScheme.error else progressOnCardColor
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            LinearProgressIndicator(
                                progress = progressRatio.coerceIn(0f, 1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .clip(CircleShape),
                                color = progressColor,
                                trackColor = progressTrackBg
                            )
                        }
                    }
                }

                // Bento Row containing Tip Card & Activity Card
                item {
                    val borderOfCard = if (isCurrentlyDark) Color(0xFF38363D) else Color(0xFFCAC4D0)
                    val outlineCardBg = if (isCurrentlyDark) Color(0xFF1D1B22) else Color(0xFFFFFBFE)
                    val activityCardBg = if (isCurrentlyDark) Color(0xFF221F2A) else Color(0xFFF3EDF7)
                    val activityCardBorder = if (isCurrentlyDark) Color(0xFF312F38) else Color(0xFFE7E0EC)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tip Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(140.dp)
                                .testTag("tip_card")
                                .border(1.dp, borderOfCard, RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = outlineCardBg)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("💡", fontSize = 16.sp)
                                    Text(
                                        text = if (isOverLimit) "Внимание" else "Совет",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                }

                                Text(
                                    text = if (isOverLimit) {
                                        "Вы превысили лимит калорий. Рекомендуется пройти отработку, чтобы сжечь лишнее!"
                                    } else {
                                        "Вы в пределах нормы! Чтобы закрепить результат, выпейте стакан воды."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        // Activity/Correction Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(140.dp)
                                .testTag("activity_card")
                                .border(1.dp, activityCardBorder, RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = activityCardBg)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ОТРАБОТКА",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val overBy = if (isOverLimit) -remaining else 0
                                    val steps = overBy * 15
                                    val squats = overBy * 2

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("🏃", fontSize = 18.sp)
                                        Text(
                                            text = "$steps шагов",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("🏋️", fontSize = 18.sp)
                                        Text(
                                            text = "$squats прис.",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bento List Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Сегодня вы съели:",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        if (entries.isNotEmpty()) {
                            TextButton(
                                onClick = onReset,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("reset_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сброс")
                            }
                        }
                    }
                }

                if (entries.isEmpty()) {
                    item {
                        val borderCardEmpty = if (isCurrentlyDark) Color(0xFF38363D) else Color(0xFFCAC4D0)
                        val emptyCardBg = if (isCurrentlyDark) Color(0xFF1D1B22) else Color(0xFFFFFBFE)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, borderCardEmpty, RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = emptyCardBg)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fastfood,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Список пуст",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Сделайте фото еды, чтобы распознать ее и занести в дневник",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(entries, key = { it.id }) { entry ->
                        FoodEntryCard(
                            entry = entry,
                            onDelete = { onDeleteEntry(entry.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }

        // Primary Bento action pill button
        Button(
            onClick = { cameraLauncher.launch(null) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .height(56.dp)
                .width(280.dp)
                .testTag("analyze_food_button"),
            shape = RoundedCornerShape(20.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Анализ еды по фото",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // Settings Dropdown Alert Dialog
        if (showEditMenu) {
            AlertDialog(
                onDismissRequest = { showEditMenu = false },
                title = { Text("Настройки профиля") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Выберите действие:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                showEditMenu = false
                                onEditProfile()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Редактировать лимиты")
                        }
                        OutlinedButton(
                            onClick = {
                                showEditMenu = false
                                onChangeApiKey()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сбросить API-ключ")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Тема оформления:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themesList = listOf(
                                Triple("LIGHT", "Светлая", Icons.Default.WbSunny),
                                Triple("DARK", "Темная", Icons.Default.NightsStay),
                                Triple("SYSTEM", "Система", Icons.Default.Settings)
                            )
                            themesList.forEach { (mode, label, icon) ->
                                val isSelected = currentTheme == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("theme_button_$mode")
                                ) {
                                    if (isSelected) {
                                        FilledTonalButton(
                                            onClick = { onChangeTheme(mode) },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(label, fontSize = 12.sp)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onChangeTheme(mode) },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEditMenu = false }) {
                        Text("Закрыть")
                    }
                }
            )
        }

        // Analysis Loading Indicator overlay
        if (isAnalyzing) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .size(220.dp)
                        .testTag("analyzing_dialog")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Анализируем фото...",
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Определяем калорийность блюда с помощью Gemini AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Analysis error dialog with try again
        if (analysisError != null) {
            AlertDialog(
                onDismissRequest = onClearError,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                },
                title = {
                    Text(
                        "Ошибка распознавания",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = analysisError,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onClearError,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Понятно")
                    }
                }
            )
        }
    }
}

@Composable
fun FoodEntryCard(
    entry: CalorieEntry,
    onDelete: () -> Unit
) {
    // Custom Bento item background tint (#f7f2fa style in light, dark tinted overlay in dark)
    val isCurrentlyDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.2f
    val itemBgColor = if (isCurrentlyDark) Color(0xFF1E1C24) else Color(0xFFF7F2FA)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("food_entry_${entry.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = itemBgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Food photo thumbnail with border overlay
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                if (!entry.photoBase64.isNullOrBlank()) {
                    Base64Image(
                        base64Str = entry.photoBase64,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fastfood,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.foodName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "+${entry.calories} ккал",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_entry_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun Base64Image(base64Str: String, modifier: Modifier = Modifier) {
    val imageBitmap = remember(base64Str) {
        try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Изображение еды",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Fastfood,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
