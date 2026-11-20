package com.harsh.desicrics.ui.text

import com.harsh.desicrics.api.ProfileError
import com.harsh.desicrics.auth.SignInError
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.api.MatchSetupError
import com.harsh.desicrics.designsystem.icon.DesiIcons
import org.jetbrains.compose.resources.DrawableResource
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.StringResource

// Maps typed values from the domain and ViewModels to their text. ViewModels never hold display strings;
// composables call stringResource(x.label) on these.

val PlayerRole.label: StringResource
    get() = when (this) {
        PlayerRole.BATTER -> Res.string.player_role_batter
        PlayerRole.BOWLER -> Res.string.player_role_bowler
        PlayerRole.ALL_ROUNDER -> Res.string.player_role_all_rounder
        PlayerRole.WICKET_KEEPER -> Res.string.player_role_wicket_keeper
    }

val BattingStyle.label: StringResource
    get() = when (this) {
        BattingStyle.RIGHT_HAND -> Res.string.batting_style_right_hand
        BattingStyle.LEFT_HAND -> Res.string.batting_style_left_hand
    }

val BowlingStyle.label: StringResource
    get() = when (this) {
        BowlingStyle.NONE -> Res.string.bowling_style_none
        BowlingStyle.RIGHT_ARM_FAST -> Res.string.bowling_style_right_arm_fast
        BowlingStyle.RIGHT_ARM_MEDIUM -> Res.string.bowling_style_right_arm_medium
        BowlingStyle.RIGHT_ARM_OFF_SPIN -> Res.string.bowling_style_right_arm_off_spin
        BowlingStyle.RIGHT_ARM_LEG_SPIN -> Res.string.bowling_style_right_arm_leg_spin
        BowlingStyle.LEFT_ARM_FAST -> Res.string.bowling_style_left_arm_fast
        BowlingStyle.LEFT_ARM_MEDIUM -> Res.string.bowling_style_left_arm_medium
        BowlingStyle.LEFT_ARM_ORTHODOX -> Res.string.bowling_style_left_arm_orthodox
        BowlingStyle.LEFT_ARM_WRIST_SPIN -> Res.string.bowling_style_left_arm_wrist_spin
    }

val ProfileError.label: StringResource
    get() = when (this) {
        ProfileError.NAME_MISSING -> Res.string.profile_error_name_missing
        ProfileError.NAME_TOO_LONG -> Res.string.profile_error_name_too_long
        ProfileError.PHONE_INVALID -> Res.string.profile_error_phone_invalid
        ProfileError.CITY_TOO_LONG -> Res.string.profile_error_city_too_long
    }

val SignInError.label: StringResource
    get() = when (this) {
        SignInError.PHONE_INVALID -> Res.string.signin_error_phone_invalid
        SignInError.CODE_INCOMPLETE -> Res.string.signin_error_code_incomplete
        SignInError.OFFLINE -> Res.string.signin_error_offline
        SignInError.TOO_MANY_ATTEMPTS -> Res.string.signin_error_too_many_attempts
        SignInError.ACCOUNT_EXISTS -> Res.string.signin_error_account_exists
        SignInError.SMS_REGION -> Res.string.signin_error_sms_region
        SignInError.SMS_BILLING -> Res.string.signin_error_sms_billing
        SignInError.METHOD_DISABLED -> Res.string.signin_error_method_disabled
        SignInError.ACCOUNT_DISABLED -> Res.string.signin_error_account_disabled
        SignInError.CODE_WRONG -> Res.string.signin_error_code_wrong
        SignInError.CODE_EXPIRED -> Res.string.signin_error_code_expired
        SignInError.SMS_QUOTA -> Res.string.signin_error_sms_quota
        SignInError.UNKNOWN -> Res.string.signin_error_unknown
    }

/** The mark for a playing role, on option cards, chips and hero pills. */
val PlayerRole.icon: DrawableResource
    get() = when (this) {
        PlayerRole.BATTER -> DesiIcons.Bat
        PlayerRole.BOWLER -> DesiIcons.Ball
        PlayerRole.ALL_ROUNDER -> DesiIcons.Cricket
        PlayerRole.WICKET_KEEPER -> DesiIcons.Glove
    }

val DismissalKind.label: StringResource
    get() = when (this) {
        DismissalKind.BOWLED -> Res.string.scoring_dismissal_bowled
        DismissalKind.CAUGHT -> Res.string.scoring_dismissal_caught
        DismissalKind.LBW -> Res.string.scoring_dismissal_lbw
        DismissalKind.RUN_OUT -> Res.string.scoring_dismissal_run_out
        DismissalKind.STUMPED -> Res.string.scoring_dismissal_stumped
        DismissalKind.HIT_WICKET -> Res.string.scoring_dismissal_hit_wicket
        DismissalKind.OBSTRUCTING_FIELD -> Res.string.scoring_dismissal_obstructing
        DismissalKind.HIT_BALL_TWICE -> Res.string.scoring_dismissal_hit_twice
        DismissalKind.OTHER -> Res.string.scoring_dismissal_other
    }

val MatchSetupError.label: StringResource
    get() = when (this) {
        MatchSetupError.TEAM_NAME_MISSING -> Res.string.new_match_error_team_name_missing
        MatchSetupError.SAME_TEAM_NAMES -> Res.string.new_match_error_same_team_names
        MatchSetupError.TEAM_NAME_TOO_LONG -> Res.string.new_match_error_team_name_too_long
        MatchSetupError.TOO_FEW_PLAYERS -> Res.string.new_match_error_too_few_players
        MatchSetupError.TOO_MANY_PLAYERS -> Res.string.new_match_error_too_many_players
        MatchSetupError.PLAYER_NAME_INVALID -> Res.string.new_match_error_player_name_invalid
        MatchSetupError.OVERS_OUT_OF_RANGE -> Res.string.new_match_error_overs
        MatchSetupError.RULES_INVALID -> Res.string.new_match_error_rules
        MatchSetupError.PLAYER_TWICE -> Res.string.new_match_error_player_twice
        MatchSetupError.CAPTAIN_INVALID -> Res.string.new_match_error_captain
    }

val AbandonReason.label: StringResource
    get() = when (this) {
        AbandonReason.RAIN -> Res.string.end_reason_rain
        AbandonReason.BAD_LIGHT -> Res.string.end_reason_light
        AbandonReason.GROUND -> Res.string.end_reason_ground
        AbandonReason.OTHER -> Res.string.end_reason_other
    }
