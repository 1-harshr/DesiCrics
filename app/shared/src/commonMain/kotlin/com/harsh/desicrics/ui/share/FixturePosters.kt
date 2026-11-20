package com.harsh.desicrics.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.stringResource

/** When and what: "16:30 · Semi-final", or just the stage before a time is set. */
private fun PosterFixture.whenLine(): String = listOfNotNull(time, stage.ifBlank { null }).joinToString(" · ")

/**
 * Matchday card: brand green with mown stripes, MATCHDAY in big type over the day in gold, and a card per
 * fixture with both teams' marks. Stories list the cards; posts lay them out two by two.
 */
@Composable
fun MatchdayCardPoster(poster: FixturesPoster, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize().background(colors.brandStrong)) {
        Stripes(onGreen)
        Column(Modifier.fillMaxSize().padding(if (post) 20.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (post) 10.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(poster.tournament, share.gold, modifier = Modifier.weight(1f))
                PageBadge(poster.page, share.onGold, share.gold)
            }
            Column {
                // The round when there is one ("MATCHDAY 2", "SEMI-FINAL"), and the day beneath in gold.
                Text(
                    (poster.round ?: stringResource(Res.string.poster_matchday)).uppercase(),
                    style = type.scoreXl.sized(if (post) 40 else 64),
                    color = onGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                poster.day?.let { Text(it.uppercase(), style = type.titleLg.sized(if (post) 20 else 28, if (post) 24 else 32), color = share.gold, maxLines = 1) }
            }
            if (post) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    poster.fixtures.chunked(2).forEach { pair ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { MatchdayTile(it, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (poster.fixtures.size <= 2) Spacer(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                    poster.fixtures.forEach { MatchdayRow(it) }
                }
            }
            PosterFooter(onGreen, caption = poster.city.ifBlank { null })
        }
    }
}

/** A story's fixture card: mark, name, VS, name, mark, and when below. */
@Composable
private fun MatchdayRow(fixture: PosterFixture) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    Column(
        Modifier.fillMaxWidth().clip(DesiTheme.radius.md).background(colors.ink.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosterAvatar(fixture.teamA, null, 40.dp, onGreen, colors.brandStrong)
            Text(fixture.teamA, style = type.titleMd.sized(16, 19), color = onGreen, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            VersusDisc(28)
            Text(fixture.teamB, style = type.titleMd.sized(16, 19), color = onGreen, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            PosterAvatar(fixture.teamB, null, 40.dp, onGreen, colors.brandStrong)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(fixture.whenLine().uppercase(), style = type.label.sized(12, 16), color = share.gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A post's fixture tile: each team on a line with its mark, then when. */
@Composable
private fun MatchdayTile(fixture: PosterFixture, modifier: Modifier) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    Column(
        modifier.fillMaxHeight().clip(DesiTheme.radius.md).background(colors.ink.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        listOf(fixture.teamA, fixture.teamB).forEach { team ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosterAvatar(team, null, 24.dp, onGreen, colors.brandStrong)
                Text(team, style = type.titleMd.sized(13, 16), color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(fixture.whenLine().uppercase(), style = type.label.sized(10, 13), color = share.gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "VS" on a gold disc. */
@Composable
private fun VersusDisc(size: Int) {
    val share = DesiTheme.colors.share
    Box(Modifier.size(size.dp).clip(DesiTheme.radius.full).background(share.gold), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.poster_vs).uppercase(), style = DesiTheme.type.label.sized((size * 0.4f).toInt(), (size * 0.5f).toInt()), color = share.onGold, maxLines = 1)
    }
}

/**
 * Broadcast list: the TV look of the result card. A gold scorebug with the tournament, FIXTURES and the day,
 * then a lower-third strip per fixture with its time in a gold box.
 */
@Composable
fun BroadcastListPoster(poster: FixturesPoster, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize().background(colors.brandStrong)) {
        Stripes(onGreen)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(top = if (post) 16.dp else 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(share.gold).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(stringResource(Res.string.app_name).uppercase(), style = type.label.sized(13, 16), color = share.onGold)
                }
                Row(Modifier.weight(1f).background(colors.ink.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(poster.tournament.uppercase(), style = type.label.sized(13, 16), color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    poster.page?.let { Text(stringResource(Res.string.share_page, it.number, it.count), style = type.label.sized(13, 16), color = share.gold) }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = if (post) 6.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (post) 0.dp else 4.dp)) {
                Text(stringResource(Res.string.poster_content_fixtures).uppercase(), style = type.scoreXl.sized(if (post) 34 else 56), color = onGreen, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(36.dp).height(4.dp).background(share.gold))
                    Text(
                        listOfNotNull(poster.day, poster.round).joinToString(" · ").uppercase(),
                        style = type.titleMd.sized(if (post) 16 else 20, 24),
                        color = share.gold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (post) 3.dp else 6.dp, Alignment.CenterVertically)) {
                poster.fixtures.forEach { BroadcastFixtureRow(it, post) }
            }
            PosterFooter(onGreen, caption = poster.city.ifBlank { null }, modifier = Modifier.padding(horizontal = 20.dp, vertical = if (post) 12.dp else 24.dp))
        }
    }
}

@Composable
private fun BroadcastFixtureRow(fixture: PosterFixture, post: Boolean) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val nameStyle = type.titleMd.sized(if (post) 14 else 17, if (post) 17 else 21)
    Row(Modifier.fillMaxWidth().height(if (post) 28.dp else 54.dp).background(colors.ink.copy(alpha = 0.55f)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(if (post) 64.dp else 76.dp).fillMaxHeight().background(share.gold), contentAlignment = Alignment.Center) {
            Text(fixture.time ?: stringResource(Res.string.poster_time_tbc).uppercase(), style = type.scoreMd.sized(if (post) 14 else 20), color = share.onGold, maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fixture.teamA.uppercase(), style = nameStyle, color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(stringResource(Res.string.poster_vs_short).uppercase(), style = type.label.sized(12, 16), color = share.gold, modifier = Modifier.padding(horizontal = 8.dp))
                Text(fixture.teamB.uppercase(), style = nameStyle, color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            if (!post) {
                Text(
                    listOfNotNull(fixture.stage, fixture.venue.ifBlank { null }).joinToString(" · ").uppercase(),
                    style = type.label.sized(11, 14),
                    color = onGreen.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Minimal list: paper, a big quiet title, and each fixture on its own line with the time (and in stories
 * the stage) in the margin.
 */
@Composable
fun MinimalListPoster(poster: FixturesPoster, format: ShareFormat) {
    val share = DesiTheme.colors.share
    val type = DesiTheme.type
    val ink = share.paperInk
    val quiet = ink.copy(alpha = 0.6f)
    val post = format == ShareFormat.POST
    val heading = listOfNotNull(poster.day, poster.round).joinToString(" · ")
    Column(
        Modifier.fillMaxSize().background(share.paper).padding(horizontal = if (post) 28.dp else 32.dp, vertical = if (post) 24.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(if (post) 8.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(poster.tournament, quiet, modifier = Modifier.weight(1f))
            poster.page?.let { Text(stringResource(Res.string.share_page, it.number, it.count), style = type.label, color = quiet) }
        }
        if (post) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.poster_content_fixtures), style = type.titleLg.sized(30, 34), color = ink)
                Text(heading, style = type.bodyStrong.sized(14, 22), color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 2.dp))
            }
        } else {
            Column {
                Text(stringResource(Res.string.poster_content_fixtures), style = type.titleLg.sized(46, 50), color = ink)
                Text(heading, style = type.bodyStrong.sized(18, 24), color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            poster.fixtures.forEachIndexed { i, fixture ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.15f)))
                Row(Modifier.padding(vertical = if (post) 5.dp else 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.width(if (post) 50.dp else 72.dp)) {
                        Text(fixture.time ?: "", style = type.data.sized(if (post) 13 else 15, 22), color = quiet)
                        if (!post) Text(fixture.stage, style = type.body.sized(12, 16), color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    MinimalTeams(fixture, if (post) 15 else 17, lines = if (post) 1 else 2)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.2f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.app_name), style = type.titleMd, color = ink, modifier = Modifier.weight(1f))
            if (poster.city.isNotBlank()) Text(poster.city, style = type.body, color = quiet, maxLines = 1)
        }
    }
}

/** "Andheri Strikers v Powai XI" with a quiet v, wrapping onto a second line in stories. */
@Composable
private fun MinimalTeams(fixture: PosterFixture, size: Int, lines: Int) {
    val ink = DesiTheme.colors.share.paperInk
    val v = stringResource(Res.string.poster_vs_short)
    Text(
        buildAnnotatedString {
            append(fixture.teamA)
            withStyle(SpanStyle(color = ink.copy(alpha = 0.5f), fontWeight = FontWeight.Normal)) { append(" $v ") }
            append(fixture.teamB)
        },
        style = DesiTheme.type.bodyStrong.sized(size, size + 5),
        color = ink,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Versus tiles: each fixture as a split tile, brand green against dark, both teams' initials large and a
 * gold VS where they meet. Stories stack the tiles; posts lay them out two by two.
 */
@Composable
fun VersusTilesPoster(poster: FixturesPoster, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Column(
        Modifier.fillMaxSize().background(colors.ink).padding(if (post) 16.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (post) 8.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow(poster.tournament, share.gold, modifier = Modifier.weight(1f))
            PageBadge(poster.page, share.onGold, share.gold)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                (poster.round ?: stringResource(Res.string.poster_matchday)).uppercase(),
                style = type.scoreXl.sized(if (post) 28 else 40, if (post) 30 else 42),
                color = onGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            poster.day?.let {
                Text(it.uppercase(), style = type.titleMd.sized(if (post) 16 else 20, 24), color = share.gold, maxLines = 1, modifier = Modifier.padding(bottom = if (post) 2.dp else 4.dp))
            }
        }
        if (post) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                poster.fixtures.chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { VersusTile(it, small = true, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                // Keep a lone row of tiles the same height as a full grid's.
                if (poster.fixtures.size <= 2) Spacer(Modifier.weight(1f))
            }
        } else {
            // Tiles keep a full page's size; a short page sits in the middle.
            val missing = FixturesShare.VERSUS_PER_PAGE - poster.fixtures.size
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (missing > 0) Spacer(Modifier.weight(missing / 2f))
                poster.fixtures.forEach { VersusTile(it, small = false, Modifier.weight(1f)) }
                if (missing > 0) Spacer(Modifier.weight(missing / 2f))
            }
        }
        PosterFooter(onGreen, caption = poster.city.ifBlank { null })
    }
}

@Composable
private fun VersusTile(fixture: PosterFixture, small: Boolean, modifier: Modifier) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    Column(modifier.clip(DesiTheme.radius.md)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Row(Modifier.fillMaxSize()) {
                VersusHalf(fixture.teamA, small, Modifier.weight(1f).background(colors.brandStrong))
                VersusHalf(fixture.teamB, small, Modifier.weight(1f).background(onGreen.copy(alpha = 0.08f)))
            }
            Box(Modifier.align(Alignment.Center)) { VersusDisc(if (small) 26 else 36) }
        }
        Text(
            fixture.whenLine().uppercase(),
            style = type.label.sized(if (small) 10 else 12, if (small) 13 else 16),
            color = share.onGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(share.gold).padding(vertical = if (small) 3.dp else 5.dp, horizontal = 8.dp),
        )
    }
}

@Composable
private fun RowScope.VersusHalf(name: String, small: Boolean, modifier: Modifier) {
    val colors = DesiTheme.colors
    val type = DesiTheme.type
    val onGreen = colors.onBrandStrong
    Column(
        modifier.fillMaxHeight().padding(horizontal = if (small) 8.dp else 14.dp, vertical = if (small) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(initialsOf(name), style = type.scoreXl.sized(if (small) 34 else 42), color = onGreen, maxLines = 1)
        Text(
            name.uppercase(),
            style = type.titleMd.sized(if (small) 11 else 14, if (small) 13 else 17),
            color = onGreen.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
