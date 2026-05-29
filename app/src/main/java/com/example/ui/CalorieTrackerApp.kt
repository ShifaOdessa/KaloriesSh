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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalorieTrackerApp(viewModel: CalorieViewModel) {
    val isSettingsLoaded by viewModel.isSettingsLoaded.collectAsStateWithLifecycle()
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
                if (!isSettingsLoaded) {
                    // Loading DB
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (settings == null || settings.languageCode.isEmpty()) {
                    LanguageSelectionScreen(
                        onLanguageSelected = { lang ->
                            viewModel.saveLanguage(lang)
                        }
                    )
                } else if (!settings.isGoogleLoggedIn) {
                    GoogleLoginScreen(
                        languageCode = settings.languageCode,
                        onSignInSuccess = { email, name ->
                            viewModel.loginWithGoogle(email, name)
                        }
                    )
                } else if (!settings.isOnboarded) {
                    OnboardingScreen(
                        languageCode = settings.languageCode,
                        initialName = settings.name,
                        onCalculate = { name, age, height, weight, activity ->
                            viewModel.saveProfile(name, age, height, weight, activity)
                        }
                    )
                } else {
                    MainTrackerScreen(
                        languageCode = settings.languageCode,
                        userName = settings.name,
                        dailyLimit = settings.dailyLimit,
                        entries = entriesState,
                        isAnalyzing = isAnalyzing,
                        analysisError = analysisError,
                        currentTheme = currentTheme,
                        currentApiKey = settings.geminiApiKey,
                        onSaveApiKey = { key -> viewModel.saveApiKey(key) },
                        onChangeTheme = { newTheme -> viewModel.updateThemeMode(newTheme) },
                        onAnalyzeFood = { bitmap -> viewModel.analyzeAndAddFood(bitmap) },
                        onDeleteEntry = { id -> viewModel.deleteEntry(id) },
                        onReset = { viewModel.resetDay() },
                        onClearError = { viewModel.clearAnalysisError() },
                        onEditProfile = {
                            viewModel.resetOnboarding()
                        },
                        onLogoutGoogle = {
                            viewModel.logoutGoogle()
                        },
                        onSelectLanguage = {
                            viewModel.saveLanguage("")
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
    languageCode: String,
    initialName: String = "",
    onCalculate: (String, Int, Double, Double, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var ageStr by remember { mutableStateOf("") }
    var heightStr by remember { mutableStateOf("") }
    var weightStr by remember { mutableStateOf("") }
    var selectedActivity by remember { mutableStateOf("Сидячая работа") }
    val T = remember(languageCode) { Translations(languageCode) }

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
                    text = T.onboardingTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = T.onboardingDesc,
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
                    label = { Text(T.nameHint) },
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
                    label = { Text(T.ageLabel) },
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
                    label = { Text(T.heightLabel) },
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
                    label = { Text(T.weightLabel) },
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
                    text = T.activityLevelLabel,
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

                val optionTitle = when (option) {
                    "Сидячая работа" -> T.activityLow
                    "Умеренная активность" -> T.activityMedium
                    else -> T.activityHigh
                }
                val optionDesc = when (option) {
                    "Сидячая работа" -> when (languageCode) {
                        "en" -> "Sedentary lifestyle (x1.2)"
                        "uk" -> "Малорухливий спосіб життя (x1.2)"
                        else -> "Малоподвижный образ жизни (x1.2)"
                    }
                    "Умеренная активность" -> when (languageCode) {
                        "en" -> "Walks, light workouts (x1.375)"
                        "uk" -> "Прогулянки, легкі тренування (x1.375)"
                        else -> "Прогулки, легкие тренировки (x1.375)"
                    }
                    else -> when (languageCode) {
                        "en" -> "Regular sports, active work (x1.55)"
                        "uk" -> "Регулярний спорт, активна робота (x1.55)"
                        else -> "Регулярный спорт, тяжелая работа (x1.55)"
                    }
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
                                text = optionTitle,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = optionDesc,
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
                text = T.btnCalculate,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTrackerScreen(
    languageCode: String,
    userName: String,
    dailyLimit: Int,
    entries: List<CalorieEntry>,
    isAnalyzing: Boolean,
    analysisError: String?,
    currentTheme: String,
    currentApiKey: String?,
    onSaveApiKey: (String) -> Unit,
    onChangeTheme: (String) -> Unit,
    onAnalyzeFood: (Bitmap) -> Unit,
    onDeleteEntry: (Int) -> Unit,
    onReset: () -> Unit,
    onClearError: () -> Unit,
    onEditProfile: () -> Unit,
    onLogoutGoogle: () -> Unit,
    onSelectLanguage: () -> Unit
) {
    val totalEaten = entries.sumOf { it.calories }
    val remaining = dailyLimit - totalEaten
    val isOverLimit = remaining < 0
    val T = remember(languageCode) { Translations(languageCode) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            onAnalyzeFood(bitmap)
        }
    }

    var showEditMenu by remember { mutableStateOf(false) }
    var showApiKeyFieldDialog by remember { mutableStateOf(false) }

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
                        text = T.diaryLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${T.welcomeUser}, $userName! 👋",
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
                                        text = T.eaten,
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
                                            text = "/ $dailyLimit ${T.kcal}",
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
                                        text = if (isOverLimit) T.overLabel else T.remaining,
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
                                        text = if (isOverLimit) T.warningTitle else T.tipTitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                }

                                Text(
                                    text = if (isOverLimit) T.tipMsgRed else T.tipMsgGreen,
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
                                    text = T.workoutTitle,
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
                                            text = "$steps ${T.workoutSteps}",
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
                                            text = "$squats ${T.workoutSquats}",
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
                            text = T.eatenLabelHeader,
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
                                Text(T.resetLog)
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
                                    text = T.emptyHistoryHeader,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = T.emptyHistorySub,
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
                    text = T.cameraActionLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // Settings Dropdown Alert Dialog
        if (showEditMenu) {
            AlertDialog(
                onDismissRequest = { showEditMenu = false },
                title = { Text(T.settingsTitle) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = T.settingsSub,
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
                            Text(T.editProfile)
                        }
                        Button(
                            onClick = {
                                showEditMenu = false
                                onSelectLanguage()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(T.changeLang)
                        }
                        Button(
                            onClick = {
                                showEditMenu = false
                                showApiKeyFieldDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(T.geminiApiKeyLabel)
                        }
                        OutlinedButton(
                            onClick = {
                                showEditMenu = false
                                onLogoutGoogle()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(T.logoutBtn)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = T.themeLabel,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themesList = listOf(
                                Triple("LIGHT", T.themeLight, Icons.Default.WbSunny),
                                Triple("DARK", T.themeDark, Icons.Default.NightsStay),
                                Triple("SYSTEM", T.themeSystem, Icons.Default.Settings)
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
                        Text(T.close)
                    }
                }
            )
        }

        if (showApiKeyFieldDialog) {
            var tempKey by remember { mutableStateOf(currentApiKey ?: "") }
            var keyVisible by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showApiKeyFieldDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(T.geminiApiKeyLabel)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = T.geminiApiKeyDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            placeholder = { Text(text = T.geminiApiKeyHint) },
                            singleLine = true,
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val visibilityIcon = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(imageVector = visibilityIcon, contentDescription = "Toggle visibility")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("apiKeyInput"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onSaveApiKey(tempKey)
                            showApiKeyFieldDialog = false
                        }
                    ) {
                        Text(T.save)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyFieldDialog = false }) {
                        Text(T.cancel)
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
                        .size(240.dp)
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
                            text = T.analyzerWorking,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = T.analyzerWorkingSub,
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
                        text = T.recognitionErrorHeader,
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
                        Text(T.okBtn)
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
            contentDescription = "Food dish photo",
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

@Composable
fun GoogleLoginScreen(
    languageCode: String,
    onSignInSuccess: (email: String, name: String) -> Unit
) {
    val context = LocalContext.current
    var showAccountSelector by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    val T = remember(languageCode) { Translations(languageCode) }
    
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null && !account.email.isNullOrBlank()) {
                onSignInSuccess(account.email ?: "", account.displayName ?: "User")
            } else {
                showAccountSelector = true
            }
        } catch (e: Exception) {
            showAccountSelector = true
        }
    }

    LaunchedEffect(Unit) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null && !account.email.isNullOrBlank()) {
                onSignInSuccess(account.email ?: "", account.displayName ?: "User")
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    val isSystemDark = isSystemInDarkTheme()
    val backgroundBrush = if (isSystemDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF130B24),
                Color(0xFF0C0714)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF3EDF7),
                Color(0xFFECE6F0)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fastfood,
                    contentDescription = "Calorie Tracker Logo",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Мій Раціон",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "by Shifa",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = T.welcomeSub,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = T.welcomeDesc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                onClick = {
                    isSigningIn = true
                    try {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestProfile()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    } catch (e: Exception) {
                        showAccountSelector = true
                    }
                },
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSystemDark) Color(0xFF1D1B20) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_login_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GoogleColoredIcon(modifier = Modifier.size(24.dp))
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = T.inviteGoogle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSystemDark) Color.White else Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = T.loginSafety,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }

        if (showAccountSelector) {
            Dialog(onDismissRequest = {
                showAccountSelector = false
                isSigningIn = false
            }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSystemDark) Color(0xFF2B2930) else Color(0xFFF3EDF7)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GoogleColoredIcon(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = T.selectAccount,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = T.selectAccountDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    showAccountSelector = false
                                    isSigningIn = false
                                    onSignInSuccess("shifahome211@gmail.com", "Shifa Home")
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "S",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text(
                                    text = "Shifa Home",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "shifahome211@gmail.com",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    showAccountSelector = false
                                    isSigningIn = false
                                    onSignInSuccess("developer@example.com", "Android Developer")
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "D",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text(
                                    text = "Android Developer",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "developer@example.com",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        TextButton(
                            onClick = {
                                showAccountSelector = false
                                isSigningIn = false
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(T.cancel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleColoredIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE0E0E0), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val w = size.width
            val h = size.height
            
            drawCircle(color = Color(0xFF4285F4), radius = w * 0.4f)
            drawCircle(color = Color.White, radius = w * 0.25f)
            
            val strokeWidth = w * 0.15f
            drawLine(
                color = Color(0xFF4285F4),
                start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f),
                end = androidx.compose.ui.geometry.Offset(w * 0.9f, h * 0.5f),
                strokeWidth = strokeWidth
            )
        }
        Text(
            text = "G",
            fontWeight = FontWeight.Black,
            color = Color(0xFF4285F4),
            fontSize = 12.sp
        )
    }
}

class Translations(val lang: String) {
    val title = "Мій Раціон"
    val subtitle = "by Shifa"

    val inviteGoogle = when(lang) {
        "en" -> "Sign in with Google"
        "uk" -> "Увійти через Google"
        else -> "Войти через Google"
    }
    val welcomeSub = when(lang) {
        "en" -> "Smart Diet & Nutrition Diary"
        "uk" -> "Розумний Раціон & Щоденник Харчування"
        else -> "Умный Рацион & Дневник Питания"
    }
    val welcomeDesc = when(lang) {
        "en" -> "Automatic calorie estimation from photos utilizing Google Gemini AI technology."
        "uk" -> "Автоматичний розрахунок калорійності по фото за допомогою технології штучного інтелекту Google Gemini."
        else -> "Автоматический расчет калорийности по фото с помощью технологии искусственного интеллекта Google Gemini."
    }
    val loginSafety = when(lang) {
        "en" -> "Login is completely secure. We do not store your private passwords."
        "uk" -> "Вхід абсолютно безпечний. Ми не зберігаємо ваші особисті паролі."
        else -> "Вход абсолютно безопасен. Мы не храним ваши личные пароли."
    }
    val selectAccount = when(lang) {
        "en" -> "Select Account"
        "uk" -> "Оберіть акаунт"
        else -> "Выберите аккаунт"
    }
    val selectAccountDesc = when(lang) {
        "en" -> "to continue to \"Мій Раціон\""
        "uk" -> "для переходу в додаток \"Мій Раціон\""
        else -> "для перехода в приложение \"Мій Раціон\""
    }
    val cancel = when(lang) {
        "en" -> "Cancel"
        "uk" -> "Скасувати"
        else -> "Отмена"
    }
    
    // Onboarding
    val selectGender = when(lang) {
        "en" -> "Select Gender"
        "uk" -> "Оберіть стать"
        else -> "Выберите пол"
    }
    val onboardingTitle = when(lang) {
        "en" -> "Tell us about yourself"
        "uk" -> "Розкажіть про себе"
        else -> "Расскажите о себе"
    }
    val onboardingDesc = when(lang) {
        "en" -> "To calculate your daily calorie limit, please enter your details."
        "uk" -> "Щоб розрахувати вашу денну норму калорій, будь ласка, введіть свої дані."
        else -> "Чтобы рассчитать вашу суточную норму калорий, пожалуйста, введите свои данные."
    }
    val nameHint = when(lang) {
        "en" -> "Your Name"
        "uk" -> "Ваше Ім'я"
        else -> "Ваше Имя"
    }
    val nameHintLabel = when(lang) {
        "en" -> "Name"
        "uk" -> "Ім'я"
        else -> "Имя"
    }
    val ageLabel = when(lang) {
        "en" -> "Age (years)"
        "uk" -> "Вік (років)"
        else -> "Возраст (лет)"
    }
    val heightLabel = when(lang) {
        "en" -> "Height (cm)"
        "uk" -> "Зріст (см)"
        else -> "Рост (см)"
    }
    val weightLabel = when(lang) {
        "en" -> "Weight (kg)"
        "uk" -> "Вага (кг)"
        else -> "Вес (кг)"
    }
    val activityLevelLabel = when(lang) {
        "en" -> "Physical Activity"
        "uk" -> "Фізична активність"
        else -> "Физическая активность"
    }
    val selectActivityLabel = when(lang) {
        "en" -> "Select Activity"
        "uk" -> "Оберіть активність"
        else -> "Выбрать активность"
    }
    val activityLow = when(lang) {
        "en" -> "Sedentary Job"
        "uk" -> "Сидяча робота"
        else -> "Сидячая работа"
    }
    val activityMedium = when(lang) {
        "en" -> "Moderate Activity"
        "uk" -> "Помірна активність"
        else -> "Умеренная активность"
    }
    val activityHigh = when(lang) {
        "en" -> "Active / Sports"
        "uk" -> "Середня активність"
        else -> "Средняя активность"
    }
    val btnCalculate = when(lang) {
        "en" -> "Calculate & Start"
        "uk" -> "Розрахувати та Надіслати"
        else -> "Рассчитать и начать"
    }
    val fillAllFieldsError = when(lang) {
        "en" -> "Please correctly fill all fields!"
        "uk" -> "Будь ласка, правильно заповніть усі поля!"
        else -> "Пожалуйста, правильно заполните все поля!"
    }

    // Main Dashboard
    val diaryLabel = when(lang) {
        "en" -> "FOOD DIARY"
        "uk" -> "ЩОДЕННИК ХАРЧУВАННЯ"
        else -> "ДНЕВНИК ПИТАНИЯ"
    }
    val welcomeUser = when(lang) {
        "en" -> "Hello"
        "uk" -> "Привіт"
        else -> "Привет"
    }
    val eaten = when(lang) {
        "en" -> "Eaten today"
        "uk" -> "Сьогодні з'їдено"
        else -> "Сегодня съедено"
    }
    val limitLabel = when(lang) {
        "en" -> "kcal limit"
        "uk" -> "ккал ліміт"
        else -> "ккал лимит"
    }
    val remaining = when(lang) {
        "en" -> "REMAINING"
        "uk" -> "ЗАЛИШИЛОСЬ"
        else -> "ОСТАЛОСЬ"
    }
    val overLabel = when(lang) {
        "en" -> "OVER LIMIT"
        "uk" -> "ПЕРЕБОР"
        else -> "ПЕРЕБОР"
    }
    val kcal = when(lang) {
        "en" -> "kcal"
        "uk" -> "ккал"
        else -> "ккал"
    }
    val overLimit = when(lang) {
        "en" -> "Over limit"
        "uk" -> "Понад ліміт"
        else -> "Превышение"
    }
    val mealHistory = when(lang) {
        "en" -> "Meal History"
        "uk" -> "Історія страв"
        else -> "История блюд"
    }
    val eatenLabelHeader = when(lang) {
        "en" -> "Today's meal entries:"
        "uk" -> "Сьогодні ви з'їли:"
        else -> "Сегодня вы съели:"
    }
    val emptyHistory = when(lang) {
        "en" -> "No meals logged today yet. Use the camera tool to scan your dish!"
        "uk" -> "Сьогодні ще немає доданих страв. Скористайтеся камєрою, щоб проаналізувати страву!"
        else -> "Сегодня еще нет добавленных блюд. Воспользуйтесь камерой для сканирования блюда!"
    }
    val emptyHistorySub = when(lang) {
        "en" -> "Take a food photo to detect it and log to your diary automatically"
        "uk" -> "Зробіть фото їжі, щоб розпізнати її та занести до щоденника"
        else -> "Сделайте фото еды, чтобы распознать ее и занести в дневник"
    }
    val emptyHistoryHeader = when(lang) {
        "en" -> "List is empty"
        "uk" -> "Список порожній"
        else -> "Список пуст"
    }
    val addManual = when(lang) {
        "en" -> "Add Manually"
        "uk" -> "Додати вручну"
        else -> "Добавить вручную"
    }
    val changeLang = when(lang) {
        "en" -> "Change Language"
        "uk" -> "Змінити мову"
        else -> "Сменить язык"
    }
    val editProfile = when(lang) {
        "en" -> "Edit Profile Limits"
        "uk" -> "Редагувати ліміти"
        else -> "Редактировать лимиты"
    }
    val delete = when(lang) {
        "en" -> "Delete"
        "uk" -> "Видати"
        else -> "Удалить"
    }
    val settingsTitle = when(lang) {
        "en" -> "Profile Settings"
        "uk" -> "Налаштування профілю"
        else -> "Настройки профиля"
    }
    val settingsSub = when(lang) {
        "en" -> "Choose an action:"
        "uk" -> "Оберіть дію:"
        else -> "Выберите действие:"
    }
    val themeLabel = when(lang) {
        "en" -> "Theme Mode:"
        "uk" -> "Тема оформлення:"
        else -> "Тема оформления:"
    }
    val themeSystem = when(lang) {
        "en" -> "System"
        "uk" -> "Система"
        else -> "Система"
    }
    val themeLight = when(lang) {
        "en" -> "Light"
        "uk" -> "Світла"
        else -> "Светлая"
    }
    val themeDark = when(lang) {
        "en" -> "Dark"
        "uk" -> "Темна"
        else -> "Темная"
    }
    val logoutBtn = when(lang) {
        "en" -> "Logout Google Account"
        "uk" -> "Вийти з Google-акаунта"
        else -> "Выйти из Google-аккаунта"
    }
    val resetLog = when(lang) {
        "en" -> "Reset"
        "uk" -> "Сброс"
        else -> "Сброс"
    }
    val close = when(lang) {
        "en" -> "Close"
        "uk" -> "Закрити"
        else -> "Закрыть"
    }
    val manualFoodName = when(lang) {
        "en" -> "Food name / dish description"
        "uk" -> "Назва страви / опис"
        else -> "Название блюда / описание"
    }
    val manualCalories = when(lang) {
        "en" -> "Calories (kcal)"
        "uk" -> "Калорійність (ккал)"
        else -> "Калорийность (ккал)"
    }
    val save = when(lang) {
        "en" -> "Save"
        "uk" -> "Зберегти"
        else -> "Сохранить"
    }
    val editMealTitle = when(lang) {
        "en" -> "Edit Meal / Add Meal"
        "uk" -> "Додати страву"
        else -> "Добавление блюда"
    }
    val analyzerWorking = when(lang) {
        "en" -> "Analyzing food photo..."
        "uk" -> "Аналізуємо фото..."
        else -> "Анализируем фото..."
    }
    val analyzerWorkingSub = when(lang) {
        "en" -> "Detecting calorie count of your meal with Google Gemini AI technology"
        "uk" -> "Визначаємо калорійність страви за допомогою Gemini AI"
        else -> "Определяем калорийность блюда с помощью Gemini AI"
    }
    val cameraActionLabel = when(lang) {
        "en" -> "Analyze food by photo"
        "uk" -> "Аналіз їжі по фото"
        else -> "Анализ еды по фото"
    }
    val recognitionErrorHeader = when(lang) {
        "en" -> "Recognition Failed"
        "uk" -> "Помилка розпізнавання"
        else -> "Ошибка распознавания"
    }
    val okBtn = when(lang) {
        "en" -> "Got it"
        "uk" -> "Зрозуміло"
        else -> "Понятно"
    }

    // Tips and squates
    val tipTitle = when(lang) {
        "en" -> "Advice"
        "uk" -> "Порада"
        else -> "Совет"
    }
    val warningTitle = when(lang) {
        "en" -> "Warning"
        "uk" -> "Увага"
        else -> "Внимание"
    }
    val tipMsgGreen = when(lang) {
        "en" -> "You are within limits! To support digestion, drink a glass of water."
        "uk" -> "Ви в межах норми! Щоб закріпити результат, випийте склянку води."
        else -> "Вы в пределах нормы! Чтобы закрепить результат, выпейте стакан воды."
    }
    val tipMsgRed = when(lang) {
        "en" -> "You overconsumed calories. We recommend quick exercises to burn off excesses!"
        "uk" -> "Ви перевищили ліміт калорій. Рекомендується пройти відробку, щоб спалити зайве!"
        else -> "Вы превысили лимит калорий. Рекомендуется пройти отработку, чтобы сжечь лишнее!"
    }
    val workoutTitle = when(lang) {
        "en" -> "WORKOUT BURN"
        "uk" -> "ВІДРОБКА"
        else -> "ОТРАБОТКА"
    }
    val workoutSteps = when(lang) {
        "en" -> "steps"
        "uk" -> "кроків"
        else -> "шагов"
    }
    val workoutSquats = when(lang) {
        "en" -> "squats"
        "uk" -> "прис."
        else -> "прис."
    }

    val geminiApiKeyLabel = when(lang) {
        "en" -> "Gemini API Key"
        "uk" -> "API-ключ Gemini"
        else -> "API-ключ Gemini"
    }
    val geminiApiKeyDesc = when(lang) {
        "en" -> "Enter your custom Google Gemini API key to use the intelligence without local limits. It is stored securely on your device."
        "uk" -> "Введіть власний API-ключ Google Gemini для користування штучним інтелектом без обмежень. Він надійно зберігається на пристрої."
        else -> "Введите собственный API-ключ Google Gemini для использования искусственного интеллекта без лимитов. Он надежно сохраняется на устройстве."
    }
    val geminiApiKeyHint = when(lang) {
        "en" -> "Value starting with AIzaSy..."
        "uk" -> "Значення, що починається з AIzaSy..."
        else -> "Значение, начинающееся с AIzaSy..."
    }
}

@Composable
fun LanguageSelectionScreen(onLanguageSelected: (String) -> Unit) {
    val isSystemDark = isSystemInDarkTheme()
    val backgroundBrush = if (isSystemDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF130B24),
                Color(0xFF0C0714)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF3EDF7),
                Color(0xFFECE6F0)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Language selection",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Мій Раціон",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "by Shifa",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Text(
                text = "Choose your language / Оберіть мову / Выберите язык",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 40.dp)
            )

            // Ukrainian Language button (Primary Option)
            LanguageButton(
                title = "Українська",
                subtitle = "Мій Раціон",
                flagEmoji = "🇺🇦",
                onClick = { onLanguageSelected("uk") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // English Language button
            LanguageButton(
                title = "English",
                subtitle = "My Diet",
                flagEmoji = "🇬🇧",
                onClick = { onLanguageSelected("en") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Russian Language button
            LanguageButton(
                title = "Русский",
                subtitle = "Мой Рацион",
                flagEmoji = "🇷🇺",
                onClick = { onLanguageSelected("ru") }
            )
        }
    }
}

@Composable
fun LanguageButton(
    title: String,
    subtitle: String,
    flagEmoji: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("lang_button_${title.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = flagEmoji,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1.0f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

