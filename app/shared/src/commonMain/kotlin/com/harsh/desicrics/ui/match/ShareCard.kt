package com.harsh.desicrics.ui.match

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.icon.DesiIllustrations
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.ui.system.rememberShareImage
import com.harsh.desicrics.ui.text.resultText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A preview of the score card and a button that shares it as a picture. The card is drawn once on screen
 * and recorded as it draws, so the picture is exactly what the user sees.
 */
@Composable
internal fun ShareCardSheet(state: MatchState, onDismiss: () -> Unit) =
    ShareImageSheet(stringResource(Res.string.match_share_card_title), onDismiss) { ShareCard(state) }

/** Any 9:16 card, previewed and shared as a picture. */
@Composable
fun ShareImageSheet(title: String, onDismiss: () -> Unit, card: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val shareImage = rememberShareImage()
    DesiSheet(title, onDismiss, stringResource(Res.string.match_share_card_hint)) {
        Box(
            Modifier.fillMaxWidth(0.75f).align(Alignment.CenterHorizontally).drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) {
            card()
        }
        PrimaryButton(
            stringResource(Res.string.match_share),
            onClick = { scope.launch { shareImage(layer.toImageBitmap()) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The score as a 9:16 card for WhatsApp status: teams, each innings' total, and the result or the chase. */
@Composable
internal fun ShareCard(state: MatchState) {
    val colors = DesiTheme.colors
    val onGreen = colors.onBrandStrong
    val match = state.match
    Column(
        Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(DesiTheme.radius.lg).background(colors.brandStrong).padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Image(painterResource(DesiIllustrations.BallMark), contentDescription = null, modifier = Modifier.size(56.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.innings.forEach { innings ->
                Column {
                    Text(innings.battingTeam.name, style = DesiTheme.type.titleMd, color = onGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(innings.scoreText, style = DesiTheme.type.scoreXl, color = onGreen)
                        Text("(${innings.oversText})", style = DesiTheme.type.data, color = onGreen.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
            val status = state.result?.let { resultText(match, it) }
                ?: stringResource(Res.string.match_title, match.teamA.name, match.teamB.name)
            Text(status, style = DesiTheme.type.bodyStrong, color = onGreen)
        }
        Text(stringResource(Res.string.share_card_footer).uppercase(), style = DesiTheme.type.label, color = onGreen.copy(alpha = 0.8f))
    }
}
