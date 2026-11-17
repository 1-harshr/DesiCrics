package com.harsh.desicrics.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.painterResource

/**
 * Mobile number input: a country pill, then the number in large scoreboard digits grouped as the user
 * types ("98765 43210"). [value] holds digits only. A tick appears once [length] digits are in.
 *
 * [label] sits above the box rather than inside it, so the digits get the whole field.
 */
@Composable
fun PhoneNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    countryCode: String,
    countryLabel: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    length: Int = 10,
    enabled: Boolean = true,
    error: String? = null,
    onDone: () -> Unit = {},
) {
    val colors = DesiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val complete = value.length == length
    val outline = when {
        error != null -> colors.event.wicket
        focused -> colors.brand
        else -> colors.sunk
    }
    val digitStyle = DesiTheme.type.scoreMd.copy(fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = 0.04.em)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionLabel(label)
        BasicTextField(
            value = value,
            onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(length)) },
            enabled = enabled,
            singleLine = true,
            textStyle = digitStyle.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.brand),
            visualTransformation = GroupedDigits,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(DesiTheme.radius.lg)
                        .background(if (focused) colors.surface else colors.sunk)
                        .border(if (focused || error != null) 2.dp else 1.dp, outline, DesiTheme.radius.lg)
                        .padding(start = DesiTheme.space.s2, end = DesiTheme.space.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Country pill
                    Row(
                        Modifier
                            .clip(DesiTheme.radius.md)
                            .background(colors.brandSoft)
                            .padding(horizontal = DesiTheme.space.s3, vertical = DesiTheme.space.s2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(countryLabel, style = DesiTheme.type.label, color = colors.brand)
                        Text(countryCode, style = DesiTheme.type.bodyStrong, color = colors.ink)
                    }
                    Box(Modifier.padding(horizontal = DesiTheme.space.s3).width(1.dp).height(28.dp).background(colors.line))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, style = digitStyle, color = colors.muted.copy(alpha = 0.55f))
                        field()
                    }
                    AnimatedVisibility(complete && error == null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                        Icon(painterResource(DesiIcons.CheckCircle), contentDescription = null, tint = colors.brand, modifier = Modifier.size(DesiTheme.size.icon))
                    }
                }
            },
        )
        if (error != null) Text(error, style = DesiTheme.type.caption, color = colors.event.wicket)
    }
}

/** Shows "9876543210" as "98765 43210", the way Indian mobile numbers are read out. */
private object GroupedDigits : VisualTransformation {
    private const val GROUP = 5

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val grouped = if (raw.length > GROUP) raw.substring(0, GROUP) + " " + raw.substring(GROUP) else raw
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset > GROUP) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = if (offset > GROUP) offset - 1 else offset.coerceAtMost(raw.length)
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}
