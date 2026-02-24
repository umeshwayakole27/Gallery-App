package com.uw.simplegallery.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uw.simplegallery.ui.screens.albums.AlbumsScreen
import com.uw.simplegallery.ui.screens.detail.ImageDetailScreen
import com.uw.simplegallery.ui.screens.gallery.GalleryGridScreen
import com.uw.simplegallery.viewmodel.GalleryViewModel

// TODO: Add bottom navigation bar with Gallery and Albums tabs
// TODO: Pass image URI/ID safely via NavBackStackEntry arguments
// TODO: Handle deep links for shared image URIs

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

    /** Builds the album detail route for a specific album ID. */
    fun albumDetail(albumId: Long) = "album_detail/$albumId"
}

/**
 * Bottom navigation tab definitions.
 *
 * @param route The navigation route for this tab
 * @param label The display label for this tab
 * @param icon The icon for this tab
 */
sealed class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Gallery : BottomNavTab(
        route = GalleryRoutes.GALLERY_GRID,
        label = "Gallery",
        icon = Icons.Default.Photo
    )

    data object Albums : BottomNavTab(
        route = GalleryRoutes.ALBUMS,
        label = "Albums",
        icon = Icons.Default.PhotoAlbum
    )
}

/** All bottom navigation tabs. */
private val bottomNavTabs = listOf(BottomNavTab.Gallery, BottomNavTab.Albums)

/**
 * Main navigation graph for the Gallery app.
 *
 * Sets up a [NavHost] with bottom navigation between Gallery and Albums tabs,
 * plus a detail screen for individual images.
 *
 * The [GalleryViewModel] is scoped to this composable so it's shared across
 * all screens in the navigation graph.
 */
@Composable
fun GalleryNavGraph() {
    val navController: NavHostController = rememberNavController()
    val viewModel: GalleryViewModel = viewModel()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Only show bottom bar on top-level screens
    val showBottomBar = currentDestination?.route in bottomNavTabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavTabs.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    // Pop up to the start destination to avoid building up
                                    // a large stack of destinations on the back stack
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination
                                    launchSingleTop = true
                                    // Restore state when re-selecting a previously selected tab
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = GalleryRoutes.GALLERY_GRID,
            modifier = Modifier.padding(innerPadding)
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
                        // TODO: Navigate to filtered GalleryGridScreen on album tap
                        navController.navigate(GalleryRoutes.albumDetail(albumId))
                    }
                )
            }

            // Album Detail Screen (filtered gallery for a specific album)
            composable(
                route = GalleryRoutes.ALBUM_DETAIL,
                arguments = listOf(
                    navArgument("albumId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                // TODO: Implement album detail view with filtered images
                // For now, reuse GalleryGridScreen with the same viewModel
                GalleryGridScreen(
                    viewModel = viewModel,
                    onImageClick = { imageId ->
                        navController.navigate(GalleryRoutes.imageDetail(imageId))
                    }
                )
            }
        }
    }
}
