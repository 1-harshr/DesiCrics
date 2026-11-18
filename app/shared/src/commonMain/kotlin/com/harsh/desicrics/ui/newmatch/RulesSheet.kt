package com.harsh.desicrics.ui.newmatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.NumberStepper
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.RetiredReturn
import com.harsh.desicrics.model.RuleMode
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Starting points for a match's rules. [CUSTOM] is whatever the scorer changed from one of them. */
internal enum class RulesPreset(val rules: MatchRules?, val label: StringResource, val hint: StringResource) {
    RELAXED(MatchRules(), Res.string.rules_preset_relaxed, Res.string.rules_preset_relaxed_hint),
    GULLY(MatchRules.Gully, Res.string.rules_preset_gully, Res.string.rules_preset_gully_hint),
    CLUB(MatchRules.Club, Res.string.rules_preset_club, Res.string.rules_preset_club_hint),
    STANDARD(MatchRules.Standard, Res.string.rules_preset_standard, Res.string.rules_preset_standard_hint),
    CUSTOM(null, Res.string.rules_preset_custom, Res.string.rules_preset_custom_hint);

    companion object {
        fun matching(rules: MatchRules): RulesPreset = entries.firstOrNull { it.rules == rules } ?: CUSTOM
    }
}

/** Every rule, grouped into batting, bowling and extras. Changes apply straight away; Done just closes. */
@Composable
internal fun RulesSheet(rules: MatchRules, oversPerInnings: Int, onChange: (MatchRules) -> Unit, onDismiss: () -> Unit) {
    DesiSheet(stringResource(Res.string.rules_title), onDismiss, stringResource(Res.string.rules_modes_hint)) {
        SectionLabel(stringResource(Res.string.rules_section_batting))
        Rule(Res.string.rules_last_man, Res.string.rules_last_man_hint) {
            SegmentedControl(
                options = listOf(false, true),
                selected = rules.lastManStands,
                label = { stringResource(if (it) Res.string.rules_on else Res.string.rules_mode_off) },
                onSelect = { onChange(rules.copy(lastManStands = it)) },
            )
        }
        Rule(Res.string.rules_retired_return, Res.string.rules_retired_return_hint) {
            ModeControl(rules.retiredReturnMode) { onChange(rules.copy(retiredReturnMode = it)) }
            if (rules.retiredReturnMode != RuleMode.OFF) {
                ChoiceChips(
                    options = RetiredReturn.entries,
                    selected = rules.retiredReturn,
                    label = { stringResource(it.label) },
                    onSelect = { onChange(rules.copy(retiredReturn = it)) },
                )
            }
        }
        Rule(Res.string.rules_retire_at, Res.string.rules_retire_at_hint) {
            ModeControl(rules.retireAtMode) { onChange(rules.copy(retireAtMode = it)) }
            if (rules.retireAtMode != RuleMode.OFF) {
                NumberStepper(
                    value = rules.retireAtRuns,
                    onValueChange = { onChange(rules.copy(retireAtRuns = it)) },
                    label = stringResource(Res.string.rules_retire_at_label),
                    decreaseLabel = stringResource(Res.string.rules_retire_at_fewer),
                    increaseLabel = stringResource(Res.string.rules_retire_at_more),
                    range = MatchRules.RETIRE_AT,
                )
            }
        }

        SectionLabel(stringResource(Res.string.rules_section_bowling), Modifier.padding(top = DesiTheme.space.s4))
        Rule(Res.string.rules_over_limit, Res.string.rules_over_limit_hint) {
            ModeControl(rules.bowlerOverLimitMode) { onChange(rules.copy(bowlerOverLimitMode = it)) }
            if (rules.bowlerOverLimitMode != RuleMode.OFF) {
                val evenShare = MatchRules().overLimit(oversPerInnings)
                // Even share follows the overs if they change later; a number picked here stays as it is.
                ChoiceChips(
                    options = listOf(Unit),
                    selected = Unit.takeIf { rules.bowlerOverLimit == null },
                    label = { stringResource(Res.string.rules_over_limit_even, evenShare) },
                    onSelect = { onChange(rules.copy(bowlerOverLimit = null)) },
                )
                NumberStepper(
                    value = rules.overLimit(oversPerInnings).coerceAtMost(oversPerInnings),
                    onValueChange = { onChange(rules.copy(bowlerOverLimit = it)) },
                    label = stringResource(Res.string.rules_over_limit_label),
                    decreaseLabel = stringResource(Res.string.rules_over_limit_fewer),
                    increaseLabel = stringResource(Res.string.rules_over_limit_more),
                    range = 1..oversPerInnings,
                )
            }
        }
        Rule(Res.string.rules_consecutive, Res.string.rules_consecutive_hint) {
            ModeControl(rules.consecutiveOversMode) { onChange(rules.copy(consecutiveOversMode = it)) }
        }

        SectionLabel(stringResource(Res.string.rules_section_extras), Modifier.padding(top = DesiTheme.space.s4))
        ExtraRule(
            title = Res.string.rules_wide,
            penalty = rules.widePenalty,
            rebowled = rules.wideRebowled,
            onPenalty = { onChange(rules.copy(widePenalty = it)) },
            onRebowled = { onChange(rules.copy(wideRebowled = it)) },
        )
        ExtraRule(
            title = Res.string.rules_no_ball,
            penalty = rules.noBallPenalty,
            rebowled = rules.noBallRebowled,
            onPenalty = { onChange(rules.copy(noBallPenalty = it)) },
            onRebowled = { onChange(rules.copy(noBallRebowled = it)) },
        )
        Rule(Res.string.rules_free_hit, Res.string.rules_free_hit_hint) {
            ModeControl(rules.freeHitMode) { onChange(rules.copy(freeHitMode = it)) }
        }

        SectionLabel(stringResource(Res.string.rules_section_scoring), Modifier.padding(top = DesiTheme.space.s4))
        Rule(Res.string.rules_wagon_wheel, Res.string.rules_wagon_wheel_hint) {
            SegmentedControl(
                options = listOf(false, true),
                selected = rules.wagonWheel,
                label = { stringResource(if (it) Res.string.rules_on else Res.string.rules_mode_off) },
                onSelect = { onChange(rules.copy(wagonWheel = it)) },
            )
        }
        Rule(Res.string.rules_powerplay, Res.string.rules_powerplay_hint) {
            NumberStepper(
                value = rules.powerplayOvers.coerceAtMost(oversPerInnings),
                onValueChange = { onChange(rules.copy(powerplayOvers = it)) },
                label = stringResource(Res.string.rules_powerplay_label),
                decreaseLabel = stringResource(Res.string.rules_powerplay_fewer),
                increaseLabel = stringResource(Res.string.rules_powerplay_more),
                range = 0..oversPerInnings,
            )
        }

        PrimaryButton(
            text = stringResource(Res.string.rules_done),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(top = DesiTheme.space.s2),
        )
    }
}

/** One rule: its name, a line on what it does, then its controls. */
@Composable
private fun Rule(title: StringResource, hint: StringResource?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        Text(stringResource(title), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
        if (hint != null) Text(stringResource(hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        content()
    }
}

@Composable
private fun ModeControl(mode: RuleMode, onMode: (RuleMode) -> Unit) {
    SegmentedControl(
        options = RuleMode.entries,
        selected = mode,
        label = { stringResource(it.label) },
        onSelect = onMode,
    )
}

/** A wide or no-ball: how many runs it gives on its own, and whether it's bowled again. */
@Composable
private fun ExtraRule(
    title: StringResource,
    penalty: Int,
    rebowled: Boolean,
    onPenalty: (Int) -> Unit,
    onRebowled: (Boolean) -> Unit,
) {
    Rule(title, hint = null) {
        Text(stringResource(Res.string.rules_penalty), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        ChoiceChips(options = MatchRules.PENALTY.toList(), selected = penalty, label = { "$it" }, onSelect = onPenalty)
        SegmentedControl(
            options = listOf(true, false),
            selected = rebowled,
            label = { stringResource(if (it) Res.string.rules_rebowled else Res.string.rules_counts_as_ball) },
            onSelect = onRebowled,
        )
    }
}

private val RuleMode.label: StringResource
    get() = when (this) {
        RuleMode.OFF -> Res.string.rules_mode_off
        RuleMode.WARN -> Res.string.rules_mode_warn
        RuleMode.ENFORCE -> Res.string.rules_mode_enforce
    }

private val RetiredReturn.label: StringResource
    get() = when (this) {
        RetiredReturn.ANY_TIME -> Res.string.rules_return_any_time
        RetiredReturn.AFTER_WICKET -> Res.string.rules_return_after_wicket
        RetiredReturn.AT_END -> Res.string.rules_return_at_end
        RetiredReturn.NEVER -> Res.string.rules_return_never
    }

// Previews: the sheet with a preset's rules; all labels come from string resources.

@Preview
@Composable
private fun RulesSheetStandardPreview() = DesiTheme(darkTheme = false) {
    RulesSheet(MatchRules.Standard, oversPerInnings = 20, onChange = {}, onDismiss = {})
}

@Preview
@Composable
private fun RulesSheetGullyDarkPreview() = DesiTheme(darkTheme = true) {
    RulesSheet(MatchRules.Gully.copy(retireAtMode = RuleMode.ENFORCE), oversPerInnings = 8, onChange = {}, onDismiss = {})
}
