package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

/** Bordered text input; the border darkens while focused. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    autoFocus: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(Radius.control)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
        textStyle = type.body.copy(color = colors.textPrimary),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 4,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(colors.textPrimary),
        decorationBox = { inner ->
            Box(
                Modifier
                    .background(colors.surface, shape)
                    .border(1.dp, if (focused) colors.textSecondary else colors.hairline, shape)
                    .padding(horizontal = 10.dp, vertical = 11.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = type.body, color = colors.textMuted)
                inner()
            }
        },
    )
    if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Text input with a small label above it. */
@Composable
fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    testTag: String = label,
    autoFocus: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = ZeroBiteTheme.type.caption.medium(), color = ZeroBiteTheme.colors.textSecondary)
        AppTextField(
            value, onValueChange, placeholder,
            Modifier.fillMaxWidth().testTag(testTag),
            singleLine = singleLine, autoFocus = autoFocus, keyboardOptions = keyboardOptions,
        )
    }
}

data class SearchAccessory(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Search field pinned above the tab bar, with an optional square action (Scan). */
@Composable
fun BottomSearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    accessory: SearchAccessory? = null,
    testTag: String = "search.field",
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.control)

    Row(
        modifier.fillMaxWidth().background(colors.backgroundPrimary).padding(horizontal = Spacing.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(42.dp)
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, if (focused) colors.textSecondary else colors.hairline, shape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text(placeholder, style = type.subheadline, color = colors.textMuted)
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.testTag(testTag),
                    singleLine = true,
                    textStyle = type.subheadline.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.textPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
            }
            if (text.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Cancel,
                    contentDescription = "Clear search",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp).clickable(role = Role.Button) { onTextChange("") },
                )
            }
        }
        if (focused) {
            Text(
                "Cancel",
                style = type.subheadline.medium(),
                color = colors.textPrimary,
                modifier = Modifier.clickable(role = Role.Button) {
                    onTextChange("")
                    focusManager.clearFocus()
                },
            )
        } else if (accessory != null) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(shape)
                    .background(colors.brandOrange)
                    .clickable(role = Role.Button, onClick = accessory.onClick)
                    .semantics { contentDescription = accessory.label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(accessory.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
