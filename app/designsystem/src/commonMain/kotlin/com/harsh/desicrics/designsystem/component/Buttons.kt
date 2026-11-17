package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The one main action on a screen. Shows a spinner instead of the label while [loading]. Disabled stays
 * green but faded, so the next step still reads as the main action. [trailingIcon] is usually
 * [DesiIcons.Forward] for "go to the next step".
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingIcon: DrawableResource? = null,
) {
    val colors = DesiTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = DesiTheme.size.button),
        shape = DesiTheme.radius.full,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.brand,
            contentColor = colors.onBrand,
            disabledContainerColor = colors.brand.copy(alpha = if (loading) 1f else 0.32f),
            disabledContentColor = colors.onBrand.copy(alpha = if (loading) 1f else 0.85f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = colors.onBrand, strokeWidth = 2.dp)
        } else {
            Text(text, style = DesiTheme.type.button)
            if (trailingIcon != null) {
                Spacer(Modifier.width(DesiTheme.space.s2))
                Icon(painterResource(trailingIcon), contentDescription = null, modifier = Modifier.size(DesiTheme.size.iconSmall))
            }
        }
    }
}

/**
 * A soft, filled secondary action with a leading icon ("Continue with Google"). Quieter than
 * [PrimaryButton], friendlier than an outline.
 */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
    /** False for brand logos (e.g. Google's "G") that must keep their own colours. */
    tintLeadingIcon: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = DesiTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = DesiTheme.size.button),
        shape = DesiTheme.radius.full,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.sunk,
            contentColor = colors.ink,
            disabledContainerColor = colors.sunk,
            disabledContentColor = colors.muted,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = colors.brand, strokeWidth = 2.dp)
            return@Button
        }
        if (leadingIcon != null) {
            Icon(
                painterResource(leadingIcon),
                contentDescription = null,
                tint = if (tintLeadingIcon) colors.brand else Color.Unspecified,
                modifier = Modifier.size(DesiTheme.size.iconSmall),
            )
            Spacer(Modifier.width(DesiTheme.space.s3))
        }
        Text(text, style = DesiTheme.type.button)
    }
}

/** Secondary actions next to or below a [PrimaryButton]. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = DesiTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = DesiTheme.size.button),
        shape = DesiTheme.radius.full,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.5.dp, colors.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink, disabledContentColor = colors.muted),
    ) {
        Text(text, style = DesiTheme.type.button)
    }
}

private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
