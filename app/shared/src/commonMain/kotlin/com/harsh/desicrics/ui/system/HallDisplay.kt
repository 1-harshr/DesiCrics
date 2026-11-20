package com.harsh.desicrics.ui.system

import androidx.compose.runtime.Composable

/**
 * While composed, keeps the screen on, and with [landscape] turns it sideways: the auction hall runs on one
 * device facing a room, often on a TV or projector. Both go back to normal when it leaves.
 */
@Composable
expect fun HallDisplay(landscape: Boolean)
