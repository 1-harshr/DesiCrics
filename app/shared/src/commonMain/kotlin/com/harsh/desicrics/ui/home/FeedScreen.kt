package com.harsh.desicrics.ui.home
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

@Composable
internal fun FeedScreen(onOpen: (String) -> Unit, community: () -> Unit, rankings: () -> Unit) {
    val repo = koinInject<SocialRepository>()
    val model = viewModel { FeedModel(repo) }
    LaunchedEffect(model.filter, model.scope) { while (true) { kotlinx.coroutines.delay(30_000); model.load(model.filter, background = true) } }
    val filters = listOf(null, "IN_PROGRESS", "SCHEDULED", "COMPLETED")
    val labels = listOf(Res.string.feed_all, Res.string.home_section_live, Res.string.home_section_upcoming, Res.string.home_section_recent)
    DesiScaffold(stringResource(Res.string.tab_feed), actions = {
        TextButton(onClick = { model.load(model.filter) }) { Text(stringResource(Res.string.feed_refresh)) }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(DesiTheme.space.s4),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
            item { Row {
                TextButton(onClick = community) { Text(stringResource(Res.string.home_community)) }
                TextButton(onClick = rankings) { Text(stringResource(Res.string.home_leaderboards)) }
            } }
            item { ChoiceChips(listOf(null, "following", "city"), model.scope, label = {
                stringResource(when (it) { "following" -> Res.string.feed_following; "city" -> Res.string.feed_city; else -> Res.string.feed_all })
            }, onSelect = { model.load(model.filter, audience = it) }) }
            if (model.incoming.isNotEmpty()) item { GhostButton(stringResource(Res.string.feed_new), { model.load(model.filter) }) }
            if (model.failed && model.rows.isNotEmpty()) item {
                ErrorBanner(stringResource(Res.string.feed_stale), icon = DesiIcons.CloudOff, warning = true, action = stringResource(Res.string.feed_retry), onAction = { model.load(model.filter) })
            }
            item { ChoiceChips(filters, model.filter, label = { stringResource(labels[filters.indexOf(it)]) }, onSelect = { model.load(it) }) }
            items(model.rows, key = { it.id }) { HomeMatchCard(it, false) { onOpen(it.id) } }
            item {
                when {
                    model.loading -> CircularProgressIndicator()
                    model.failed && model.rows.isEmpty() -> ErrorState(stringResource(Res.string.error_feed_title), stringResource(Res.string.error_connection_body), onRetry = { model.load(model.filter) }, compact = true)
                    model.failed -> Unit
                    model.rows.isEmpty() -> Text(stringResource(Res.string.feed_empty))
                    model.more -> GhostButton(stringResource(Res.string.feed_more), { model.load(model.filter, true) })
                }
            }
        }
    }
}
