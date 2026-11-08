package dev.lumbina.tv.feature.home

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.backdrop.MediaFile
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.TileContent
import dev.lumbina.tv.core.domain.phone.PhonePage
import dev.lumbina.tv.core.ui.AppArtwork
import dev.lumbina.tv.core.ui.BANNER_ASPECT
import dev.lumbina.tv.core.ui.OVER_PICTURE
import dev.lumbina.tv.core.ui.QrCode
import dev.lumbina.tv.core.ui.SidePanel
import dev.lumbina.tv.core.ui.TickBadge
import dev.lumbina.tv.core.ui.focusGlow
import dev.lumbina.tv.core.ui.highlightBorder
import dev.lumbina.tv.core.ui.requestFocusSoon

/**
 * Where the focus starts among the tiles: on the picture the tile shows now ([current]), after the
 * app's own banner; on the app's own (0) when it has no other or that picture is gone.
 */
internal fun bannerStart(current: String?, pictures: List<WallpaperItem>): Int =
    pictures.indexOfFirst { it.uri == current }.let { if (current == null || it < 0) 0 else it + 1 }

/** A tile that can show the user's own banner: an app's, or one of Lumbina's own. */
sealed interface BannerOf {
    /** Under which [dev.lumbina.tv.core.domain.home.HomeAppearance.customBanners] keeps the tile's banner. */
    val key: String

    data class OfApp(val app: App) : BannerOf {
        override val key: String get() = app.id.flatten()
    }

    data object AllApps : BannerOf {
        override val key: String = HomeItem.AllApps.key
    }

    data class OfInput(val input: TileContent.InputTile) : BannerOf {
        override val key: String get() = input.key
    }
}

/** The user's banners, and the library's photos a banner is made of on choosing one. */
data class BannerChoices(val banners: List<WallpaperItem>, val photos: List<MediaFile>)

/**
 * What the tile shows by itself (an app's own banner, the icon of a tile of Lumbina's) and the
 * user's banners, as tiles two a line; the one the tile shows now ([current], null for its own) is
 * ticked and has the focus. OK puts one on the tile and goes back ([dismiss]). Under them the media
 * library's photos: OK on one puts a copy of it in the banners and on the tile. Last, how to send
 * another banner from a phone; one sent while the panel is open joins the tiles.
 */
@Composable
internal fun BannerPicker(
    of: BannerOf,
    current: String?,
    commands: HomeCommands,
    dismiss: () -> Unit,
    /** Closes every menu, for leaving home for the settings. */
    closeAll: () -> Unit = dismiss,
) {
    val flow = remember(commands) { commands.bannerChoices() }
    val choices = flow.collectAsState(null).value
    val pictures = choices?.banners
    val phone = produceState<PhonePage?>(null) { value = commands.phonePage() }.value
    val start = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    val choose = { uri: String? ->
        commands.setBanner(of.key, uri)
        dismiss()
    }
    val name = when (of) {
        is BannerOf.OfApp -> of.app.label
        BannerOf.AllApps -> stringResource(R.string.home_all_apps)
        is BannerOf.OfInput -> of.input.name
    }
    // The name of the entry that opened the panel, which needs no declension around the tile's name.
    SidePanel(
        title = pageName(R.string.menu_banner),
        onDismiss = dismiss,
        // "All apps" has the banner in its own menu, with no "Name and banner" on the way.
        path = if (of == BannerOf.AllApps) listOf(name) else listOf(name, pageName(R.string.menu_name_banner)),
    ) {
        if (choices == null || pictures == null) return@SidePanel
        // Once, when the pictures are first read: one sent later must not pull the focus to itself.
        val startAt = remember { bannerStart(current, pictures) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            // Room for the focused tile to grow and for the tick over its corner.
            contentPadding = PaddingValues(10.dp),
            // Only as tall as the pictures, so the way to add one stands right under them.
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        ) {
            item(key = ORIGINAL_KEY) {
                BannerChoice(
                    onClick = { choose(null) },
                    chip = stringResource(R.string.banner_original),
                    chosen = current == null,
                    chipOnTop = true,
                    modifier = if (startAt == 0) Modifier.focusRequester(start) else Modifier,
                ) { OwnLook(of) }
            }
            itemsIndexed(pictures, key = { _, picture -> picture.uri }) { index, picture ->
                BannerChoice(
                    onClick = { choose(picture.uri) },
                    chip = picture.title,
                    chosen = picture.uri == current,
                    modifier = if (startAt == index + 1) Modifier.focusRequester(start) else Modifier,
                ) { Picture(picture.uri, picture.title) }
            }
            photoChoices(choices.photos) { photo ->
                commands.bannerFromPhoto(of.key, photo)
                dismiss()
            }
        }
        phone?.let { AddFromPhone(it, closeAll, commands::openNetwork, Modifier.padding(top = 12.dp)) }
        LaunchedEffect(Unit) {
            // A tile far down is not laid out yet: bring it in first, then focus it.
            grid.scrollToItem(startAt)
            start.requestFocusSoon()
        }
    }
}

/** What the tile shows without a banner of the user's: the app's own, or the icon and name of Lumbina's tile. */
@Composable
private fun OwnLook(of: BannerOf) {
    when (of) {
        is BannerOf.OfApp -> AppArtwork(of.app)
        BannerOf.AllApps -> IconArt(R.drawable.ic_apps)
        is BannerOf.OfInput -> IconArt(R.drawable.ic_input_hdmi)
    }
}

/** The library's [photos] under a title of their own; [pick] one to make a banner of it. */
private fun LazyGridScope.photoChoices(photos: List<MediaFile>, pick: (MediaFile) -> Unit) {
    if (photos.isEmpty()) return
    item(key = PHOTOS_KEY, span = { GridItemSpan(maxLineSpan) }) { PhotosTitle() }
    items(photos, key = { "photo:${it.id}" }) { photo ->
        BannerChoice(onClick = { pick(photo) }, chip = null, chosen = false) { Picture(photo.uri, photo.title) }
    }
}

@Composable
private fun Picture(uri: String, title: String?) {
    AsyncImage(
        model = uri,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * A 16:9 tile to choose, with [chip] over its picture: at the top for the app's own banner, at the
 * bottom for a picture's name. A tick on its corner when it is the [chosen] one.
 */
@Composable
private fun BannerChoice(
    onClick: () -> Unit,
    chip: String?,
    chosen: Boolean,
    modifier: Modifier = Modifier,
    chipOnTop: Boolean = false,
    picture: @Composable () -> Unit,
) {
    Box(modifier) {
        Card(
            onClick = onClick,
            border = highlightBorder(),
            glow = focusGlow(),
            modifier = Modifier.aspectRatio(BANNER_ASPECT),
        ) {
            Box(Modifier.fillMaxSize()) {
                picture()
                chip?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(if (chipOnTop) Alignment.TopStart else Alignment.BottomStart)
                            .padding(8.dp)
                            .background(OVER_PICTURE, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        // Over the card, which rises over what stands beside it while focused.
        if (chosen) {
            TickBadge(checked = true, Modifier.align(Alignment.TopEnd).offset(x = BADGE_OUT, y = -BADGE_OUT).zIndex(1f))
        }
    }
}

/** Over the library's photos: what they are, and what choosing one does. */
@Composable
private fun PhotosTitle(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.padding(top = 12.dp),
    ) {
        Text(stringResource(R.string.banner_photos), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.banner_photos_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The code that opens the phone remote page, where pictures are sent from; or why there is none. */
@Composable
private fun AddFromPhone(
    page: PhonePage,
    closeAll: () -> Unit,
    openNetwork: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = BLOCK_ALPHA), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (page is PhonePage.At) QrCode(page.address, size = CODE_SIZE)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.banner_add), style = MaterialTheme.typography.titleMedium)
            val how = when (page) {
                is PhonePage.At -> R.string.banner_add_how
                PhonePage.Off -> R.string.banner_phone_off
                PhonePage.ServiceOff -> R.string.banner_service_off
                PhonePage.NoNetwork -> R.string.banner_no_network
            }
            Text(
                stringResource(how),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Where the text sends the user, one OK away.
            val target = when (page) {
                PhonePage.Off -> LumbinaActions.SETTINGS_PAGE_PHONE
                PhonePage.ServiceOff -> LumbinaActions.SETTINGS_ROOT
                else -> null
            }
            val context = LocalContext.current
            // No network: the device's own settings for it, as the status plate offers.
            if (page == PhonePage.NoNetwork) {
                OutlinedButton(
                    onClick = {
                        // As the status plate: a device with no such screen says so rather than nothing.
                        if (!openNetwork()) {
                            Toast.makeText(
                                context,
                                R.string.status_network_closed,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ) { Text(stringResource(R.string.banner_open_network)) }
            }
            if (target != null) {
                // The menus are closed first: home stays alive under the settings, and so would they.
                OutlinedButton(
                    onClick = {
                        closeAll()
                        context.openLumbinaPage(target)
                    },
                ) {
                    Text(stringResource(R.string.banner_open_settings))
                }
            }
        }
    }
}

/** Opens Lumbina's settings at [page], as the remote's overlays do: home is left for the settings. */
private fun Context.openLumbinaPage(page: String) {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    startActivity(intent.putExtra(LumbinaActions.EXTRA_SETTINGS_PAGE, page))
}

/** The name of the page a menu entry opens: the entry's own words, without the "…" that says a page follows. */
@Composable
private fun pageName(@StringRes entry: Int): String = stringResource(entry).removeSuffix("…")

private const val ORIGINAL_KEY = "original"
private const val PHOTOS_KEY = "photos"
private const val BLOCK_ALPHA = 0.06f

/** With the card's white margin about 100 dp: smaller than in the settings, the pictures come first here. */
private val CODE_SIZE = 64.dp
