package com.harsh.desicrics.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.system.ImageFileFormat
import org.jetbrains.compose.resources.StringResource

/**
 * The shape of the picture. Every template is laid out on a fixed design canvas [DESIGN_WIDTH] wide (in
 * dp, a third of [widthPx]) and the canvas is scaled to whatever size it's shown or exported at, so a
 * template looks the same in the preview and in the shared file.
 */
enum class ShareFormat(val label: StringResource, val widthPx: Int, val heightPx: Int) {
    /** 9:16, for Instagram and WhatsApp stories and status. The default. */
    STORY(Res.string.share_format_story, 1080, 1920),
    /** 1:1, for a feed post. */
    POST(Res.string.share_format_post, 1080, 1080),
    ;

    val aspectRatio: Float get() = widthPx.toFloat() / heightPx
    /** The design canvas's height in dp; its width is always [DESIGN_WIDTH]. */
    val designHeight: Dp get() = DESIGN_WIDTH * (heightPx.toFloat() / widthPx)

    companion object {
        val DESIGN_WIDTH: Dp = 360.dp
    }
}

/** Whether a template is drawn on its own background or on nothing at all. */
enum class TemplateKind(val file: ImageFileFormat) {
    /** White text and line art on a transparent background, to sit on the user's own photo. Saved as PNG. */
    OVERLAY(ImageFileFormat.PNG),
    /** A full card with its own background. Saved as JPEG. */
    OPAQUE(ImageFileFormat.JPEG),
}

/**
 * One look for a piece of content. [draw] fills the whole design canvas (see [ShareFormat]) and is the
 * picture itself: the studio records exactly what it draws. Overlays must leave the background empty.
 */
@Immutable
class ShareTemplate(
    /** Stable within its content, e.g. "classic"; used for the saved file's name. */
    val key: String,
    val name: StringResource,
    val kind: TemplateKind,
    /** Formats this template can be drawn in; the studio hides the format switch when there's one. */
    val formats: List<ShareFormat> = ShareFormat.entries,
    val draw: @Composable (ShareFormat) -> Unit,
)

/**
 * Something that can be shared as a picture: a batting innings, a bowling spell, a result, a season, and
 * (with posters) fixtures, tables and auction sales. Each offers the [templates] that suit it.
 *
 * To add a kind of content, implement this (usually as a small class holding the data) and pass an
 * instance to [ShareStudio]. Opaque cards can reuse [summaryTemplates] by describing the content as a
 * [ShareSummary]; anything else is a [ShareTemplate] with its own draw function.
 */
@Immutable
interface ShareContent {
    /** Stable and unique among the contents offered together, e.g. "batting-1-a0". */
    val key: String

    /** Templates, best first; the first is shown when the studio opens. */
    val templates: List<ShareTemplate>

    /** A short name for the content picker when the studio offers more than one ("Rohan's innings"). */
    @Composable
    fun label(): String
}

/**
 * Content described the way the generic opaque templates (Classic, Broadcast, Minimal) draw it: a name, a
 * few big numbers, small stats, some lines of text and a caption naming the match or season.
 */
@Immutable
data class ShareSummary(
    /** A player's name, or null when the content is a match. */
    val title: String?,
    /** The big numbers: a team's total, a batter's runs, a bowler's figures. */
    val headlines: List<ShareHeadline>,
    val stats: List<ShareStat> = emptyList(),
    /** The result, how a batter got out, the wickets a bowler took. */
    val lines: List<String> = emptyList(),
    /** The match or season the picture is from, in small type at the foot. */
    val caption: String,
)

/** "Andheri Strikers · 52/3 · (5.0 ov)": what it is, the number, and the detail beside it. */
@Immutable
data class ShareHeadline(val label: String, val value: String, val detail: String? = null)

@Immutable
data class ShareStat(val value: String, val label: String)

/**
 * The theme a picture is drawn in. The studio applies it around every template, so a later theme picker
 * (Phase 8) only needs to offer more of these. [Default] is the Crease light theme, whatever the app is set
 * to, so a shared card looks the same from every phone.
 */
fun interface ShareLook {
    @Composable
    fun Apply(content: @Composable () -> Unit)

    companion object {
        val Default = ShareLook { content -> DesiTheme(darkTheme = false, reduceMotion = true, content = content) }
    }
}
