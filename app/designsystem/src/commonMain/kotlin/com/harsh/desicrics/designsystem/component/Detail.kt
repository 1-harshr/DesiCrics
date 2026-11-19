package com.harsh.desicrics.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.ds_back
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val BarHeight = 56.dp
private val CollapseAfter = 40.dp
private val HeroShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

/**
 * The frame of an inside page about one thing (a team, a player, a tournament): a green [hero] with the
 * page's identity, then [content] on the page colour. The back button and [actions] float over the hero;
 * once it scrolls away they sit on a compact green bar with [title].
 */
@Composable
fun HeroScaffold(
    title: String,
    onBack: (() -> Unit)?,
    hero: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // The bar turns solid, with the title, as soon as the name starts to slide under it. Short pages
    // can't scroll the whole hero away, so waiting for that would leave content under the status bar.
    val collapseAt = with(density) { CollapseAfter.roundToPx() }
    val collapsed by remember { derivedStateOf { scroll.value > collapseAt } }
    val barColor by animateColorAsState(if (collapsed) colors.brandStrong else Color.Transparent, tween(DesiTheme.motion.BASE_MS))

    Column(modifier.fillMaxSize().background(colors.bg)) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(HeroShape)
                        .heroBackground()
                        .statusBarsPadding()
                        .padding(top = BarHeight)
                        .padding(horizontal = space.screen)
                        .padding(bottom = space.s6),
                    verticalArrangement = Arrangement.spacedBy(space.s4),
                    content = hero,
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(horizontal = space.screen, vertical = space.s5),
                    verticalArrangement = Arrangement.spacedBy(space.s5),
                    content = content,
                )
            }
            Row(
                Modifier.fillMaxWidth().background(barColor).statusBarsPadding().height(BarHeight).padding(horizontal = space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.s2),
            ) {
                if (onBack != null) HeroIconButton(DesiIcons.Back, stringResource(Res.string.ds_back), onBack)
                Box(Modifier.weight(1f).padding(start = if (onBack == null) space.s2 else 0.dp)) {
                    androidx.compose.animation.AnimatedVisibility(collapsed, enter = fadeIn(), exit = fadeOut()) {
                        Text(title, style = DesiTheme.type.titleMd, color = colors.onBrandStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                actions()
            }
        }
        bottomBar()
    }
}

/** The hero's deep green, lighter towards the bottom, with a faint pitch circle in the top corner. */
@Composable
fun Modifier.heroBackground(): Modifier {
    val colors = DesiTheme.colors
    val top = colors.brandStrong
    val bottom = lerp(colors.brandStrong, colors.brand, if (colors.isDark) 0.25f else 0.45f)
    val ring = colors.onBrandStrong.copy(alpha = 0.07f)
    return this
        .background(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)))
        .drawBehind {
            val centre = Offset(size.width * 0.92f, size.height * 0.05f)
            drawCircle(ring, radius = size.width * 0.42f, center = centre, style = Stroke(width = 2.dp.toPx()))
            drawCircle(ring, radius = size.width * 0.28f, center = centre, style = Stroke(width = 2.dp.toPx()))
            drawCircle(ring.copy(alpha = 0.05f), radius = size.width * 0.14f, center = centre)
        }
}

/** A round, see-through button for the hero and its bar (back, share, settings). */
@Composable
fun HeroIconButton(icon: DrawableResource, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    Box(
        modifier
            .size(40.dp)
            .clip(DesiTheme.radius.full)
            .background(colors.onBrandStrong.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, tint = colors.onBrandStrong, modifier = Modifier.size(DesiTheme.size.iconSmall))
    }
}

/**
 * Who or what the page is about: a large [avatar] in a white ring, the [name], and a [subtitle] line.
 * [eyebrow] is a small uppercase line above the name ("TEAM", "TOURNAMENT").
 */
@Composable
fun HeroIdentity(
    name: String,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
) {
    val colors = DesiTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
        Box(Modifier.clip(DesiTheme.radius.full).border(3.dp, colors.onBrandStrong.copy(alpha = 0.9f), DesiTheme.radius.full).padding(3.dp)) {
            avatar()
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (eyebrow != null) Text(eyebrow.uppercase(), style = DesiTheme.type.label, color = colors.onBrandStrong.copy(alpha = 0.7f))
            Text(name, style = DesiTheme.type.titleLg, color = colors.onBrandStrong, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = DesiTheme.type.body, color = colors.onBrandStrong.copy(alpha = 0.8f))
        }
    }
}

@Immutable
data class HeroChip(val text: String, val icon: DrawableResource? = null)

/** Small facts about the page as frosted pills: city, format, role. Blank texts are skipped. */
@Composable
fun HeroChips(chips: List<HeroChip>, modifier: Modifier = Modifier) {
    val visible = chips.filter { it.text.isNotBlank() }
    if (visible.isEmpty()) return
    val colors = DesiTheme.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        visible.forEach { chip ->
            Row(
                Modifier.clip(DesiTheme.radius.full).background(colors.onBrandStrong.copy(alpha = 0.12f)).padding(horizontal = DesiTheme.space.s3, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (chip.icon != null) Icon(painterResource(chip.icon), null, tint = colors.onBrandStrong, modifier = Modifier.size(16.dp))
                Text(chip.text, style = DesiTheme.type.caption, color = colors.onBrandStrong, maxLines = 1)
            }
        }
    }
}

@Immutable
data class StatItem(val value: String, val label: String)

/** Two to four headline numbers in a frosted strip at the bottom of the hero, split by hairlines. */
@Composable
fun HeroStats(stats: List<StatItem>, modifier: Modifier = Modifier) {
    if (stats.isEmpty()) return
    val colors = DesiTheme.colors
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(DesiTheme.radius.md)
            .background(colors.onBrandStrong.copy(alpha = 0.1f)).padding(vertical = DesiTheme.space.s3),
    ) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.onBrandStrong.copy(alpha = 0.18f)))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stat.value, style = DesiTheme.type.scoreMd, color = colors.onBrandStrong, maxLines = 1)
                Text(stat.label, style = DesiTheme.type.caption, color = colors.onBrandStrong.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * An action on the hero. [filled] is white with green text, for the page's main action (Follow, Add a
 * player); otherwise it's frosted, for a second one.
 */
@Composable
fun HeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: DrawableResource? = null, filled: Boolean = true) {
    val colors = DesiTheme.colors
    val container = if (filled) colors.onBrandStrong else colors.onBrandStrong.copy(alpha = 0.14f)
    val content = if (filled) colors.brandStrong else colors.onBrandStrong
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(DesiTheme.radius.full)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DesiTheme.space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(DesiTheme.size.iconSmall))
        Text(text, style = DesiTheme.type.button, color = content, maxLines = 1)
    }
}

/** A heading for a block of content: an optional brand icon, the title, and an optional link on the right. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = DesiTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        if (icon != null) Icon(painterResource(icon), null, tint = colors.brand, modifier = Modifier.size(DesiTheme.size.iconSmall))
        Text(
            title,
            style = DesiTheme.type.bodyLg.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null && onAction != null) {
            Row(
                Modifier.clip(DesiTheme.radius.full).clickable(role = Role.Button, onClick = onAction).padding(horizontal = DesiTheme.space.s2, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = DesiTheme.type.button, color = colors.brand)
                Icon(painterResource(DesiIcons.ChevronRight), null, tint = colors.brand, modifier = Modifier.size(DesiTheme.size.iconSmall))
            }
        }
    }
}

/** An icon on a soft rounded square, the leading mark of rows and tiles. */
@Composable
fun IconTile(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = DesiTheme.colors.brand,
    container: Color = DesiTheme.colors.brandSoft,
) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(container), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A square-ish shortcut with an icon over a label, laid out several to a row. */
@Composable
fun ActionTile(icon: DrawableResource, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    Column(
        modifier
            .clip(DesiTheme.radius.md)
            .background(colors.surface)
            .border(1.dp, colors.line.copy(alpha = 0.7f), DesiTheme.radius.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DesiTheme.space.s2, vertical = DesiTheme.space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        IconTile(icon, size = 44.dp)
        Text(label, style = DesiTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.ink, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** A grouped list on one surface. Put [ListRow]s inside; they draw their own separators. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = DesiTheme.colors
    Column(
        modifier.fillMaxWidth().clip(DesiTheme.radius.md).background(colors.surface).border(1.dp, colors.line.copy(alpha = 0.7f), DesiTheme.radius.md),
        content = content,
    )
}

/**
 * One row of a [ListCard]: a [leading] mark (an [Avatar] or [IconTile]), the title and a subtitle, then
 * [trailing] content. Tappable rows end in a chevron unless [trailing] says otherwise. [divider] draws a
 * hairline above the row, inset past the leading mark.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    divider: Boolean = false,
    titleColor: Color = DesiTheme.colors.ink,
    onClick: (() -> Unit)? = null,
) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    Column(modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(Modifier.padding(start = if (leading != null) 68.dp else space.s4), color = colors.line.copy(alpha = 0.7f))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = DesiTheme.size.listRowWithAvatar - 8.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = space.s4, vertical = space.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = DesiTheme.type.bodyStrong, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = DesiTheme.type.caption, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(painterResource(DesiIcons.ChevronRight), null, tint = colors.muted, modifier = Modifier.size(DesiTheme.size.iconSmall))
            }
        }
    }
}

enum class TagTone { BRAND, NEUTRAL, WARN, DANGER }

/** A small status pill: Admin, Guest, Pending, Captain. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, tone: TagTone = TagTone.NEUTRAL, icon: DrawableResource? = null) {
    val colors = DesiTheme.colors
    val (container, content) = when (tone) {
        TagTone.BRAND -> colors.brandSoft to colors.brand
        TagTone.NEUTRAL -> colors.sunk to colors.muted
        TagTone.WARN -> colors.event.extra.copy(alpha = 0.14f) to colors.event.extra
        TagTone.DANGER -> colors.event.wicket.copy(alpha = 0.12f) to colors.event.wicket
    }
    Row(
        modifier.clip(DesiTheme.radius.full).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, style = DesiTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = content, maxLines = 1)
    }
}

/**
 * Figures as tiles, [columns] to a row. The first [featured] tiles are drawn on the brand tint, for the
 * numbers that matter most (runs, average, strike rate).
 */
@Composable
fun StatGrid(stats: List<StatItem>, modifier: Modifier = Modifier, columns: Int = 3, featured: Int = 0) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.s2)) {
        stats.withIndex().chunked(columns).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                row.forEach { (index, stat) ->
                    val highlight = index < featured
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(DesiTheme.radius.md)
                            .background(if (highlight) colors.brandSoft else colors.surface)
                            .border(1.dp, if (highlight) Color.Transparent else colors.line.copy(alpha = 0.7f), DesiTheme.radius.md)
                            .padding(horizontal = space.s3, vertical = space.s3),
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                    ) {
                        Text(stat.value, style = if (highlight) DesiTheme.type.scoreLg.copy(fontSize = 32.sp, lineHeight = 36.sp) else DesiTheme.type.scoreMd, color = if (highlight) colors.brand else colors.ink, maxLines = 1)
                        Text(stat.label, style = DesiTheme.type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** What an empty list looks like: an icon, a line saying why, and what to do about it. */
@Composable
fun EmptyState(
    icon: DrawableResource,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = DesiTheme.colors
    Column(
        modifier.fillMaxWidth().clip(DesiTheme.radius.md).background(colors.surface)
            .border(1.dp, colors.line.copy(alpha = 0.7f), DesiTheme.radius.md).padding(DesiTheme.space.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        IconTile(icon, size = 56.dp)
        Text(title, style = DesiTheme.type.bodyStrong, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = DesiTheme.space.s2))
        if (body != null) Text(body, style = DesiTheme.type.body, color = colors.muted, textAlign = TextAlign.Center)
        if (action != null) Box(Modifier.padding(top = DesiTheme.space.s2)) { action() }
    }
}

/** A position in a ranking. The top spot is filled, the next two tinted, the rest quiet. */
@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val colors = DesiTheme.colors
    val (container, content) = when (rank) {
        1 -> colors.brand to colors.onBrand
        2, 3 -> colors.brandSoft to colors.brand
        else -> colors.sunk to colors.muted
    }
    Box(modifier.size(size).clip(DesiTheme.radius.full).background(container), contentAlignment = Alignment.Center) {
        Text("$rank", style = DesiTheme.type.bodyStrong, color = content)
    }
}
