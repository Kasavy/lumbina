@file:Suppress("InvalidPackageDeclaration", "MatchingDeclarationName")

package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcel
import android.os.Parcelable

/*
 * The parcelables of the Projectivy wallpaper plugin API
 * (https://github.com/spocky/projectivy-plugin-wallpaper-provider, Apache-2.0), written by hand so
 * they need no Parcelize plugin, but laid out exactly as Parcelize writes the originals: plugins
 * built against the original API read and write them.
 */

/**
 * What asks a plugin for wallpapers. Lumbina only sends [TimeElapsed] ("time for the next ones"),
 * which Parcelize writes as the single int 1 — the same value as its event type.
 */
sealed class Event : Parcelable {
    data object TimeElapsed : Event() {
        override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeInt(TIME_ELAPSED)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val TIME_ELAPSED = 1

        @JvmField
        val CREATOR = object : Parcelable.Creator<Event> {
            override fun createFromParcel(parcel: Parcel): Event {
                parcel.readInt()
                return TimeElapsed
            }

            override fun newArray(size: Int): Array<Event?> = arrayOfNulls(size)
        }
    }
}

/** One wallpaper from a plugin; [type] and [displayMode] use the constants below. */
data class Wallpaper(
    val uri: String,
    val type: Int = IMAGE,
    val displayMode: Int = CROP,
    val title: String? = null,
    val source: String? = null,
    val author: String? = null,
    val actionUri: String? = null,
) : Parcelable {

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(uri)
        dest.writeInt(type)
        dest.writeInt(displayMode)
        dest.writeString(title)
        dest.writeString(source)
        dest.writeString(author)
        dest.writeString(actionUri)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val IMAGE = 0
        const val DRAWABLE = 1
        const val ANIMATED_DRAWABLE = 2
        const val LOTTIE = 3
        const val VIDEO = 4
        const val COLOR = 5
        const val CROP = 1

        @JvmField
        val CREATOR = object : Parcelable.Creator<Wallpaper> {
            override fun createFromParcel(parcel: Parcel) = Wallpaper(
                uri = parcel.readString().orEmpty(),
                type = parcel.readInt(),
                displayMode = parcel.readInt(),
                title = parcel.readString(),
                source = parcel.readString(),
                author = parcel.readString(),
                actionUri = parcel.readString(),
            )

            override fun newArray(size: Int): Array<Wallpaper?> = arrayOfNulls(size)
        }
    }
}
