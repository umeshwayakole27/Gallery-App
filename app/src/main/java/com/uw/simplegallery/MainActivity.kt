package com.uw.simplegallery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat.enableEdgeToEdge
import com.uw.simplegallery.ui.components.RequestPermissions
import com.uw.simplegallery.ui.navigation.GalleryNavGraph
import com.uw.simplegallery.ui.theme.SimpleGalleryTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for the Simple Gallery app.
 *
 * Annotated with [AndroidEntryPoint] to enable Hilt dependency injection
 * for this activity and all composables hosted within it.
 *
 * The base class [androidx.activity.ComponentActivity] is specified in the
 * annotation parameter because the Hilt Gradle plugin is not used
 * (incompatible with AGP 9.x). This tells Hilt which class to extend
 * in the generated [Hilt_MainActivity].
 *
 * Requests storage permissions on launch. If granted, shows the gallery
 * navigation graph. If denied, shows a message explaining that permissions
 * are required.
 */
@AndroidEntryPoint(androidx.activity.ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleGalleryTheme {
                var permissionGranted by rememberSaveable { mutableStateOf(false) }

                RequestPermissions { granted ->
                    permissionGranted = granted
                }

                if (permissionGranted) {
                    GalleryNavGraph()
                } else {
                    // Shown briefly while the permission dialog is up,
                    // or persistently if the user denies permissions.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Storage permission is required to view your photos and videos.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
