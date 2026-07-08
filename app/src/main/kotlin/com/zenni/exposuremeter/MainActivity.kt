package com.zenni.exposuremeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zenni.exposuremeter.ui.ExposureScreen
import com.zenni.exposuremeter.ui.theme.ExposureMeterTheme

/**
 * Single-activity host (brief §2). All UI is Compose; the activity only sets the
 * theme and content. Metering adapters (sensor, CameraX) are wired into the
 * ViewModel in later phases.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExposureMeterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ExposureScreen()
                }
            }
        }
    }
}
