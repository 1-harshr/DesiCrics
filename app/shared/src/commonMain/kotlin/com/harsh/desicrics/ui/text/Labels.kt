package com.harsh.desicrics.ui.text

import com.harsh.desicrics.api.ProfileError
import com.harsh.desicrics.auth.SignInError
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.PlayerRole
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
