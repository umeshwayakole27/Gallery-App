package com.uw.simplegallery.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        onPermissionResult(allGranted)
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
        }
    }
}
