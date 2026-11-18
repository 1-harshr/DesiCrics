package com.harsh.desicrics.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.harsh.desicrics.BuildConfig
import com.harsh.desicrics.MainActivity
import com.harsh.desicrics.R
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The home screen widget: the match that matters most right now, picked as Home picks it (your own live
 * match, then live, then upcoming, then the latest result). Tapping it opens that match.
 */
class MatchWidget : GlanceAppWidget(), KoinComponent {

    private val matches: MatchRepository by inject()
    private val auth: AuthRepository by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load()
        provideContent { Content(context, state) }
    }

    private sealed interface WidgetState {
        data object SignedOut : WidgetState
        data object Nothing : WidgetState
        data object Offline : WidgetState
        data class Showing(val match: Match) : WidgetState
    }

    private suspend fun load(): WidgetState {
        if (auth.currentUser == null) return WidgetState.SignedOut
        val feed = try {
            matches.observeFeed().first().getOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return WidgetState.Offline
        }
        val match = (feed.mine + feed.live + feed.upcoming + feed.recent).firstOrNull() ?: return WidgetState.Nothing
        return WidgetState.Showing(match)
    }

    @Composable
    private fun Content(context: Context, state: WidgetState) {
        val green = ColorProvider(Color(0xFF0B6B3A))
        val white = ColorProvider(Color.White)
        val faint = ColorProvider(Color(0xCCFFFFFF))
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (state is WidgetState.Showing) data = Uri.parse("${BuildConfig.PUBLIC_URL.trimEnd('/')}/m/${state.match.id}")
        }
        Column(
            GlanceModifier.fillMaxSize().background(green).cornerRadius(20.dp).padding(14.dp).clickable(actionStartActivity(open)),
        ) {
            when (state) {
                is WidgetState.Showing -> {
                    val match = state.match
                    val label = when (match.status) {
                        MatchStatus.IN_PROGRESS -> context.getString(R.string.widget_live)
                        MatchStatus.COMPLETED -> context.getString(R.string.widget_result)
                        else -> context.getString(R.string.widget_upcoming)
                    }
                    Text(label, style = TextStyle(color = faint, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    Text(
                        context.getString(R.string.widget_title, match.teamA.name, match.teamB.name),
                        style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 2,
                    )
                    // The server's summary is already "Strikers 45/2 (6.0) · Royals 30/1 (4.2)".
                    Text(match.scoreSummary.ifBlank { context.getString(R.string.widget_not_started) }, style = TextStyle(color = white, fontSize = 13.sp), maxLines = 3)
                }
                WidgetState.SignedOut -> Text(context.getString(R.string.widget_signed_out), style = TextStyle(color = white, fontSize = 13.sp))
                WidgetState.Nothing -> Text(context.getString(R.string.widget_nothing), style = TextStyle(color = white, fontSize = 13.sp))
                WidgetState.Offline -> Text(context.getString(R.string.widget_offline), style = TextStyle(color = white, fontSize = 13.sp))
            }
        }
    }

    companion object {
        /** Redraws every placed widget with fresh data; cheap when none is placed. */
        suspend fun refresh(context: Context) = MatchWidget().updateAll(context)
    }
}

class MatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MatchWidget()
}
