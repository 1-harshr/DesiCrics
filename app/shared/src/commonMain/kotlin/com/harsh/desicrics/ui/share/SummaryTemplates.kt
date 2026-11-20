package com.harsh.desicrics.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIllustrations
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The three opaque looks every [ShareSummary] can be drawn in: Classic (the original green card),
 * Broadcast (a TV scorebug in green and gold) and Minimal (big numerals on paper).
 */
fun summaryTemplates(summary: @Composable () -> ShareSummary): List<ShareTemplate> = listOf(
    ShareTemplate("classic", Res.string.share_template_classic, TemplateKind.OPAQUE) { ClassicTemplate(summary(), it) },
    ShareTemplate("broadcast", Res.string.share_template_broadcast, TemplateKind.OPAQUE) { BroadcastTemplate(summary(), it) },
    ShareTemplate("minimal", Res.string.share_template_minimal, TemplateKind.OPAQUE) { MinimalTemplate(summary(), it) },
)

/** The original card: the ball mark, each big number on brand green, then the lines and a footer. */
@Composable
fun ClassicTemplate(summary: ShareSummary, format: ShareFormat) {
    val colors = DesiTheme.colors
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Column(
        Modifier.fillMaxSize().background(colors.brandStrong).padding(if (post) 24.dp else 28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(DesiIllustrations.BallMark), contentDescription = null, modifier = Modifier.size(if (post) 40.dp else 64.dp))
            if (post) summary.title?.let { Text(it, style = type.titleLg, color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (post) 8.dp else 18.dp)) {
            if (!post) summary.title?.let { Text(it, style = type.titleLg.sized(44, 48), color = onGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            summary.headlines.forEach { line ->
                Column {
                    if (line.label.isNotEmpty()) {
                        Text(line.label, style = type.titleMd, color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(line.value, style = type.scoreXl.sized(if (post) 52 else 68), color = onGreen)
                        line.detail?.let {
                            Text(it, style = type.data.sized(15, 20), color = onGreen.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                }
            }
            if (summary.stats.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    summary.stats.forEach { stat ->
                        Column {
                            Text(stat.value, style = type.scoreMd, color = onGreen)
                            Text(stat.label.uppercase(), style = type.label, color = onGreen.copy(alpha = 0.8f))
                        }
                    }
                }
            }
            summary.lines.take(if (post) 1 else 6).forEach { Text(it, style = type.bodyStrong.sized(16, 22), color = onGreen) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!post) Text(summary.caption, style = type.caption, color = onGreen.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.share_card_footer).uppercase(), style = type.label, color = onGreen.copy(alpha = 0.8f))
        }
    }
}

/**
 * A TV look: turf stripes on brand green, a gold scorebug at the top with the match name, and a
 * lower third of score rows with a gold result strip.
 */
@Composable
fun BroadcastTemplate(summary: ShareSummary, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize().background(colors.brandStrong)) {
        // Mown stripes, like a ground on TV.
        Canvas(Modifier.fillMaxSize()) {
            val band = size.width / 6f
            for (i in 0 until 6 step 2) drawRect(onGreen.copy(alpha = 0.05f), Offset(i * band, 0f), Size(band, size.height))
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            // The scorebug.
            Row(Modifier.padding(top = if (post) 20.dp else 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(share.gold).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(Res.string.app_name).uppercase(), style = type.label.sized(13, 16), color = share.onGold)
                }
                Box(Modifier.weight(1f).background(colors.ink.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(summary.caption.uppercase(), style = type.label.sized(13, 16), color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.title?.let {
                    Text(it.uppercase(), style = type.titleLg.sized(if (post) 36 else 48, if (post) 40 else 52), color = onGreen, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.width(56.dp).height(4.dp).background(share.gold))
                }
            }
            // The lower third.
            Column(Modifier.fillMaxWidth().padding(bottom = if (post) 20.dp else 40.dp)) {
                summary.headlines.forEach { line ->
                    Row(
                        Modifier.fillMaxWidth().background(colors.ink.copy(alpha = 0.55f)).padding(horizontal = 20.dp, vertical = if (post) 6.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(4.dp).height(32.dp).background(share.gold))
                        Spacer(Modifier.width(12.dp))
                        Text(line.label.uppercase(), style = type.titleMd, color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        line.detail?.let { Text(it, style = type.data.sized(14, 18), color = onGreen.copy(alpha = 0.8f), modifier = Modifier.padding(end = 10.dp)) }
                        Text(line.value, style = type.scoreLg.sized(if (post) 36 else 44), color = share.gold)
                    }
                }
                if (summary.stats.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().background(colors.ink.copy(alpha = 0.35f)).padding(horizontal = 20.dp, vertical = 8.dp)) {
                        summary.stats.forEach { stat ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stat.label.uppercase(), style = type.label, color = onGreen.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))
                                Text(stat.value, style = type.scoreMd, color = onGreen)
                            }
                        }
                    }
                }
                summary.lines.firstOrNull()?.let {
                    Box(Modifier.fillMaxWidth().background(share.gold).padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(it.uppercase(), style = type.bodyStrong.sized(15, 20), color = share.onGold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!post) {
                    summary.lines.drop(1).take(5).forEach {
                        Text(
                            it,
                            style = type.body,
                            color = onGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().background(colors.ink.copy(alpha = 0.35f)).padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Off-white paper, one huge numeral, and everything else small and quiet. */
@Composable
fun MinimalTemplate(summary: ShareSummary, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val ink = share.paperInk
    val quiet = ink.copy(alpha = 0.6f)
    val post = format == ShareFormat.POST
    val lead = summary.headlines.firstOrNull()
    Column(
        Modifier.fillMaxSize().background(share.paper).padding(if (post) 28.dp else 36.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(summary.caption.uppercase(), style = type.label, color = quiet, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Column(verticalArrangement = Arrangement.spacedBy(if (post) 4.dp else 10.dp)) {
            lead?.let {
                if (it.label.isNotEmpty()) Text(it.label, style = type.bodyStrong.sized(16, 22), color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(it.value, style = type.scoreXl.sized(if (post) 104 else 140), color = ink, maxLines = 1)
                it.detail?.let { detail -> Text(detail, style = type.data.sized(16, 22), color = quiet) }
            }
            summary.title?.let { Text(it, style = type.titleLg.sized(if (post) 30 else 40, if (post) 34 else 44), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            summary.headlines.drop(1).forEach { line ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.value, style = type.scoreLg, color = ink)
                    Text(listOfNotNull(line.label.takeIf { it.isNotEmpty() }, line.detail).joinToString(" "), style = type.body, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(if (post) 8.dp else 14.dp)) {
            if (summary.stats.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    summary.stats.forEach { stat ->
                        Column {
                            Text(stat.label.uppercase(), style = type.label, color = quiet)
                            Text(stat.value, style = type.scoreMd, color = ink)
                        }
                    }
                }
            }
            summary.lines.take(if (post) 1 else 5).forEach { Text(it, style = type.body, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.2f)))
            Text(stringResource(Res.string.app_name), style = type.titleMd, color = ink)
        }
    }
}
