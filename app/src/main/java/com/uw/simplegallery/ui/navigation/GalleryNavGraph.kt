package com.uw.simplegallery.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uw.simplegallery.ui.components.FloatingBottomNavBar
import com.uw.simplegallery.ui.components.FloatingNavTab
import com.uw.simplegallery.ui.components.floatingNavBarTotalHeight
import com.uw.simplegallery.ui.screens.albums.AlbumsScreen
import com.uw.simplegallery.ui.screens.detail.ImageDetailScreen
import com.uw.simplegallery.ui.screens.gallery.GalleryGridScreen
import com.uw.simplegallery.viewmodel.GalleryViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Navigation routes used throughout the app.
 */
object GalleryRoutes {
    const val GALLERY_GRID = "gallery_grid"
    const val IMAGE_DETAIL = "image_detail/{imageId}"
    const val ALBUMS = "albums"
    const val ALBUM_DETAIL = "album_detail/{albumId}"

    /** Builds the image detail route for a specific image ID. */
    fun imageDetail(imageId: Long) = "image_detail/$imageId"

    /** Builds the album detail route for a specific album ID (URL-encoded). */
    fun albumDetail(albumId: String): String {
        val encoded = URLEncoder.encode(albumId, StandardCharsets.UTF_8.toString())
        return "album_detail/$encoded"
    }
}

/**
 * Bottom navigation tab definitions.
 *
 * @param route The navigation route for this tab
 * @param label The display label for this tab
 * @param selectedIcon The icon shown when the tab is active (filled)
 * @param unselectedIcon The icon shown when the tab is inactive (outlined)
 */
sealed class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Gallery : BottomNavTab(
        route = GalleryRoutes.GALLERY_GRID,
        label = "Gallery",
        selectedIcon = Icons.Filled.Photo,
        unselectedIcon = Icons.Outlined.Photo
    )

    data object Albums : BottomNavTab(
        route = GalleryRoutes.ALBUMS,
        label = "Albums",
        selectedIcon = Icons.Filled.PhotoAlbum,
        unselectedIcon = Icons.Outlined.PhotoAlbum
    )
}

/** All bottom navigation tabs as [FloatingNavTab] instances for the floating bar. */
private val floatingNavTabs = listOf(BottomNavTab.Gallery, BottomNavTab.Albums).map { tab ->
    FloatingNavTab(
        route = tab.route,
        label = tab.label,
        selectedIcon = tab.selectedIcon,
        unselectedIcon = tab.unselectedIcon
    )
}

/** Routes that should show the bottom navigation bar. */
private val bottomNavRoutes = listOf(BottomNavTab.Gallery, BottomNavTab.Albums).map { it.route }

/**
 * Main navigation graph for the Gallery app.
 *
 * Sets up a [NavHost] with bottom navigation between Gallery and Albums tabs,
 * plus a detail screen for individual images.
 *
 * The [GalleryViewModel] is obtained via [hiltViewModel] and scoped to this
 * composable so it's shared across all screens in the navigation graph.
 * Hilt handles the creation and lifecycle of the ViewModel automatically.
 */
@Composable
fun GalleryNavGraph() {
    val navController: NavHostController = rememberNavController()
    val viewModel: GalleryViewModel = hiltViewModel()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Only show bottom bar on top-level screens
    val showBottomBar = currentDestination?.route in bottomNavRoutes
    val isOnAlbumsScreen = currentDestination?.route == GalleryRoutes.ALBUMS

    // Total height of the floating nav bar for offsetting content and FAB
    val navBarHeight = floatingNavBarTotalHeight()

    // FAB state — managed here so the FAB lives in the overlay layer above the nav bar
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var albumsScrolledDown by remember { mutableStateOf(false) }

    // Use Box overlay so the floating bar sits on top of the content
    // instead of Scaffold's bottomBar slot which docks it flush to the bottom.
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = GalleryRoutes.GALLERY_GRID,
            modifier = Modifier.fillMaxSize()
        ) {
            // Gallery Grid Screen
            composable(GalleryRoutes.GALLERY_GRID) {
                GalleryGridScreen(
                    viewModel = viewModel,
                    onImageClick = { imageId ->
                        navController.navigate(GalleryRoutes.imageDetail(imageId))
                    }
                )
            }

            // Image Detail Screen
            composable(
                route = GalleryRoutes.IMAGE_DETAIL,
                arguments = listOf(
                    navArgument("imageId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val imageId = backStackEntry.arguments?.getLong("imageId") ?: 0L
                ImageDetailScreen(
                    imageId = imageId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Albums Screen
            composable(GalleryRoutes.ALBUMS) {
                AlbumsScreen(
                    viewModel = viewModel,
                    onAlbumClick = { albumId ->
                        navController.navigate(GalleryRoutes.albumDetail(albumId))
                    },
                    extraBottomPadding = navBarHeight,
                    onScrolledDown = { scrolledDown ->
                        albumsScrolledDown = scrolledDown
                    }
                )
            }

            // Album Detail Screen (filtered gallery for a specific album)
            composable(
                route = GalleryRoutes.ALBUM_DETAIL,
                arguments = listOf(
                    navArgument("albumId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId")?.let {
                    URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
                } ?: ""
                GalleryGridScreen(
                    viewModel = viewModel,
                    onImageClick = { imageId ->
                        navController.navigate(GalleryRoutes.imageDetail(imageId))
                    },
                    albumId = albumId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Extended FAB — only on Albums screen, positioned above the floating nav bar
        if (isOnAlbumsScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = navBarHeight + 8.dp
                    )
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateAlbumDialog = true },
                    expanded = !albumsScrolledDown,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null
                        )
                    },
                    text = { Text("New Album") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Floating nav bar overlaid at the bottom of the screen
        if (showBottomBar) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                FloatingBottomNavBar(
                    tabs = floatingNavTabs,
                    selectedRoute = currentDestination?.route ?: "",
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }

    // Create Album dialog
    if (showCreateAlbumDialog) {
        CreateAlbumDialog(
            onDismiss = { showCreateAlbumDialog = false },
            onCreate = { albumName ->
                viewModel.createAlbum(albumName)
                showCreateAlbumDialog = false
            }
        )
    }
}

/**
 * Dialog for creating a new album.
 */
@Composable
private fun CreateAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var albumName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Album") },
        text = {
            OutlinedTextField(
                value = albumName,
                onValueChange = { albumName = it },
                label = { Text("Album name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(albumName.trim()) },
                enabled = albumName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
