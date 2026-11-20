package com.harsh.desicrics.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.api.CitySuggestion
import com.harsh.desicrics.data.CityRepository
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.koin.mp.KoinPlatform

/** Where suggestions come from: the server's city list, or nothing where there's no app to ask (previews). */
private fun defaultSuggest(): suspend (String) -> List<CitySuggestion> {
    val repository = KoinPlatform.getKoinOrNull()?.getOrNull<CityRepository>() ?: return { emptyList() }
    return repository::suggest
}

/**
 * A city, typed freely, with matching cities from the list underneath as you type. Picking one fills in its
 * standard spelling; anything else is kept as typed (the server tidies known names either way). Suggestions
 * only show while the field has focus, and never for a city that's already exactly picked.
 */
@Composable
fun CityField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
    supportingText: String? = null,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    suggest: suspend (String) -> List<CitySuggestion> = remember { defaultSuggest() },
) {
    var focused by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(emptyList<CitySuggestion>()) }
    val suggestionsInView = remember { BringIntoViewRequester() }
    LaunchedEffect(value, focused) {
        val query = value.trim()
        if (!focused || query.length < MIN_QUERY) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        // Wait for a pause in typing before asking.
        delay(DEBOUNCE_MS)
        suggestions = try {
            suggest(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No suggestions offline; the city can still be typed.
            emptyList()
        }
    }
    // Once a city is picked (or typed exactly), the list has done its job.
    val shown = if (suggestions.any { it.name == value.trim() }) emptyList() else suggestions
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        DesiTextField(
            value = value,
            onValueChange = { onValueChange(it.take(MAX_LENGTH)) },
            label = label,
            modifier = Modifier.onFocusChanged { focused = it.isFocused },
            leadingIcon = leadingIcon,
            supportingText = supportingText,
            error = error,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = imeAction),
        )
        if (focused && shown.isNotEmpty()) {
            // Fields low on a screen would leave the list under the keyboard.
            LaunchedEffect(shown) { suggestionsInView.bringIntoView() }
            CitySuggestions(shown, Modifier.bringIntoViewRequester(suggestionsInView), onPick = {
                onValueChange(it.name)
                suggestions = emptyList()
            })
        }
    }
}

@Composable
internal fun CitySuggestions(suggestions: List<CitySuggestion>, modifier: Modifier = Modifier, onPick: (CitySuggestion) -> Unit) {
    ListCard(modifier) {
        suggestions.forEachIndexed { index, city ->
            ListRow(
                city.name,
                subtitle = city.state.ifBlank { null },
                leading = { IconTile(DesiIcons.Location, size = 32.dp) },
                divider = index > 0,
                trailing = {},
                onClick = { onPick(city) },
            )
        }
    }
}

private const val MIN_QUERY = 2
private const val MAX_LENGTH = 100
private const val DEBOUNCE_MS = 250L
