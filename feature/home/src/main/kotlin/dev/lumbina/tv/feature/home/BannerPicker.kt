package dev.lumbina.tv.feature.home

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.phone.PhonePage
import dev.lumbina.tv.core.ui.AppArtwork
import dev.lumbina.tv.core.ui.BANNER_ASPECT
import dev.lumbina.tv.core.ui.QrCode
import dev.lumbina.tv.core.ui.SidePanel
import dev.lumbina.tv.core.ui.focusGlow
import dev.lumbina.tv.core.ui.highlightBorder
import dev.lumbina.tv.core.ui.requestFocusSoon

/**
 * Where the focus starts among the tiles: on the picture the tile shows now ([current]), after the
 * app's own banner; on the app's own (0) when it has no other or that picture is gone.
 */
internal fun bannerStart(current: String?, pictures: List<WallpaperItem>): Int =
    pictures.indexOfFirst { it.uri == current }.let { if (current == null || it < 0) 0 else it + 1 }

/**
 * The app's own banner and the user's pictures, as tiles two a line; the one the tile shows now
 * ([current], null for the app's own) is ticked and has the focus. OK puts one on the app's tile
 * and closes the panel. Under them, how to send another picture from a phone; one sent while the
 * panel is open joins the tiles.
 */
@Composable
internal fun BannerPicker(app: App, current: String?, commands: HomeCommands, dismiss: () -> Unit) {
    val choices = remember(commands) { commands.bannerChoices() }
    val pictures = choices.collectAsState(null).value
    val phone = produceState<PhonePage?>(null) { value = commands.phonePage() }.value
    val start = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    val choose = { uri: String? ->
        commands.setBanner(app, uri)
        dismiss()
    }
    // The name of the entry that opened the panel, which needs no declension around the app's name.
    SidePanel(
        title = pageName(R.string.menu_banner),
        onDismiss = dismiss,
        path = listOf(app.label, pageName(R.string.menu_name_banner)),
        subtitle = stringResource(R.string.banner_hint),
    ) {
        if (pictures == null) return@SidePanel
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
                ) { AppArtwork(app) }
            }
            itemsIndexed(pictures, key = { _, picture -> picture.uri }) { index, picture ->
                BannerChoice(
                    onClick = { choose(picture.uri) },
                    chip = picture.title,
                    chosen = picture.uri == current,
                    modifier = if (startAt == index + 1) Modifier.focusRequester(start) else Modifier,
                ) {
                    AsyncImage(
                        model = picture.uri,
                        contentDescription = picture.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        phone?.let { AddFromPhone(it, Modifier.padding(top = 12.dp)) }
        LaunchedEffect(Unit) {
            // A tile far down is not laid out yet: bring it in first, then focus it.
            grid.scrollToItem(startAt)
            start.requestFocusSoon()
        }
    }
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
                            .background(Color.Black.copy(alpha = CHIP_ALPHA), CircleShape)
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

/** The code that opens the phone remote page, where pictures are sent from; or why there is none. */
@Composable
private fun AddFromPhone(page: PhonePage, modifier: Modifier = Modifier) {
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
        }
    }
}

/** The name of the page a menu entry opens: the entry's own words, without the "…" that says a page follows. */
@Composable
private fun pageName(@StringRes entry: Int): String = stringResource(entry).removeSuffix("…")

private const val ORIGINAL_KEY = "original"
private const val CHIP_ALPHA = 0.6f
private const val BLOCK_ALPHA = 0.06f

/** With the card's white margin about 100 dp: smaller than in the settings, the pictures come first here. */
private val CODE_SIZE = 64.dp
