package com.harsh.desicrics.ui.auction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.PlayerRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionMode
import com.harsh.desicrics.model.AuctionOrder
import com.harsh.desicrics.model.AuctionPlayer
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionTeam
import com.harsh.desicrics.model.BidStep
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.components.formatPublicCode
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private sealed interface SetupSheet {
    data object Rules : SetupSheet
    data object AddPlayers : SetupSheet
    data class Player(val profileId: String) : SetupSheet
}

/**
 * Before the auction starts. Organisers set the rules, build the pool (search, code or new players), keep
 * retained players with their teams, answer requests to join and start it. Everyone else sees the rules, the
 * teams and the pool, and can ask to be put up.
 */
@Composable
internal fun AuctionSetupContent(state: AuctionUiState, actions: AuctionActions) {
    val details = state.details ?: return
    val space = DesiTheme.space
    var sheet by remember { mutableStateOf<SetupSheet?>(null) }
    val close = {
        sheet = null
        actions.clearError()
        actions.search("")
    }
    val organiser = state.isOrganiser
    DesiScaffold(
        title = if (organiser) stringResource(Res.string.auction_setup_title) else stringResource(Res.string.auction_title),
        onBack = actions::back,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = space.screen).padding(bottom = space.s8),
            verticalArrangement = Arrangement.spacedBy(space.s4),
        ) {
            Text(details.auction.tournamentName, style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
            if (!organiser) WaitingCard(state, actions)
            if (state.actionFailed && sheet == null) ErrorBanner(state.actionError ?: stringResource(Res.string.auction_action_failed))
            RulesCard(details.auction.settings, onEdit = if (organiser) ({ sheet = SetupSheet.Rules }) else null)
            SetupTeams(details)
            val requests = details.players.filter { it.requested }
            if (organiser && requests.isNotEmpty()) Requests(requests, actions)
            Pool(details, organiser, onAdd = { sheet = SetupSheet.AddPlayers }, onOpen = { sheet = SetupSheet.Player(it) })
            if (organiser) {
                val error = state.startError
                PrimaryButton(
                    stringResource(Res.string.auction_start),
                    onClick = actions::start,
                    enabled = error == null,
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = DesiIcons.Auction,
                )
                Text(
                    error?.let { stringResource(it.text) } ?: stringResource(Res.string.auction_start_hint),
                    style = DesiTheme.type.caption,
                    color = DesiTheme.colors.muted,
                )
            }
        }
    }
    when (val current = sheet) {
        null -> Unit
        SetupSheet.Rules -> RulesSheet(details.auction.settings, onSave = { actions.updateSettings(it); close() }, onDismiss = close)
        SetupSheet.AddPlayers -> AddPlayersSheet(state, actions, onDismiss = close)
        is SetupSheet.Player -> details.player(current.profileId)?.let { player ->
            PlayerSheet(details, player, actions, onDismiss = close)
        }
    }
}

/** For players and owners waiting on the organiser: what's coming, and a way into the pool. */
@Composable
private fun WaitingCard(state: AuctionUiState, actions: AuctionActions) {
    val details = state.details ?: return
    val me = state.me
    DesiCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
            com.harsh.desicrics.designsystem.component.IconTile(DesiIcons.Auction)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.auction_waiting_title), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
                Text(stringResource(Res.string.auction_waiting_body), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
            }
        }
        state.myTeams.takeIf { it.isNotEmpty() && details.auction.settings.mode == AuctionMode.ONLINE }?.let { teams ->
            Tag(stringResource(Res.string.auction_you_bid_for, teams.joinToString { it.name }), tone = TagTone.BRAND, icon = DesiIcons.Shield)
        }
        when {
            me == null -> TonalButton(
                stringResource(Res.string.auction_card_ask),
                onClick = { actions.setRequest(true) },
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = DesiIcons.PersonAdd,
            )
            me.requested -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.auction_card_requested), style = DesiTheme.type.body, color = DesiTheme.colors.muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { actions.setRequest(false) }) {
                    Text(stringResource(Res.string.auction_card_withdraw), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                }
            }
            else -> Tag(stringResource(Res.string.auction_card_in_pool), tone = TagTone.BRAND, icon = DesiIcons.CheckCircle)
        }
    }
}

/** The rules at a glance, with an Edit link for organisers. */
@Composable
internal fun RulesCard(settings: AuctionSettings, onEdit: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionHeader(stringResource(Res.string.auction_rules), icon = DesiIcons.Whistle, action = onEdit?.let { stringResource(Res.string.auction_rules_edit) }, onAction = onEdit)
        ListCard {
            val rows = buildList {
                add(Res.string.auction_rule_mode to stringResource(settings.mode.label))
                add(Res.string.auction_rule_purse to points(settings.purse, settings))
                add(Res.string.auction_rule_base to points(settings.basePrice, settings))
                add(Res.string.auction_rule_squad to stringResource(Res.string.auction_rule_squad_value, settings.minSquad, settings.maxSquad))
                add(Res.string.auction_rule_steps to stepsText(settings.bidSteps))
                add(Res.string.auction_rule_order to stringResource(settings.order.label))
                add(Res.string.auction_rule_unsold to stringResource(if (settings.unsoldRound) Res.string.auction_rule_on else Res.string.auction_rule_off))
                if (settings.mode == AuctionMode.ONLINE) {
                    add(Res.string.auction_rule_clock to stringResource(Res.string.auction_rule_clock_value, settings.lotSeconds, settings.bidSeconds))
                }
            }
            rows.forEachIndexed { i, (label, value) ->
                ListRow(
                    title = stringResource(label),
                    titleColor = DesiTheme.colors.muted,
                    trailing = { Text(value, style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink) },
                    divider = i > 0,
                )
            }
        }
    }
}

/** The tournament's confirmed teams, with what each has kept back before the auction. */
@Composable
private fun SetupTeams(details: AuctionDetails) {
    val settings = details.auction.settings
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionHeader(stringResource(Res.string.auction_teams), icon = DesiIcons.Shield)
        if (details.teams.size < 2) {
            EmptyState(DesiIcons.Shield, stringResource(Res.string.auction_teams_none))
            if (details.teams.isEmpty()) return@Column
        }
        ListCard {
            details.teams.forEachIndexed { i, team ->
                val kept = details.players.filter { !it.requested && it.retainedBy == team.teamId }
                val left = settings.purse - kept.sumOf { it.retainedPrice ?: 0 }
                ListRow(
                    title = team.name,
                    subtitle = stringResource(Res.string.auction_team_purse_line, points(left, settings), kept.size),
                    leading = { Avatar(team.name, size = 40) },
                    divider = i > 0,
                )
            }
        }
    }
}

@Composable
private fun Requests(requests: List<AuctionPlayer>, actions: AuctionActions) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionHeader(stringResource(Res.string.auction_requests), icon = DesiIcons.PersonAdd)
        ListCard {
            requests.forEachIndexed { i, request ->
                ListRow(
                    title = request.player.name,
                    subtitle = playerLine(request, request.player.city.ifBlank { null }).ifEmpty { null },
                    leading = { PhotoAvatar(request.player.name, request.player.photoUrl) },
                    trailing = {
                        TextButton(onClick = { actions.remove(request.profileId) }) {
                            Text(stringResource(Res.string.auction_decline), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                        }
                        TextButton(onClick = { actions.accept(request.profileId) }) {
                            Text(stringResource(Res.string.auction_accept), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
                        }
                    },
                    divider = i > 0,
                )
            }
        }
    }
}

/** The players going under the hammer, grouped by category in the order they'll come up. */
@Composable
private fun Pool(details: AuctionDetails, organiser: Boolean, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val settings = details.auction.settings
    val pool = details.players.filter { !it.requested }
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionHeader(
            stringResource(Res.string.auction_pool) + " · " + pluralStringResource(Res.plurals.auction_players_count, pool.size, pool.size),
            icon = DesiIcons.Teams,
        )
        if (organiser) TonalButton(stringResource(Res.string.auction_pool_add), onClick = onAdd, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Add)
        if (pool.isEmpty()) {
            EmptyState(DesiIcons.Teams, stringResource(Res.string.auction_pool_empty))
            return@Column
        }
        val rank = { player: AuctionPlayer ->
            settings.categories.indexOfFirst { it.equals(player.category?.trim(), ignoreCase = true) }.let { if (it < 0) settings.categories.size else it }
        }
        ListCard {
            pool.sortedWith(compareBy(rank).thenBy { it.addedAt }).forEachIndexed { i, player ->
                val retainedBy = player.retainedBy?.let(details::teamName)
                val price = if (retainedBy != null) {
                    stringResource(Res.string.auction_player_retained, retainedBy, points(player.retainedPrice ?: 0, settings))
                } else {
                    stringResource(Res.string.auction_player_line_base, points(player.basePrice, settings))
                }
                ListRow(
                    title = player.player.name,
                    subtitle = playerLine(player, if (player.player.isPlaceholder) stringResource(Res.string.auction_player_placeholder) else null),
                    leading = { PhotoAvatar(player.player.name, player.player.photoUrl) },
                    trailing = {
                        Text(
                            price,
                            style = DesiTheme.type.caption,
                            color = if (retainedBy != null) DesiTheme.colors.brand else DesiTheme.colors.muted,
                        )
                    },
                    divider = i > 0,
                    onClick = if (organiser) ({ onOpen(player.profileId) }) else null,
                )
            }
        }
    }
}

/** A whole-number field that keeps what's typed and hands back the number, or null while it isn't one. */
@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String? = null) {
    DesiTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() }.take(8)) },
        label = label,
        modifier = modifier,
        supportingText = suffix,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
}

/**
 * The organiser's rules: mode, purse and its label, base price, squad limits, bid steps (a small step up to a
 * point and a bigger one after), order, categories, the unsold round and the online clock. Saving checks them
 * with the same rules as the server.
 */
@Composable
private fun RulesSheet(settings: AuctionSettings, onSave: (AuctionSettings) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(settings.mode) }
    var purse by rememberSaveable { mutableStateOf("${settings.purse}") }
    var label by rememberSaveable { mutableStateOf(settings.purseLabel) }
    var base by rememberSaveable { mutableStateOf("${settings.basePrice}") }
    var min by rememberSaveable { mutableStateOf("${settings.minSquad}") }
    var max by rememberSaveable { mutableStateOf("${settings.maxSquad}") }
    val low = settings.bidSteps.first()
    var lowStep by rememberSaveable { mutableStateOf("${low.step}") }
    var until by rememberSaveable { mutableStateOf(low.upTo?.toString().orEmpty()) }
    var highStep by rememberSaveable { mutableStateOf("${settings.bidSteps.last().step}") }
    var order by rememberSaveable { mutableStateOf(settings.order) }
    var categories by rememberSaveable { mutableStateOf(settings.categories.joinToString(", ")) }
    var unsoldRound by rememberSaveable { mutableStateOf(settings.unsoldRound) }
    var lotSeconds by rememberSaveable { mutableStateOf("${settings.lotSeconds}") }
    var bidSeconds by rememberSaveable { mutableStateOf("${settings.bidSeconds}") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val untilValue = until.toIntOrNull()
    val draft = settings.copy(
        mode = mode,
        purse = purse.toIntOrNull() ?: 0,
        purseLabel = label.trim(),
        basePrice = base.toIntOrNull() ?: 0,
        minSquad = min.toIntOrNull() ?: -1,
        maxSquad = max.toIntOrNull() ?: 0,
        bidSteps = if (untilValue == null) listOf(BidStep(step = lowStep.toIntOrNull() ?: 0))
        else listOf(BidStep(untilValue, lowStep.toIntOrNull() ?: 0), BidStep(step = highStep.toIntOrNull() ?: 0)),
        order = order,
        categories = categories.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        unsoldRound = unsoldRound,
        lotSeconds = lotSeconds.toIntOrNull() ?: 0,
        bidSeconds = bidSeconds.toIntOrNull() ?: 0,
    )
    val error = draft.validate()
    val space = DesiTheme.space
    DesiSheet(stringResource(Res.string.auction_settings_title), onDismiss, fullHeight = true) {
        SectionLabelText(stringResource(Res.string.auction_rule_mode))
        SegmentedControl(AuctionMode.entries, selected = mode, label = { stringResource(it.label) }, onSelect = { mode = it })
        Text(
            stringResource(if (mode == AuctionMode.HALL) Res.string.auction_mode_hall_hint else Res.string.auction_mode_online_hint),
            style = DesiTheme.type.caption,
            color = DesiTheme.colors.muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
            NumberField(purse, { purse = it }, stringResource(Res.string.auction_rule_purse), Modifier.weight(1f))
            DesiTextField(
                value = label,
                onValueChange = { label = it.take(AuctionSettings.MAX_LABEL) },
                label = stringResource(Res.string.auction_settings_label),
                modifier = Modifier.weight(1f),
            )
        }
        Text(stringResource(Res.string.auction_settings_label_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        NumberField(base, { base = it }, stringResource(Res.string.auction_rule_base), suffix = stringResource(Res.string.auction_settings_base_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
            NumberField(min, { min = it }, stringResource(Res.string.auction_settings_min), Modifier.weight(1f))
            NumberField(max, { max = it }, stringResource(Res.string.auction_settings_max), Modifier.weight(1f))
        }
        SectionLabelText(stringResource(Res.string.auction_rule_steps))
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
            NumberField(lowStep, { lowStep = it }, stringResource(Res.string.auction_settings_step), Modifier.weight(1f))
            NumberField(until, { until = it }, stringResource(Res.string.auction_settings_until), Modifier.weight(1f))
            if (until.isNotEmpty()) NumberField(highStep, { highStep = it }, stringResource(Res.string.auction_settings_then), Modifier.weight(1f))
        }
        Text(stepsText(draft.bidSteps), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        SectionLabelText(stringResource(Res.string.auction_rule_order))
        ChoiceChips(AuctionOrder.entries, selected = order, label = { stringResource(it.label) }, onSelect = { order = it })
        DesiTextField(
            value = categories,
            onValueChange = { categories = it.take(400) },
            label = stringResource(Res.string.auction_settings_categories),
            supportingText = stringResource(Res.string.auction_settings_categories_hint),
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.auction_settings_unsold), style = DesiTheme.type.body, color = DesiTheme.colors.ink, modifier = Modifier.weight(1f))
            Switch(
                checked = unsoldRound,
                onCheckedChange = { unsoldRound = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DesiTheme.colors.brand, checkedThumbColor = DesiTheme.colors.onBrand),
            )
        }
        if (mode == AuctionMode.ONLINE) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                NumberField(lotSeconds, { lotSeconds = it }, stringResource(Res.string.auction_settings_lot_seconds), Modifier.weight(1f))
                NumberField(bidSeconds, { bidSeconds = it }, stringResource(Res.string.auction_settings_bid_seconds), Modifier.weight(1f))
            }
        }
        if (tried && error != null) ErrorBanner(stringResource(error.text))
        PrimaryButton(
            stringResource(Res.string.auction_settings_save),
            onClick = { if (error == null) onSave(draft) else tried = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionLabelText(text: String) = Text(text, style = DesiTheme.type.label, color = DesiTheme.colors.muted)

/**
 * Adds players to the pool: people on the app by name or player code, or new players by name (and phone, so
 * they can claim the profile later). The sheet stays open for the next one.
 */
@Composable
private fun AddPlayersSheet(state: AuctionUiState, actions: AuctionActions, onDismiss: () -> Unit) {
    val inPool = state.details?.players.orEmpty().map { it.profileId }.toSet()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    DesiSheet(stringResource(Res.string.auction_pool_add), onDismiss, fullHeight = true) {
        DesiTextField(
            value = state.searchQuery,
            onValueChange = { actions.search(it.take(60)) },
            label = stringResource(Res.string.auction_add_search),
            leadingIcon = DesiIcons.Search,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        )
        if (state.searchQuery.trim().length >= 2 && !state.searching && state.searchResults.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                Icon(painterResource(DesiIcons.Info), null, tint = DesiTheme.colors.muted, modifier = Modifier.size(16.dp))
                Text(stringResource(Res.string.auction_add_search_empty), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
            }
        }
        Column {
            state.searchResults.forEach { player ->
                val added = player.profileId in inPool
                PlayerRow(
                    name = player.name,
                    subtitle = listOfNotNull(player.role?.let { stringResource(it.label) }, player.city.ifBlank { null }, player.code?.let(::formatPublicCode))
                        .joinToString(" · ").ifEmpty { null },
                    avatar = { PhotoAvatar(player.name, player.photoUrl) },
                    trailing = {
                        if (added) Tag(stringResource(Res.string.auction_add_added), tone = TagTone.BRAND, icon = DesiIcons.CheckCircle)
                        else Icon(painterResource(DesiIcons.PersonAdd), null, tint = DesiTheme.colors.brand, modifier = Modifier.size(DesiTheme.size.iconSmall))
                    },
                    onClick = if (added) null else ({ actions.addPlayer(player.profileId) }),
                )
            }
        }
        SectionHeader(stringResource(Res.string.auction_add_new_title), Modifier.padding(top = DesiTheme.space.s2), icon = DesiIcons.PersonAdd)
        Text(stringResource(Res.string.auction_add_new_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        DesiTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = stringResource(Res.string.auction_add_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        DesiTextField(
            value = phone,
            onValueChange = { new -> phone = new.filter { it.isDigit() || it == '+' || it == ' ' }.take(16) },
            label = stringResource(Res.string.auction_add_phone),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        )
        PrimaryButton(
            stringResource(Res.string.auction_add_new_action, name.trim()),
            onClick = {
                actions.addPlaceholder(name.trim(), phone)
                name = ""
                phone = ""
            },
            enabled = name.isNotBlank(),
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.actionFailed) ErrorBanner(state.actionError ?: stringResource(Res.string.auction_action_failed))
        GhostButton(stringResource(Res.string.auction_add_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

/** One pool player: base price, category, and a team keeping them before the auction at a set price. */
@Composable
private fun PlayerSheet(details: AuctionDetails, player: AuctionPlayer, actions: AuctionActions, onDismiss: () -> Unit) {
    val settings = details.auction.settings
    var base by rememberSaveable(player.profileId) { mutableStateOf("${player.basePrice}") }
    var category by rememberSaveable(player.profileId) { mutableStateOf(player.category) }
    var retainedBy by rememberSaveable(player.profileId) { mutableStateOf(player.retainedBy) }
    var retainedPrice by rememberSaveable(player.profileId) { mutableStateOf(player.retainedPrice?.toString() ?: "${player.basePrice}") }
    val none = stringResource(Res.string.auction_player_none)
    val categories = (settings.categories + listOfNotNull(player.category)).distinctBy { it.lowercase() }
    val nobody = AuctionTeam("", stringResource(Res.string.auction_player_nobody))
    val valid = (base.toIntOrNull() ?: 0) >= 1 && (retainedBy == null || retainedPrice.toIntOrNull() != null)
    DesiSheet(player.player.name, onDismiss, hint = playerLine(player).ifEmpty { null }) {
        NumberField(base, { base = it }, stringResource(Res.string.auction_rule_base), suffix = settings.purseLabel)
        SectionLabelText(stringResource(Res.string.auction_player_category))
        ChoiceChips(listOf(none) + categories, selected = category ?: none, label = { it }, onSelect = { category = it.takeIf { c -> c != none } })
        SectionLabelText(stringResource(Res.string.auction_player_retain))
        ChoiceChips(
            listOf(nobody) + details.teams,
            selected = details.teams.firstOrNull { it.teamId == retainedBy } ?: nobody,
            label = { it.name },
            onSelect = { retainedBy = it.teamId.ifEmpty { null } },
        )
        if (retainedBy != null) {
            NumberField(retainedPrice, { retainedPrice = it }, stringResource(Res.string.auction_player_retain_price), suffix = settings.purseLabel)
        }
        PrimaryButton(
            stringResource(Res.string.auction_player_save),
            onClick = {
                actions.updatePlayer(player.profileId, base.toInt(), category, retainedBy, retainedPrice.toIntOrNull().takeIf { retainedBy != null })
                onDismiss()
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        )
        GhostButton(
            stringResource(Res.string.auction_player_remove),
            onClick = {
                actions.remove(player.profileId)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = DesiIcons.PersonRemove,
        )
    }
}
