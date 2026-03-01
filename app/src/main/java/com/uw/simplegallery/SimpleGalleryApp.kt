package com.uw.simplegallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Simple Gallery.
 *
 * Annotated with [HiltAndroidApp] to trigger Hilt's code generation and
 * serve as the application-level dependency container.
 *
 * The base class [Application] is specified in the annotation parameter because
 * the Hilt Gradle plugin is not used (incompatible with AGP 9.x). This tells
 * Hilt to generate [Hilt_SimpleGalleryApp] extending [Application].
 *
 * We must extend [Hilt_SimpleGalleryApp] (the generated class) so that Hilt's
 * component initialization runs at app startup. Without this, Activities
 * annotated with [@AndroidEntryPoint] will crash with:
 * "Hilt Activity must be attached to an @HiltAndroidApp Application".
 *
 * Implements [ImageLoaderFactory] to provide a custom Coil [ImageLoader] with
 * [VideoFrameDecoder] support, enabling video thumbnail loading in the gallery grid.
 * Without this, Coil's default ImageLoader cannot decode video frames from
 * content:// URIs, and video items would appear as blank tiles.
 */
@HiltAndroidApp(Application::class)
class SimpleGalleryApp : Hilt_SimpleGalleryApp(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
