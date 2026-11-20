package com.harsh.desicrics.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIllustrations
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Splits [items] into pages that each fit [budget] (in design dp), measuring each item with [height].
 * An item that [keepsWithNext] (a heading) never ends a page; a page that starts mid-section begins with
 * the [carry] rows for its first item (e.g. "Strikers innings, continued" and the column headings), and
 * items that [dropAtTop] (spacers) are left off the top of a page. Always at least one page.
 *
 * Long posters (a scorecard, a squad, a day's fixtures) use this to decide their pages, then hand the
 * count to [ShareTemplate.paged].
 */
fun <T> paginate(
    items: List<T>,
    budget: Float,
    height: (T) -> Float,
    keepsWithNext: (T) -> Boolean = { false },
    carry: (T) -> List<T> = { emptyList() },
    dropAtTop: (T) -> Boolean = { false },
): List<List<T>> {
    val pages = mutableListOf<List<T>>()
    var page = mutableListOf<T>()
    var used = 0f
    items.forEachIndexed { i, item ->
        // A heading needs room for itself and whatever it heads.
        var need = height(item)
        var j = i
        while (keepsWithNext(items[j]) && j + 1 < items.size) need += height(items[++j])
        if (page.isNotEmpty() && used + need > budget) {
            pages += page
            page = carry(item).toMutableList()
            used = page.sumOf { height(it).toDouble() }.toFloat()
        }
        if (page.isEmpty() && dropAtTop(item)) return@forEachIndexed
        page += item
        used += height(item)
    }
    pages += page
    return pages
}

/** First letters of the first and last words: "Rohan Iyer" → "RI", "Zoya" → "Z". */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull()).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
}

/**
 * A round badge for a player or team: their photo or logo where there is one, and big initials where
 * there isn't. [ring] draws a thin border in that colour.
 */
@Composable
internal fun PosterAvatar(name: String, url: String?, size: Dp, background: Color, ink: Color, ring: Color? = null) {
    val shape = DesiTheme.shapes.pill
    Box(
        Modifier.size(size).clip(shape).background(background).then(if (ring != null) Modifier.border(size / 40 + 1.dp, ring, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            PhotoAvatar(name, url, Modifier.fillMaxSize().padding(if (ring != null) size / 40 + 1.dp else 0.dp), size = size.value.toInt())
        } else {
            val letters = (size.value * 0.36f).toInt()
            Text(initialsOf(name), style = DesiTheme.type.scoreLg.sized(letters, letters + 4), color = ink, maxLines = 1)
        }
    }
}

/** A gold tab with a word in capitals, at the head of a poster: "RESULT", "SQUAD". */
@Composable
internal fun PosterTag(text: String, modifier: Modifier = Modifier) {
    val share = DesiTheme.colors.share
    Box(modifier.background(share.gold).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text.uppercase(), style = DesiTheme.type.label.sized(12, 14), color = share.onGold, maxLines = 1)
    }
}

/** A full-width gold strip with the result, the line every result poster ends on. */
@Composable
internal fun PosterResultStrip(text: String, post: Boolean, modifier: Modifier = Modifier) {
    val share = DesiTheme.colors.share
    Box(modifier.fillMaxWidth().background(share.gold).padding(horizontal = 14.dp, vertical = if (post) 7.dp else 10.dp)) {
        Text(text.uppercase(), style = DesiTheme.type.bodyStrong.sized(if (post) 13 else 15, if (post) 17 else 20), color = share.onGold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The foot of every opaque poster: the DesiCrics mark, an optional [caption] (the match), and "2/3" when
 * the poster has more than one page.
 */
@Composable
internal fun PosterFooter(ink: Color, page: Int = 0, pages: Int = 1, caption: String? = null, modifier: Modifier = Modifier) {
    val type = DesiTheme.type
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(DesiIllustrations.BallMark), contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(stringResource(Res.string.app_name).uppercase(), style = type.label.sized(12, 14), color = ink)
        if (caption != null) {
            Text(caption, style = type.caption.sized(11, 14), color = ink.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (pages > 1) Text(stringResource(Res.string.share_page, page + 1, pages), style = type.label.sized(13, 16), color = ink)
    }
}

/** A hairline across the poster in [ink]. */
@Composable
internal fun PosterRule(ink: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(ink))
}

/**
 * The brand-green backdrop most posters sit on: a faint ball seam sweeping in from the right, so a plain
 * colour still looks printed.
 */
@Composable
internal fun PosterBackdrop(modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    val faint = colors.onBrandStrong.copy(alpha = 0.06f)
    Canvas(modifier.fillMaxSize().background(colors.brandStrong)) {
        val center = Offset(size.width * 1.02f, size.height * 0.32f)
        val radius = size.width * 0.62f
        drawCircle(faint, radius, center)
        val seam = 2.dp.toPx()
        drawCircle(faint, radius * 0.78f, center, style = Stroke(seam))
        drawCircle(faint, radius * 0.72f, center, style = Stroke(seam))
    }
}
