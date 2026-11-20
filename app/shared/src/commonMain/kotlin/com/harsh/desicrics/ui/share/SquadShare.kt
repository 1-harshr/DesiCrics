package com.harsh.desicrics.ui.share

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A team announcing its squad, from the team screen: everyone on the team, with logo and photos where they exist. */
@Immutable
data class SquadFigures(
    val team: String,
    val logoUrl: String?,
    val city: String,
    val players: List<PlayerSummary>,
)

/** A team's squad as a numbered team sheet or a grid of faces, split into pages for big squads. */
class SquadShare(private val squad: SquadFigures) : ShareContent {
    constructor(team: SavedTeam) : this(SquadFigures(team.name, team.logoUrl, team.city, team.members.map { it.player }))

    override val key = KEY

    @Composable
    override fun label() = stringResource(Res.string.share_content_squad)

    private fun sheetPages(format: ShareFormat) = squad.players.chunked(if (format == ShareFormat.POST) SHEET_PER_POST else SHEET_PER_STORY).ifEmpty { listOf(emptyList()) }
    private fun gridPages(format: ShareFormat) = squad.players.chunked(if (format == ShareFormat.POST) GRID_PER_POST else GRID_PER_STORY).ifEmpty { listOf(emptyList()) }

    override val templates: List<ShareTemplate> = listOf(
        ShareTemplate.paged("sheet", Res.string.share_template_team_sheet, TemplateKind.OPAQUE, pages = { sheetPages(it).size }) { format, page ->
            val pages = sheetPages(format)
            TeamSheetTemplate(squad, pages[page.coerceIn(pages.indices)], firstNumber = page * (if (format == ShareFormat.POST) SHEET_PER_POST else SHEET_PER_STORY) + 1, page, pages.size, format)
        },
        ShareTemplate.paged("grid", Res.string.share_template_player_grid, TemplateKind.OPAQUE, pages = { gridPages(it).size }) { format, page ->
            val pages = gridPages(format)
            PlayerGridTemplate(squad, pages[page.coerceIn(pages.indices)], page, pages.size, format)
        },
    )

    companion object {
        const val KEY = "squad"
        private const val SHEET_PER_STORY = 12
        private const val SHEET_PER_POST = 10
        private const val GRID_PER_STORY = 12
        private const val GRID_PER_POST = 8
    }
}

/** The team's badge, name and city above a numbered list on brand green; posts get two columns. */
@Composable
fun TeamSheetTemplate(squad: SquadFigures, players: List<PlayerSummary>, firstNumber: Int, page: Int, pages: Int, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    val post = format == ShareFormat.POST
    Box(Modifier.fillMaxSize()) {
        PosterBackdrop()
        Column(Modifier.fillMaxSize().padding(horizontal = if (post) 20.dp else 26.dp, vertical = if (post) 18.dp else 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PosterAvatar(squad.team, squad.logoUrl, if (post) 52.dp else 76.dp, on.copy(alpha = 0.12f), share.gold, ring = share.gold)
                Column(verticalArrangement = Arrangement.spacedBy(if (post) 2.dp else 6.dp)) {
                    PosterTag(stringResource(Res.string.poster_squad))
                    Text(squad.team, style = type.titleLg.sized(if (post) 22 else 30, if (post) 26 else 34), color = on, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(squad.city.takeIf { it.isNotBlank() }, pluralStringResource(Res.plurals.poster_player_count, squad.players.size, squad.players.size)).joinToString(" · "),
                        style = type.body.sized(13, 16),
                        color = on.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(if (post) 12.dp else 24.dp))
            val columns = if (post) players.chunked((players.size + 1) / 2).ifEmpty { listOf(emptyList()) } else listOf(players)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                var number = firstNumber
                columns.forEach { column ->
                    Column(Modifier.weight(1f)) {
                        column.forEach { player ->
                            SheetRow(number++, player, post)
                        }
                    }
                }
            }
            PosterFooter(on.copy(alpha = 0.85f), page, pages)
        }
    }
}

@Composable
private fun SheetRow(number: Int, player: PlayerSummary, post: Boolean) {
    val colors = DesiTheme.colors
    val type = DesiTheme.type
    val on = colors.onBrandStrong
    Column {
        Row(Modifier.fillMaxWidth().height(if (post) 28.dp else 34.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number.toString().padStart(2, '0'), style = type.scoreMd.sized(if (post) 14 else 18, 20), color = colors.share.gold, modifier = Modifier.width(if (post) 26.dp else 36.dp))
            Text(player.name, style = type.titleMd.sized(if (post) 13 else 17, 20), color = on, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (!post) player.role?.let { Text(stringResource(it.label).uppercase(), style = type.label.sized(10, 12), color = on.copy(alpha = 0.65f), maxLines = 1) }
        }
        PosterRule(on.copy(alpha = 0.12f))
    }
}

/** Faces on paper: each player's photo or initials, name and role, in a grid under the team's name. */
@Composable
fun PlayerGridTemplate(squad: SquadFigures, players: List<PlayerSummary>, page: Int, pages: Int, format: ShareFormat) {
    val colors = DesiTheme.colors
    val share = colors.share
    val type = DesiTheme.type
    val ink = share.paperInk
    val post = format == ShareFormat.POST
    val columns = if (post) 4 else 3
    Column(Modifier.fillMaxSize().background(share.paper).padding(horizontal = if (post) 18.dp else 24.dp, vertical = if (post) 16.dp else 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosterAvatar(squad.team, squad.logoUrl, if (post) 40.dp else 52.dp, colors.brandStrong, colors.onBrandStrong)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.poster_squad).uppercase(), style = type.label.sized(11, 14), color = colors.brand)
                Text(squad.team, style = type.titleLg.sized(if (post) 20 else 26, if (post) 24 else 30), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PosterRule(ink.copy(alpha = 0.15f), Modifier.padding(vertical = if (post) 10.dp else 18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (post) 8.dp else 14.dp)) {
            players.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { player ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PosterAvatar(player.name, player.photoUrl, if (post) 52.dp else 68.dp, colors.brandStrong, colors.onBrandStrong)
                            Text(player.name, style = type.bodyStrong.sized(if (post) 11 else 13, if (post) 13 else 16), color = ink, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(player.role?.let { stringResource(it.label) }.orEmpty(), style = type.caption.sized(10, 12), color = ink.copy(alpha = 0.6f), maxLines = 1)
                        }
                    }
                    // Keep the last row's faces the same size as the rest.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        PosterFooter(ink, page, pages)
    }
}
