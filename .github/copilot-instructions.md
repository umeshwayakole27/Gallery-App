# Copilot Instructions for SimpleGallery

An Android gallery app using Jetpack Compose, Hilt DI, and Clean Architecture.

## Build & Test Commands

### Build
```bash
./gradlew :app:assembleDebug      # Debug build
./gradlew :app:assembleRelease    # Release build
./gradlew :app:build              # Build and run tests
```

### Testing
```bash
./gradlew :app:test                          # Run unit tests
./gradlew :app:connectedDebugAndroidTest     # Run all instrumented tests on device
./gradlew :app:connectedCheck                # Run all device checks
```

### Lint
```bash
./gradlew :app:lint                # Run lint checks
```

### Installation
```bash
./gradlew :app:installDebug        # Install debug build to connected device
./gradlew :app:installDebugAndroidTest  # Install test APK
```

## Architecture

### Clean Architecture Layers

The app follows Clean Architecture with distinct separation of concerns:

1. **Data Layer** (`data/`)
   - `model/` - Data models (`MediaItem`, `AlbumItem`, `MediaType`)
   - `repository/` - Repository interfaces and implementations
   - `MediaManager` - Direct MediaStore access (data source)
   - `MediaRepository` interface - Abstraction over data operations
   - `MediaRepositoryImpl` - Implementation using `MediaManager`

2. **Domain Layer** (`usecase/`)
   - Single-responsibility use cases that orchestrate business logic
   - `GetMediaItemsUseCase`, `GetAlbumsUseCase`, etc.
   - Each use case injected independently into ViewModels

3. **Presentation Layer** (`ui/`, `viewmodel/`)
   - `viewmodel/` - `GalleryViewModel` manages UI state with StateFlow
   - `ui/screens/` - Screen composables (gallery, detail, albums, video)
   - `ui/components/` - Reusable UI components
   - `ui/navigation/` - Navigation graph and routes
   - `ui/theme/` - Material3 theming

### Dependency Flow
ViewModels → Use Cases → Repository Interface → Repository Impl → MediaManager → MediaStore

ViewModels depend on use cases and repository interfaces, never on concrete data sources directly.

## Dependency Injection (Hilt)

### Special Setup Required

**CRITICAL:** This project uses Hilt **without** the Hilt Gradle plugin (incompatible with AGP 9.x). This requires explicit base class specification:

```kotlin
// Application class MUST extend generated Hilt class
@HiltAndroidApp(Application::class)
class SimpleGalleryApp : Hilt_SimpleGalleryApp(), ImageLoaderFactory { }

// Activities MUST extend generated Hilt class
@AndroidEntryPoint(androidx.activity.ComponentActivity::class)
class MainActivity : Hilt_MainActivity() { }
```

**Why:** Without the Hilt plugin, `@HiltAndroidApp` and `@AndroidEntryPoint` require explicit base class parameters so Hilt knows which class to generate. The generated classes (e.g., `Hilt_SimpleGalleryApp`) handle component initialization.

**Consequence:** If you create new Activities, they MUST follow this pattern or the app will crash with "Hilt Activity must be attached to an @HiltAndroidApp Application".

### DI Bindings

All app-level bindings are in `di/AppModule.kt`:
- `MediaManager` - Singleton data source
- `MediaRepository` - Singleton repository interface → `MediaRepositoryImpl`

Use cases and ViewModels use `@Inject` constructors for Hilt to discover them automatically.

## Key Conventions

### Navigation & State Management

- **Shared ViewModel:** `GalleryViewModel` is scoped to `GalleryNavGraph` composable via `hiltViewModel()`, making it shared across all screens in the nav graph
- **Selection State:** Selection lists (`selectedMediaItems`, `selectedAlbumItems`) are managed in `GalleryNavGraph` using `remember { mutableStateListOf() }` to persist across tab switches
- **Delete Confirmation:** Media deletion on API 30+ requires user approval. The ViewModel emits `DeleteConfirmationEvent` via a Channel (not StateFlow) for one-shot event delivery. The nav graph observes this and launches the system dialog via `ActivityResultLauncher`

### Media Deletion Flow (API 30+)

1. UI calls `viewModel.deleteMediaItems(ids)`
2. Repository attempts deletion, returns `DeleteResult.RequiresConfirmation` with `IntentSender`
3. ViewModel sends `DeleteConfirmationEvent` to Channel
4. Nav graph collects event and launches system dialog
5. On approval, nav graph calls `viewModel.onDeleteConfirmationResult(true, ids)`
6. ViewModel verifies with MediaStore which items were deleted (handles partial approvals) and refreshes

### Coil Image Loading

`SimpleGalleryApp` implements `ImageLoaderFactory` to provide a custom Coil `ImageLoader` with `VideoFrameDecoder.Factory()`. This enables loading video thumbnails from `content://` URIs. Without this, video items would appear as blank tiles.

### StateFlow Patterns

- ViewModels expose `StateFlow<T>` for UI state (media, albums, loading, errors)
- Use `.asStateFlow()` to expose read-only StateFlow from private MutableStateFlow
- Channels are used for one-shot events (e.g., delete confirmations) that must be delivered exactly once

### URL Encoding for Album Routes

Album IDs (folder paths) can contain special characters like `/`. The nav graph uses `URLEncoder.encode()` / `URLDecoder.decode()` when passing album IDs as route parameters to prevent navigation parsing errors.

### Floating Bottom Nav Bar

- Uses custom `FloatingBottomNavBar` component instead of Material3 NavigationBar
- Overlaid using `Box` instead of Scaffold's `bottomBar` slot
- Hidden when selection mode is active (`isAnySelectionActive`)
- Content and FAB offset by `floatingNavBarTotalHeight()` to prevent overlap

## Android-Specific Details

### Permissions

- **API 33+:** `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`
- **API < 33:** `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` (max SDK 32/29)
- **MANAGE_MEDIA:** Allows bypassing per-file permission prompts on API 31+

Permissions requested at runtime via Accompanist Permissions in `RequestPermissions` composable.

### Target SDK

- minSdk: 28 (Android 9.0)
- targetSdk: 36 (Android 15+)
- compileSdk: 36

### Video Playback

Uses Media3 ExoPlayer for video playback in `VideoPlayer.kt`. Coil Video provides video thumbnail extraction for the gallery grid.

## Dependencies of Note

- **Hilt:** 2.59.2 (without Hilt Gradle plugin)
- **Compose BOM:** 2024.09.00
- **Navigation Compose:** 2.8.4
- **Coil:** 2.7.0 (with coil-video for thumbnails)
- **Media3:** 1.6.1 (ExoPlayer)
- **Accompanist Permissions:** 0.36.0
- **KSP:** 2.1.21-2.0.2 (for Hilt annotation processing)
- **Kotlin:** 2.1.21
- **Kotlin Serialization:** Used for navigation type-safe arguments

Dependencies managed via Gradle Version Catalog (`gradle/libs.versions.toml`).
