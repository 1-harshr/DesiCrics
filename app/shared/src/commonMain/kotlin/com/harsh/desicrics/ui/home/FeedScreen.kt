package com.harsh.desicrics.ui.home
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.FilterRow
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.ErrorState

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.designsystem.component.*
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class FeedModel(private val repo: SocialRepository) : ViewModel() {
    var rows by mutableStateOf(emptyList<Match>())
        private set
    var filter by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var more by mutableStateOf(true)
    var scope by mutableStateOf<String?>(null)
        private set
    var incoming by mutableStateOf(emptyList<Match>())
        private set
    private var job: Job? = null
    init { load(null) }

    fun load(status: String?, append: Boolean = false, audience: String? = scope, background: Boolean = false) {
        if ((append || background) && loading) return
        job?.cancel()
        if (filter != status || scope != audience) { rows = emptyList(); incoming = emptyList() }
        scope = audience
        filter = status
        loading = !background
        failed = false
        job = viewModelScope.launch {
            try {
                val next = repo.feedPage(status, if (append) rows.lastOrNull() else null, audience)
                if (background && rows.isNotEmpty()) {
                    val updates = next.associateBy { it.id }
                    incoming = next.takeWhile { m -> rows.none { it.id == m.id } }
                    rows = rows.map { updates[it.id] ?: it }
                    return@launch
                }
                incoming = emptyList()
                rows = if (append) (rows + next).distinctBy { it.id } else next
                more = next.size == 30
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true }
            finally { if (kotlin.coroutines.coroutineContext[Job]?.isActive == true) loading = false }
        }
    }
}

/** The status filters: everything, then live, upcoming and finished matches. */
private val StatusFilters = listOf(null, "IN_PROGRESS", "SCHEDULED", "COMPLETED")

/** Who the matches are from. */
private val AudienceFilters = listOf(null, "following", "city")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedScreen(onOpen: (String) -> Unit, community: () -> Unit, rankings: () -> Unit) {
    val repo = koinInject<SocialRepository>()
    val model = viewModel { FeedModel(repo) }
    val space = DesiTheme.space
    LaunchedEffect(model.filter, model.scope) { while (true) { kotlinx.coroutines.delay(30_000); model.load(model.filter, background = true) } }
    val statusLabels = listOf(Res.string.feed_status_all, Res.string.feed_status_live, Res.string.feed_status_upcoming, Res.string.feed_status_results)
    val statusIcons = listOf(null, DesiIcons.Bolt, DesiIcons.Calendar, DesiIcons.Trophy)
    DesiScaffold(stringResource(Res.string.tab_feed), actions = {
        // Refreshing is a pull on the list; these two lead to the rest of the community.
        IconButton(onClick = rankings) { Icon(painterResource(DesiIcons.Leaderboard), stringResource(Res.string.home_leaderboards)) }
        IconButton(onClick = community) { Icon(painterResource(DesiIcons.Teams), stringResource(Res.string.home_community)) }
    }) { pad ->
        PullToRefreshBox(
            isRefreshing = model.loading && model.rows.isNotEmpty(),
            onRefresh = { model.load(model.filter) },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s2),
                verticalArrangement = Arrangement.spacedBy(space.s3),
            ) {
                item {
                    SegmentedControl(
                        AudienceFilters,
                        model.scope,
                        label = {
                            stringResource(when (it) { "following" -> Res.string.feed_following; "city" -> Res.string.feed_city; else -> Res.string.feed_everyone })
                        },
                        onSelect = { model.load(model.filter, audience = it) },
                    )
                }
                item {
                    FilterRow(
                        StatusFilters,
                        model.filter,
                        label = { stringResource(statusLabels[StatusFilters.indexOf(it)]) },
                        onSelect = { model.load(it) },
                        icon = { statusIcons[StatusFilters.indexOf(it)] },
                    )
                }
                if (model.incoming.isNotEmpty()) item {
                    TonalButton(stringResource(Res.string.feed_new), { model.load(model.filter) }, Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Refresh)
                }
                if (model.failed && model.rows.isNotEmpty()) item {
                    ErrorBanner(stringResource(Res.string.feed_stale), icon = DesiIcons.CloudOff, warning = true, action = stringResource(Res.string.feed_retry), onAction = { model.load(model.filter) })
                }
                items(model.rows, key = { it.id }) { HomeMatchCard(it, false) { onOpen(it.id) } }
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = space.s2), contentAlignment = Alignment.Center) {
                        when {
                            model.loading && model.rows.isEmpty() -> CircularProgressIndicator(color = DesiTheme.colors.brand)
                            model.loading -> Unit
                            model.failed && model.rows.isEmpty() -> ErrorState(stringResource(Res.string.error_feed_title), stringResource(Res.string.error_connection_body), onRetry = { model.load(model.filter) }, compact = true)
                            model.failed -> Unit
                            model.rows.isEmpty() -> EmptyState(DesiIcons.Cricket, stringResource(Res.string.feed_empty), body = stringResource(Res.string.feed_empty_body))
                            model.more -> GhostButton(stringResource(Res.string.feed_more), { model.load(model.filter, true) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
