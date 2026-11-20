package com.harsh.desicrics.ui.share

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.system.ImageFileFormat
import com.harsh.desicrics.ui.system.rememberCopyImage
import com.harsh.desicrics.ui.system.rememberSaveImage
import com.harsh.desicrics.ui.system.rememberShareImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The share studio: one sheet for every picture DesiCrics makes. The user picks what to share from
 * [contents] (when there's more than one), swipes through its templates as live previews, picks story or
 * post, then shares the picture, copies it, or saves it to the gallery. The picture is recorded from the preview
 * itself at full size, so it's exactly what was shown. A template with several pages shows a page picker,
 * and Share and Save take every page together; Copy takes the page on show.
 *
 * [initial] is the key of the content to open on; [look] is the theme pictures are drawn in.
 */
@Composable
fun ShareStudio(
    contents: List<ShareContent>,
    onDismiss: () -> Unit,
    title: String = stringResource(Res.string.share_studio_title),
    initial: String? = null,
    look: ShareLook = ShareLook.Default,
) {
    val shareImage = rememberShareImage()
    val saveImage = rememberSaveImage()
    val copyImage = rememberCopyImage()
    DesiSheet(title, onDismiss) {
        ShareStudioContent(
            contents,
            initial = initial,
            look = look,
            onShare = { images, format, _ -> shareImage(images, format) },
            onSave = { image, format, name -> saveImage(image, format, name) },
            onCopy = { image, format -> copyImage(image, format) },
        )
    }
}

/** What the studio last did, for the line under its buttons. */
private enum class StudioNote(val text: StringResource, val failed: Boolean) {
    SAVED(Res.string.share_saved, false),
    SAVE_FAILED(Res.string.share_save_failed, true),
    COPIED(Res.string.share_copied, false),
    COPY_FAILED(Res.string.share_copy_failed, true),
}

/**
 * The studio without its sheet or the platform's share and save, so tests can draw it. [onShare] gets
 * every page of the picture (usually one), its file format and a file name; [onSave] is called once per
 * page, with "-1", "-2"… on the name when there's more than one; [onCopy] gets the page on show.
 */
@Composable
fun ShareStudioContent(
    contents: List<ShareContent>,
    initial: String? = null,
    look: ShareLook = ShareLook.Default,
    onShare: suspend (List<ImageBitmap>, ImageFileFormat, String) -> Unit,
    onSave: suspend (ImageBitmap, ImageFileFormat, String) -> Boolean,
    onCopy: suspend (ImageBitmap, ImageFileFormat) -> Boolean,
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    var contentKey by rememberSaveable { mutableStateOf(initial ?: contents.first().key) }
    val content = contents.firstOrNull { it.key == contentKey } ?: contents.first()
    var format by rememberSaveable { mutableStateOf(ShareFormat.STORY) }
    var onPhoto by rememberSaveable { mutableStateOf(false) }
    var note by remember { mutableStateOf<StudioNote?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(space.s4)) {
        if (contents.size > 1) {
            // One line that scrolls sideways, however many players there are.
            ChoiceChips(
                options = contents,
                selected = content,
                label = { it.label() },
                onSelect = { contentKey = it.key; note = null },
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
        // A fresh pager for each content, starting on its first template.
        key(content.key) {
            val pager = rememberPagerState { content.templates.size }
            val template = content.templates[pager.currentPage.coerceIn(content.templates.indices)]
            val shown = if (format in template.formats) format else template.formats.first()
            // Every page of the template in the middle is recorded, so Share and Save send them all.
            val pageCount = content.templates[pager.settledPage.coerceIn(content.templates.indices)].pages(shown)
            var picked by remember(pager.settledPage, shown) { mutableStateOf(0) }
            val shownPage = picked.coerceIn(0, pageCount - 1)
            val layers = List(pageCount) { key(it) { rememberGraphicsLayer() } }
            if (template.formats.size > 1) {
                SegmentedControl(
                    options = template.formats,
                    selected = shown,
                    label = { stringResource(it.label) },
                    onSelect = { format = it },
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Stories are tall, so they're shown narrower; the next template peeks in at the sides.
                val page = if (shown == ShareFormat.STORY) minOf(maxWidth * 0.62f, 300.dp) else minOf(maxWidth * 0.78f, 360.dp)
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = (maxWidth - page) / 2),
                    pageSpacing = space.s3,
                    verticalAlignment = Alignment.Top,
                ) { index ->
                    val each = content.templates[index]
                    val eachFormat = if (shown in each.formats) shown else each.formats.first()
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(space.s2)) {
                        TemplatePreview(
                            each,
                            eachFormat,
                            look,
                            onPhoto = onPhoto,
                            page = if (index == pager.settledPage) shownPage else 0,
                            // Only the template in the middle is recorded; that's the one Share sends.
                            layers = if (index == pager.settledPage) layers else emptyList(),
                        )
                        Text(
                            stringResource(each.name),
                            style = if (index == pager.currentPage) DesiTheme.type.bodyStrong else DesiTheme.type.body,
                            color = if (index == pager.currentPage) colors.ink else colors.muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (pageCount > 1) PagePicker(pageCount, shownPage, onSelect = { picked = it })
            if (template.kind == TemplateKind.OVERLAY) OverlayNote(onPhoto, onToggle = { onPhoto = it })
            val fileName = "desicrics-${content.key}-${template.key}"
            // Three buttons don't fit a small phone side by side, so Share, the main one, gets its own row.
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    TonalButton(
                        stringResource(Res.string.share_save),
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val saved = layers.mapIndexed { i, layer ->
                                        val image = withContext(Dispatchers.Default) { layer.toImageBitmap() }
                                        onSave(image, template.kind.file, if (layers.size > 1) "$fileName-${i + 1}" else fileName)
                                    }
                                    note = if (saved.all { it }) StudioNote.SAVED else StudioNote.SAVE_FAILED
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        leadingIcon = DesiIcons.Download,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                    TonalButton(
                        stringResource(Res.string.share_copy),
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val image = withContext(Dispatchers.Default) { layers[shownPage].toImageBitmap() }
                                    val copied = onCopy(image, template.kind.file)
                                    note = if (copied) StudioNote.COPIED else StudioNote.COPY_FAILED
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        leadingIcon = DesiIcons.Copy,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    stringResource(Res.string.share_action),
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val images = withContext(Dispatchers.Default) { layers.map { it.toImageBitmap() } }
                                onShare(images, template.kind.file, fileName)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    leadingIcon = DesiIcons.Share,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        note?.let {
            Text(
                stringResource(it.text),
                style = DesiTheme.type.caption,
                color = if (it.failed) colors.event.wicket else colors.muted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One [page] of a template at [format], with rounded corners. Overlays sit on a checkerboard (or the
 * sample photo) so their transparency shows; that backdrop is outside what's recorded. Each of [layers]
 * records the page with its index at the format's full pixel size; pages other than [page] are recorded
 * without being shown.
 */
@Composable
fun TemplatePreview(
    template: ShareTemplate,
    format: ShareFormat,
    look: ShareLook = ShareLook.Default,
    onPhoto: Boolean = false,
    page: Int = 0,
    layers: List<GraphicsLayer> = emptyList(),
) {
    val colors = DesiTheme.colors
    val description = stringResource(template.name)
    Box(
        Modifier.fillMaxWidth().aspectRatio(format.aspectRatio).clip(DesiTheme.radius.md)
            .border(1.dp, colors.line, DesiTheme.radius.md)
            .semantics { contentDescription = description },
    ) {
        if (template.kind == TemplateKind.OVERLAY) {
            if (onPhoto) SamplePhoto() else Checkerboard()
        }
        layers.forEachIndexed { i, layer ->
            if (i != page) {
                Box(Modifier.fillMaxSize().recordInto(layer, format, show = false)) {
                    look.Apply { ShareCanvas(format) { template.drawPage(format, i) } }
                }
            }
        }
        val shownLayer = layers.getOrNull(page)
        Box(Modifier.fillMaxSize().then(if (shownLayer != null) Modifier.recordInto(shownLayer, format) else Modifier)) {
            look.Apply { ShareCanvas(format) { template.drawPage(format, page) } }
        }
    }
}

/**
 * Records the content into [layer] scaled up to the format's full size, and draws it as usual unless
 * [show] is false (a page that's shared but not the one on screen).
 */
private fun Modifier.recordInto(layer: GraphicsLayer, format: ShareFormat, show: Boolean = true) = drawWithContent {
    val up = format.widthPx / size.width
    layer.record(IntSize(format.widthPx, format.heightPx)) {
        scale(up, up, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
    }
    if (show) scale(1f / up, 1f / up, pivot = Offset.Zero) { drawLayer(layer) }
}

/** "1/3 2/3 3/3": which page of a long poster is previewed, and a note that they're shared together. */
@Composable
private fun PagePicker(count: Int, page: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        ChoiceChips(
            options = (0 until count).toList(),
            selected = page,
            label = { stringResource(Res.string.share_page, it + 1, count) },
            onSelect = onSelect,
        )
        Text(stringResource(Res.string.share_pages_note, count), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
    }
}

/** Where overlays work best, and a switch to preview one on a photo rather than the checkerboard. */
@Composable
private fun OverlayNote(onPhoto: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = DesiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            Icon(painterResource(DesiIcons.Info), null, tint = colors.muted, modifier = Modifier.size(DesiTheme.size.iconSmall))
            Text(stringResource(Res.string.share_overlay_note), style = DesiTheme.type.caption, color = colors.muted)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.share_preview_on_photo), style = DesiTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
            Switch(
                checked = onPhoto,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.brand, checkedThumbColor = colors.onBrand),
            )
        }
    }
}
