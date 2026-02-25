package com.uw.simplegallery.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Composable that handles runtime permission requests for media access.
 *
 * On API 33+ (Android 13 Tiramisu), requests granular media permissions:
 * - [Manifest.permission.READ_MEDIA_IMAGES]
 * - [Manifest.permission.READ_MEDIA_VIDEO]
 *
 * On older APIs, requests legacy storage permissions:
 * - [Manifest.permission.READ_EXTERNAL_STORAGE]
 * - [Manifest.permission.WRITE_EXTERNAL_STORAGE]
 *
 * Additionally, on API 31+ (Android 12+), requests MANAGE_MEDIA permission
 * via the system settings screen. This allows the app to perform delete/move
 * operations without per-file user prompts, similar to the Tulsi gallery app.
 *
 * If permissions are already granted, [onPermissionResult] is called immediately
 * with `true`. Otherwise the system permission dialog is shown.
 *
 * @param onPermissionResult Callback indicating whether all permissions were granted
 */
@Composable
fun RequestPermissions(
    onPermissionResult: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val permissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    // Launcher for MANAGE_MEDIA settings page (API 31+)
    val manageMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // MANAGE_MEDIA result doesn't matter for blocking — it's optional
        // and just improves UX by skipping per-file prompts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isMediaManager = MediaStore.canManageMedia(context)
            Log.d("RequestPermissions", "MANAGE_MEDIA granted: $isMediaManager")
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        onPermissionResult(allGranted)

        // After granting read permissions, request MANAGE_MEDIA on API 31+
        // This is optional but greatly improves delete/move UX
        if (allGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!MediaStore.canManageMedia(context)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    manageMediaLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.w("RequestPermissions", "Failed to launch MANAGE_MEDIA settings: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val allPermissionsGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!allPermissionsGranted) {
            launcher.launch(permissions)
        } else {
            onPermissionResult(true)

            // Also check MANAGE_MEDIA on API 31+ even if read permissions are already granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!MediaStore.canManageMedia(context)) {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        manageMediaLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.w("RequestPermissions", "Failed to launch MANAGE_MEDIA settings: ${e.message}")
                    }
                }
            }
        }
    }
}
