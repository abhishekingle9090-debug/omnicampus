package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.OmniCampusTheme
import com.example.viewmodel.OmniCampusViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: OmniCampusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        try {
            configureAppCheck(this, intent)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "AppCheck configuration bypassed: ${e.message}")
        }

        // STEP 2: Enable Edge-to-Edge full bleed layout
        enableEdgeToEdge()

        // STEP 3: Initialize UI
        setContent {
            OmniCampusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
