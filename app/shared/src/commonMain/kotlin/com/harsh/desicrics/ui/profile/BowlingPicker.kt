package com.harsh.desicrics.ui.profile

import com.harsh.desicrics.model.BowlingStyle

// The bowling picker asks three small questions (do you bowl: pace or spin? which arm? what type?) instead
// of showing all nine styles at once. These map between the answers and BowlingStyle.

enum class BowlingKind { NONE, PACE, SPIN }

enum class BowlingArm { RIGHT, LEFT }

val BowlingStyle.kind: BowlingKind
    get() = when (this) {
        BowlingStyle.NONE -> BowlingKind.NONE
        BowlingStyle.RIGHT_ARM_FAST, BowlingStyle.RIGHT_ARM_MEDIUM,
        BowlingStyle.LEFT_ARM_FAST, BowlingStyle.LEFT_ARM_MEDIUM -> BowlingKind.PACE
        else -> BowlingKind.SPIN
    }

val BowlingStyle.arm: BowlingArm
    get() = when (this) {
        BowlingStyle.LEFT_ARM_FAST, BowlingStyle.LEFT_ARM_MEDIUM,
        BowlingStyle.LEFT_ARM_ORTHODOX, BowlingStyle.LEFT_ARM_WRIST_SPIN -> BowlingArm.LEFT
        else -> BowlingArm.RIGHT
    }

/** The types to pick from for a kind and arm, most common first. Empty for [BowlingKind.NONE]. */
fun bowlingStyles(kind: BowlingKind, arm: BowlingArm): List<BowlingStyle> = when (kind) {
    BowlingKind.NONE -> emptyList()
    BowlingKind.PACE -> when (arm) {
        BowlingArm.RIGHT -> listOf(BowlingStyle.RIGHT_ARM_FAST, BowlingStyle.RIGHT_ARM_MEDIUM)
        BowlingArm.LEFT -> listOf(BowlingStyle.LEFT_ARM_FAST, BowlingStyle.LEFT_ARM_MEDIUM)
    }
    BowlingKind.SPIN -> when (arm) {
        BowlingArm.RIGHT -> listOf(BowlingStyle.RIGHT_ARM_OFF_SPIN, BowlingStyle.RIGHT_ARM_LEG_SPIN)
        BowlingArm.LEFT -> listOf(BowlingStyle.LEFT_ARM_ORTHODOX, BowlingStyle.LEFT_ARM_WRIST_SPIN)
    }
}

/**
 * The style after changing the kind or arm. Keeps the same position in the list where it can, so switching
 * a right-arm medium pacer to left arm gives left-arm medium.
 */
fun BowlingStyle.withChoice(kind: BowlingKind = this.kind, arm: BowlingArm = this.arm): BowlingStyle {
    val position = bowlingStyles(this.kind, this.arm).indexOf(this).coerceAtLeast(0)
    val options = bowlingStyles(kind, arm)
    return options.getOrNull(position) ?: options.firstOrNull() ?: BowlingStyle.NONE
}
