package com.harsh.desicrics.designsystem.icon

import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.ic_add
import com.harsh.desicrics.designsystem.resources.ic_arrow_back
import com.harsh.desicrics.designsystem.resources.ic_arrow_forward
import com.harsh.desicrics.designsystem.resources.ic_check_circle
import com.harsh.desicrics.designsystem.resources.ic_close
import com.harsh.desicrics.designsystem.resources.ic_cricket_bat
import com.harsh.desicrics.designsystem.resources.ic_front_hand
import com.harsh.desicrics.designsystem.resources.ic_google_logo
import com.harsh.desicrics.designsystem.resources.ic_location_on
import com.harsh.desicrics.designsystem.resources.ic_person
import com.harsh.desicrics.designsystem.resources.ic_remove
import com.harsh.desicrics.designsystem.resources.ic_sports_baseball
import com.harsh.desicrics.designsystem.resources.ic_sports_cricket
import com.harsh.desicrics.designsystem.resources.ic_sync
import com.harsh.desicrics.designsystem.resources.ill_ball_hero
import com.harsh.desicrics.designsystem.resources.ill_ball_mark
import org.jetbrains.compose.resources.DrawableResource

/**
 * Every icon the app uses. Material Symbols Rounded (fill 0, weight 400, grade 0, 24dp), downloaded as
 * Android XML with the tint removed; see docs/design-system.md › Icons for how to add one. [Bat] is
 * custom, drawn on the same grid.
 *
 * Use with `Icon(painterResource(DesiIcons.Back), contentDescription = …)`.
 */
object DesiIcons {
    val Back: DrawableResource = Res.drawable.ic_arrow_back
    val Forward: DrawableResource = Res.drawable.ic_arrow_forward
    val CheckCircle: DrawableResource = Res.drawable.ic_check_circle
    val Person: DrawableResource = Res.drawable.ic_person
    val Cricket: DrawableResource = Res.drawable.ic_sports_cricket
    val Sync: DrawableResource = Res.drawable.ic_sync
    val Location: DrawableResource = Res.drawable.ic_location_on
    val Add: DrawableResource = Res.drawable.ic_add
    val Remove: DrawableResource = Res.drawable.ic_remove
    val Close: DrawableResource = Res.drawable.ic_close

    // Playing roles. Ball is Material's sports_baseball (a ball with a seam); Glove is front_hand.
    val Bat: DrawableResource = Res.drawable.ic_cricket_bat
    val Ball: DrawableResource = Res.drawable.ic_sports_baseball
    val Glove: DrawableResource = Res.drawable.ic_front_hand
}

/** Illustrations (multi-colour, not tinted). Draw with `Image(painterResource(...))`. */
object DesiIllustrations {
    /** The app icon's ball and speed lines, for brand headers. 108×108 with the art centred. */
    val BallMark: DrawableResource = Res.drawable.ill_ball_mark

    /** The same ball and speed lines cropped tight to the art (54:34), for drawing large as hero art. */
    val BallHero: DrawableResource = Res.drawable.ill_ball_hero

    /** Google's "G" for the Google sign-in button. Must keep its colours, so pass `tintLeadingIcon = false`. */
    val GoogleLogo: DrawableResource = Res.drawable.ic_google_logo
}
