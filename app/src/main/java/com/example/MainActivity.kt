package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.CalorieRepository
import com.example.ui.CalorieTrackerApp
import com.example.ui.CalorieViewModel
import com.example.ui.CalorieViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val database = AppDatabase.getDatabase(this)
    val repository = CalorieRepository(database)
    val viewModel = ViewModelProvider(this, CalorieViewModelFactory(repository))[CalorieViewModel::class.java]
    
    enableEdgeToEdge()
    setContent {
      CalorieTrackerApp(viewModel)
    }
  }
}
