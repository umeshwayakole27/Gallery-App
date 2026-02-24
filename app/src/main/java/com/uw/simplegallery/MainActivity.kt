package com.uw.simplegallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.uw.simplegallery.ui.navigation.GalleryNavGraph
import com.uw.simplegallery.ui.theme.SimpleGalleryTheme

// TODO: Add UI tests using ComposeTestRule
// TODO: Implement WorkManager for background thumbnail caching
// TODO: Add localization (strings.xml) for all hardcoded strings
// TODO: Profile with Android Studio profiler and optimize recompositions

/**
 * Main entry point for the Simple Gallery app.
 *
 * Sets up edge-to-edge display, applies the Material3 theme,
 * and hosts the navigation graph.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleGalleryTheme {
                GalleryNavGraph()
            }
        }
    }
}
