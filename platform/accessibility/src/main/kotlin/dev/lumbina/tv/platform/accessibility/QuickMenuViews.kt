package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The views of [QuickMenuOverlay], sized as the launcher's side panel is: its width, the gap to
 * the screen's edges, the corners and the type.
 */
internal class QuickMenuViews(private val context: Context) {

    /** The list of items, with each item's row and the label its value goes in. */
    class ListViews(val view: View, val rows: List<LinearLayout>, val values: List<TextView>)

    /** The window is the panel and the gap around it. */
    val windowWidth: Int get() = context.dp(PANEL_WIDTH_DP + 2 * PANEL_MARGIN_DP)

    fun frame() = FrameLayout(context).apply {
        val margin = context.dp(PANEL_MARGIN_DP)
        setPadding(margin, margin, margin, margin)
    }

    /**
     * The panel: [above] names what this page is inside of, then the [title], the [body], and what
     * the [keys] do, if given.
     */
    fun panel(title: String, above: String?, body: View, keys: String?): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val side = context.dp(PANEL_PADDING_DP)
        val vertical = context.dp(PANEL_VERTICAL_DP)
        setPadding(side, vertical, side, vertical)
        background = context.rounded(OverlayPalette.surface, PANEL_CORNER_DP)
        above?.let { addView(context.label(it, OverlayDimens.HINT_SP, OverlayPalette.muted)) }
        addView(context.label(title, TITLE_SP).apply { setPadding(0, 0, 0, context.dp(OverlayDimens.GAP_DP)) })
        addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        keys?.let { addView(context.label(it, OverlayDimens.HINT_SP, OverlayPalette.muted)) }
    }

    fun list(items: List<QuickMenuOverlay.Item>): ListViews {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val made = items.mapIndexed { index, item ->
            if (item.secondary && items.getOrNull(index - 1)?.secondary == false) column.addView(divider())
            val value = context.label("", OverlayDimens.HINT_SP).apply { gravity = Gravity.END }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val side = context.dp(ROW_PADDING_DP)
                val vertical = context.dp(ROW_VERTICAL_DP)
                setPadding(side, vertical, side, vertical)
                addView(
                    context.label(item.label, LABEL_SP).apply { typeface = MEDIUM },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
                )
                addView(value)
            }
            column.addView(row)
            row to value
        }
        return ListViews(column, made.map { it.first }, made.map { it.second })
    }

    /** The row the selection is [on] is filled, as the focused row of a launcher's panel is. */
    fun mark(row: LinearLayout, on: Boolean) {
        row.background = if (on) context.rounded(OverlayPalette.selected, ROW_CORNER_DP) else null
        val color = if (on) OverlayPalette.selectedText else OverlayPalette.text
        for (child in 0 until row.childCount) (row.getChildAt(child) as? TextView)?.setTextColor(color)
    }

    fun page(shown: QuickMenuOverlay.Page): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val side = context.dp(QR_DP)
        val picture = ImageView(context).apply { setImageBitmap(shown.image) }
        val place = LinearLayout.LayoutParams(side, side).apply { topMargin = context.dp(OverlayDimens.GAP_DP) }
        column.addView(picture, place)
        shown.lines.forEachIndexed { index, line ->
            // The first line is what to type where the code can't be read: the address.
            val label = context.label(line, if (index == 0) LABEL_SP else OverlayDimens.HINT_SP).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                if (index > 0) setTextColor(OverlayPalette.muted)
                setPadding(0, context.dp(OverlayDimens.GAP_DP), 0, 0)
            }
            column.addView(label)
        }
        return column
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(OverlayPalette.divider)
        val gap = context.dp(OverlayDimens.GAP_DP)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, context.dp(1)).apply {
            setMargins(context.dp(ROW_PADDING_DP), gap, context.dp(ROW_PADDING_DP), gap)
        }
    }

    companion object {
        const val PANEL_WIDTH_DP = 440
        const val PANEL_MARGIN_DP = 20
        const val PANEL_CORNER_DP = 22
        const val PANEL_PADDING_DP = 22
        const val PANEL_VERTICAL_DP = 18
        const val ROW_PADDING_DP = 12
        const val ROW_VERTICAL_DP = 11
        const val ROW_CORNER_DP = 13
        const val QR_DP = 240
        const val TITLE_SP = 24f
        const val LABEL_SP = 16f
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}
