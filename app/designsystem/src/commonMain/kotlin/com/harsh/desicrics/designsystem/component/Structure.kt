package com.harsh.desicrics.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.resources.Res
import com.harsh.desicrics.designsystem.resources.ds_back
import com.harsh.desicrics.designsystem.theme.DesiTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Screen frame: title bar with an optional back button, on the `bg` colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesiScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = DesiTheme.colors
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = DesiTheme.type.titleMd, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(DesiIcons.Back), contentDescription = stringResource(Res.string.ds_back))
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.bg,
                    scrolledContainerColor = colors.surface,
                    titleContentColor = colors.ink,
                    navigationIconContentColor = colors.ink,
                    actionIconContentColor = colors.ink,
                ),
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DesiTheme.colors.brand)
    }
}

/** A labelled text field with an optional helper or error line underneath. */
@Composable
fun DesiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    prefix: String? = null,
) {
    val colors = DesiTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = colors.muted) } },
        prefix = prefix?.let { { Text(it, style = DesiTheme.type.body, color = colors.ink) } },
        supportingText = (error ?: supportingText)?.let { { Text(it) } },
        isError = error != null,
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = DesiTheme.type.bodyLg,
        shape = DesiTheme.radius.md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.brand,
            unfocusedBorderColor = colors.line,
            focusedLabelColor = colors.brand,
            unfocusedLabelColor = colors.muted,
            cursorColor = colors.brand,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            errorBorderColor = colors.event.wicket,
            errorLabelColor = colors.event.wicket,
            errorSupportingTextColor = colors.event.wicket,
        ),
    )
}

/** Single-choice chips for enum pickers (role, batting style, overs). */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DesiTheme.colors
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = DesiTheme.type.body) },
                shape = DesiTheme.radius.full,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surface,
                    labelColor = colors.ink,
                    selectedContainerColor = colors.brandSoft,
                    selectedLabelColor = colors.brand,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = option == selected,
                    borderColor = colors.line,
                    selectedBorderColor = colors.brand,
                ),
            )
        }
    }
}

/**
 * A bottom sheet listing options, e.g. dismissal types. Feature code builds flows such as the wicket
 * sheet on top of this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(
    title: String,
    options: List<String>,
    onSelect: (index: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DesiTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface, shape = DesiTheme.radius.lg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = DesiTheme.space.s5).padding(bottom = DesiTheme.space.s6),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4),
        ) {
            Text(title, style = DesiTheme.type.titleMd, color = colors.ink)
            ChoiceChips(
                options = options.indices.toList(),
                selected = null,
                label = { options[it] },
                onSelect = onSelect,
            )
        }
    }
}
