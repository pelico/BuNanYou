package com.aicompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aicompose.feature.camera.SnapGuideScreen
import com.aicompose.ui.theme.AIComposeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIComposeTheme {
                SnapGuideScreen()
            }
        }
    }
}
