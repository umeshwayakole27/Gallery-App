package com.uw.simplegallery.ui.components

import android.os.Build
import android.content.res.Resources
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Data class representing a tab in the floating bottom navigation bar.
 *
 * @param route Navigation route for this tab
 * @param label Display label
 * @param selectedIcon Icon shown when the tab is selected (filled variant)
 * @param unselectedIcon Icon shown when the tab is not selected (outlined variant)
 */
data class FloatingNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Detects if the device uses gesture-based navigation or traditional button navigation.
 */
private fun isGestureNavigationEnabled(resources: Resources): Boolean {
    return try {
        val resourceId = resources.getIdentifier(
            "config_navBarInteractionMode",
            "integer",
            "android"
        )
        if (resourceId > 0) {
            // 0 = 3-button, 1 = 2-button, 2 = gesture navigation
            resources.getInteger(resourceId) == 2
        } else {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        }
    } catch (_: Exception) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}

/**
 * Returns the total height occupied by the floating nav bar (pill + padding),
 * so other UI elements (e.g. FAB) can offset themselves above it.
 */
@Composable
fun floatingNavBarTotalHeight(): Dp {
    val context = LocalContext.current
    val isGestureNav = remember { isGestureNavigationEnabled(context.resources) }
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val bottomPadding = if (!isGestureNav) {
        navigationBarPadding.calculateBottomPadding() + 8.dp
    } else {
        16.dp
    }
    // pill height (76) + top padding (16) + bottom padding
    return 76.dp + 16.dp + bottomPadding
}

/**
 * A floating pill-shaped bottom navigation bar, adapted from the Tulsi Gallery app.
 *
 * Features:
 * - Pill-shaped container with rounded corners and elevation shadow
 * - Animated color transitions for selected/unselected states
 * - Circular selection indicator behind the active tab icon
 * - Filled/outlined icon switching on selection
 * - Gesture vs button navigation aware padding
 * - Only the pill itself intercepts touches; transparent areas pass through
 *
 * @param tabs List of navigation tabs to display
 * @param selectedRoute The route of the currently selected tab
 * @param onTabSelected Callback when a tab is tapped
 */
@Composable
fun FloatingBottomNavBar(
    tabs: List<FloatingNavTab>,
    selectedRoute: String,
    onTabSelected: (FloatingNavTab) -> Unit
) {
    val context = LocalContext.current
    val isGestureNav = remember { isGestureNavigationEnabled(context.resources) }
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()

    // Extra bottom padding for devices with traditional navigation buttons
    val additionalBottomPadding = if (!isGestureNav) {
        navigationBarPadding.calculateBottomPadding() + 8.dp
    } else {
        16.dp
    }

    // wrapContentHeight so the outer Box only occupies the pill's actual space
    // and does NOT stretch to fill, preventing it from stealing touches
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 16.dp,
                bottom = additionalBottomPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill-shaped bar — only this receives touches
        Box(
            modifier = Modifier
                .height(76.dp)
                .fillMaxWidth(0.95f)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(percent = 35),
                    spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                .clip(RoundedCornerShape(percent = 35))
                .background(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    shape = RoundedCornerShape(percent = 35)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEach { tab ->
                    FloatingNavBarItem(
                        tab = tab,
                        isSelected = selectedRoute == tab.route,
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
        }
    }
}

/**
 * Individual item inside the floating bottom navigation bar.
 *
 * Shows a circular selection indicator behind the icon when selected,
 * with smooth color animation transitions.
 */
@Composable
private fun FloatingNavBarItem(
    tab: FloatingNavTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val selectedColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "selectedColor"
    )
    val selectedIconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "selectedIconColor"
    )
    val selectedTextColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "selectedTextColor"
    )

    Column(
        modifier = Modifier
            .width(70.dp)
            .padding(vertical = 4.dp)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = { if (!isSelected) onClick() }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with circular selection indicator
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            // Selection indicator background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = selectedColor,
                        shape = RoundedCornerShape(percent = 100)
                    )
                    .clip(RoundedCornerShape(100))
            )

            // Tab icon — switches between filled and outlined variants
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = "Navigate to ${tab.label}",
                tint = selectedIconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        // Tab label
        Spacer(modifier = Modifier.height(0.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = selectedTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
