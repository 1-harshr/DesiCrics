package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

/** A user row keyed by Firebase Auth UID. Auth identity comes from Firebase; everything cricket-related lives here. */
@Serializable
data class UserProfile(
    val uid: String,
    val name: String,
    /** Typed in during onboarding; not verified until phone OTP linking is added. */
    val phone: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val city: String = "",
    val role: PlayerRole = PlayerRole.BATTER,
    val battingStyle: BattingStyle = BattingStyle.RIGHT_HAND,
    val bowlingStyle: BowlingStyle = BowlingStyle.NONE,
    val createdAt: Long = 0,
)

@Serializable
enum class PlayerRole(val displayName: String) {
    BATTER("Batter"),
    BOWLER("Bowler"),
    ALL_ROUNDER("All-rounder"),
    WICKET_KEEPER("Wicket-keeper"),
}

@Serializable
enum class BattingStyle(val displayName: String) {
    RIGHT_HAND("Right-hand bat"),
    LEFT_HAND("Left-hand bat"),
}

@Serializable
enum class BowlingStyle(val displayName: String) {
    NONE("Doesn't bowl"),
    RIGHT_ARM_FAST("Right-arm fast"),
    RIGHT_ARM_MEDIUM("Right-arm medium"),
    RIGHT_ARM_OFF_SPIN("Right-arm off spin"),
    RIGHT_ARM_LEG_SPIN("Right-arm leg spin"),
    LEFT_ARM_FAST("Left-arm fast"),
    LEFT_ARM_MEDIUM("Left-arm medium"),
    LEFT_ARM_ORTHODOX("Left-arm orthodox"),
    LEFT_ARM_WRIST_SPIN("Left-arm wrist spin"),
}
