package dev.lumbina.tv.core.ui

import android.os.Build

/**
 * Whether a Compose layer (what scaling, fading or clipping a piece of the screen takes) is the
 * system's own render node. From Android 10 it is. On Android 9, as seen on a Mi Box 4, each
 * layer is a View: slow to make, and a fading one is drawn off screen on every frame. Older
 * versions were not looked at and are treated as Android 9.
 */
val LAYERS_ARE_CHEAP: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
