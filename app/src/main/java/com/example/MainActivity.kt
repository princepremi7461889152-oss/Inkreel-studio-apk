package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.GeminiInkReelService
import com.example.audio.DeviceTtsSynthesizer
import com.example.data.ReelRepository
import com.example.ui.InkReelStudioScreen
import com.example.ui.theme.InkReelStudioTheme
import com.example.viewmodel.InkReelViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            InkReelStudioTheme {
                val context = LocalContext.current
                val viewModel: InkReelViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val appCtx = context.applicationContext
                            val repository = ReelRepository(appCtx)
                            val geminiService = GeminiInkReelService()
                            val deviceTts = DeviceTtsSynthesizer(appCtx)
                            InkReelViewModel(
                                repository = repository,
                                geminiService = geminiService,
                                deviceTts = deviceTts
                            )
                        }
                    }
                )
                InkReelStudioScreen(viewModel = viewModel)
            }
        }
    }
}
