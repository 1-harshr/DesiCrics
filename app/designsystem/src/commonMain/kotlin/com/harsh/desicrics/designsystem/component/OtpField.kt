package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.theme.DesiTheme

/**
 * A one-time code entered into [length] separate boxes. It is a single text field underneath, so paste
 * and SMS autofill still fill every box. Only digits are accepted.
 */
@Composable
fun OtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    val colors = DesiTheme.colors
    BasicTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(length)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth(),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                repeat(length) { index ->
                    val digit = value.getOrNull(index)
                    // The box the next digit goes into gets the brand outline.
                    val active = enabled && index == value.length.coerceAtMost(length - 1)
                    val outline = when {
                        isError -> colors.event.wicket
                        active -> colors.brand
                        else -> colors.line
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                            .clip(DesiTheme.radius.md)
                            .background(if (digit != null) colors.surface else colors.sunk)
                            .border(if (active || isError) 2.dp else 1.dp, outline, DesiTheme.radius.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(digit?.toString().orEmpty(), style = DesiTheme.type.scoreMd, color = colors.ink)
                    }
                }
            }
        },
    )
}
