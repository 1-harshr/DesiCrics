package com.harsh.desicrics.shell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.harsh.desicrics.data.AppMode
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.OptionCard
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.mode_full
import com.harsh.desicrics.resources.mode_full_body
import com.harsh.desicrics.resources.mode_hint
import com.harsh.desicrics.resources.mode_simple
import com.harsh.desicrics.resources.mode_simple_body
import com.harsh.desicrics.resources.mode_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val AppMode.label: StringResource
    get() = when (this) {
        AppMode.SIMPLE -> Res.string.mode_simple
        AppMode.FULL -> Res.string.mode_full
    }

private val AppMode.description: StringResource
    get() = when (this) {
        AppMode.SIMPLE -> Res.string.mode_simple_body
        AppMode.FULL -> Res.string.mode_full_body
    }

/**
 * The two modes, each with what it shows. Picking one switches the app straight away, so the sheet closes
 * with the shell it was opened from. Both modes' settings open this same sheet.
 */
@Composable
fun AppModeSheet(current: AppMode, onSelect: (AppMode) -> Unit, onDismiss: () -> Unit) {
    DesiSheet(stringResource(Res.string.mode_title), onDismiss, stringResource(Res.string.mode_hint)) {
        AppMode.entries.forEach { mode ->
            OptionCard(
                title = stringResource(mode.label),
                description = stringResource(mode.description),
                icon = if (mode == AppMode.SIMPLE) DesiIcons.Cricket else DesiIcons.Trophy,
                selected = mode == current,
                onClick = { onSelect(mode) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
