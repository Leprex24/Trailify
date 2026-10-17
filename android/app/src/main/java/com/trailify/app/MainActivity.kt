package com.trailify.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trailify.app.navigation.TrailifyNavHost
import com.trailify.core.ui.TrailifyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrailifyTheme {
                TrailifyNavHost()
            }
        }
    }
}
