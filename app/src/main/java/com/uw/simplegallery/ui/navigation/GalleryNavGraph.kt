package com.uw.simplegallery.ui.navigation

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import com.uw.simplegallery.R
import com.uw.simplegallery.data.model.AlbumItem
import com.uw.simplegallery.data.model.MediaItem
import com.uw.simplegallery.ui.components.FloatingBottomNavBar
import com.uw.simplegallery.ui.components.FloatingNavTab
import com.uw.simplegallery.ui.components.floatingNavBarTotalHeight
import com.uw.simplegallery.ui.screens.albums.AlbumsScreen
import com.uw.simplegallery.ui.screens.detail.ImageDetailScreen
import com.uw.simplegallery.ui.screens.gallery.GalleryGridScreen
import com.uw.simplegallery.ui.screens.search.SearchTagsScreen
import com.uw.simplegallery.ui.screens.search.TagManagerScreen
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
    const val SEARCH_TAGS = "search_tags"
    const val TAG_MANAGER = "tag_manager"
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
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Gallery : BottomNavTab(
        route = GalleryRoutes.GALLERY_GRID,
        selectedIcon = Icons.Filled.Photo,
        unselectedIcon = Icons.Outlined.Photo
    )

    data object Albums : BottomNavTab(
        route = GalleryRoutes.ALBUMS,
        selectedIcon = Icons.Filled.PhotoAlbum,
        unselectedIcon = Icons.Outlined.PhotoAlbum
    )

    data object SearchTags : BottomNavTab(
        route = GalleryRoutes.SEARCH_TAGS,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    )
}

/** Routes that should show the bottom navigation bar. */
private val bottomNavRoutes = listOf(
    BottomNavTab.Gallery,
    BottomNavTab.Albums,
    BottomNavTab.SearchTags
).map { it.route }

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

    // Selection state — scoped to the nav graph so it persists across tab switches
    val selectedMediaItems = remember { mutableStateListOf<MediaItem>() }
    val selectedAlbumItems = remember { mutableStateListOf<AlbumItem>() }

    // ── Delete confirmation launcher (API 30+) ─────────────────────────
    // Tracks the IDs that are pending user confirmation in the system dialog.
    var pendingDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val deleteConfirmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        viewModel.onDeleteConfirmationResult(approved, pendingDeleteIds)
        if (approved) {
            // Clear both media and album selections after successful deletion
            selectedMediaItems.clear()
            selectedAlbumItems.clear()
        }
        pendingDeleteIds = emptyList()
    }

    // Observe delete confirmation events from the ViewModel and launch the system dialog.
    // Uses Channel-based flow for reliable one-shot event delivery — events are consumed
    // exactly once, preventing the race condition where StateFlow could drop events.
    LaunchedEffect(Unit) {
        viewModel.deleteConfirmationEvent.collect { event ->
            pendingDeleteIds = event.pendingIds
            try {
                val request = IntentSenderRequest.Builder(event.intentSender).build()
                deleteConfirmLauncher.launch(request)
            } catch (e: Exception) {
                Log.e("GalleryNavGraph", "Failed to launch delete confirmation dialog: ${e.message}", e)
            }
        }
    }

    // Derived: true when any selection is active (media or albums)
    val isAnySelectionActive by remember {
        derivedStateOf { selectedMediaItems.isNotEmpty() || selectedAlbumItems.isNotEmpty() }
    }

    // Only show bottom bar on top-level screens AND when not in selection mode
    val showBottomBar = currentDestination?.route in bottomNavRoutes && !isAnySelectionActive
    val isOnAlbumsScreen = currentDestination?.route == GalleryRoutes.ALBUMS

    // Total height of the floating nav bar for offsetting content and FAB
    val navBarHeight = floatingNavBarTotalHeight()

    val floatingNavTabs = listOf(
        FloatingNavTab(
            route = BottomNavTab.Gallery.route,
            label = stringResource(id = R.string.nav_gallery),
            selectedIcon = BottomNavTab.Gallery.selectedIcon,
            unselectedIcon = BottomNavTab.Gallery.unselectedIcon
        ),
        FloatingNavTab(
            route = BottomNavTab.Albums.route,
            label = stringResource(id = R.string.nav_albums),
            selectedIcon = BottomNavTab.Albums.selectedIcon,
            unselectedIcon = BottomNavTab.Albums.unselectedIcon
        ),
        FloatingNavTab(
            route = BottomNavTab.SearchTags.route,
            label = stringResource(id = R.string.nav_search_tags),
            selectedIcon = BottomNavTab.SearchTags.selectedIcon,
            unselectedIcon = BottomNavTab.SearchTags.unselectedIcon
        )
    )

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
                    },
                    selectedItemsList = selectedMediaItems,
                    extraBottomPadding = navBarHeight
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
                    selectedAlbumsList = selectedAlbumItems,
                    extraBottomPadding = navBarHeight,
                    onScrolledDown = { scrolledDown ->
                        albumsScrolledDown = scrolledDown
                    }
                )
            }

            composable(GalleryRoutes.SEARCH_TAGS) {
                SearchTagsScreen(
                    viewModel = viewModel,
                    onImageClick = { imageId ->
                        navController.navigate(GalleryRoutes.imageDetail(imageId))
                    },
                    onAlbumClick = { albumId ->
                        navController.navigate(GalleryRoutes.albumDetail(albumId))
                    },
                    onOpenTagManager = { navController.navigate(GalleryRoutes.TAG_MANAGER) },
                    extraBottomPadding = navBarHeight
                )
            }

            composable(GalleryRoutes.TAG_MANAGER) {
                TagManagerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    extraBottomPadding = navBarHeight
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
                    selectedItemsList = selectedMediaItems,
                    albumId = albumId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Extended FAB — only on Albums screen when not in selection mode,
        // positioned above the floating nav bar
        if (isOnAlbumsScreen && !isAnySelectionActive) {
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
        // Hidden when any selection is active
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
