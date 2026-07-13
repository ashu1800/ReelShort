package com.reelshort.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.reelshort.app.ui.theme.Divider
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary

/**
 * 统一的金色描边输入框配色。消除 LoginTextField 与 SearchScreen 的逐字重复。
 */
@Composable
internal fun goldTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGold,
    unfocusedBorderColor = Divider,
    focusedLabelColor = PrimaryGold,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = PrimaryGold,
)

internal enum class TextFieldKind {
    TEXT,
    PHONE,
    VERIFICATION_CODE,
    POINT_AMOUNT,
    PASSWORD,
}

internal fun keyboardTypeFor(kind: TextFieldKind): KeyboardType =
    when (kind) {
        TextFieldKind.TEXT -> KeyboardType.Text
        TextFieldKind.PHONE -> KeyboardType.Phone
        TextFieldKind.VERIFICATION_CODE -> KeyboardType.NumberPassword
        TextFieldKind.POINT_AMOUNT -> KeyboardType.Number
        TextFieldKind.PASSWORD -> KeyboardType.Password
    }

internal fun passwordFieldsAllowVisibilityToggle(): Boolean = true

@OptIn(ExperimentalComposeUiApi::class)
internal fun autofillTypesFor(kind: TextFieldKind): List<AutofillType> =
    when (kind) {
        TextFieldKind.PHONE -> listOf(AutofillType.PhoneNumber)
        TextFieldKind.PASSWORD -> listOf(AutofillType.Password)
        else -> emptyList()
    }

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.withAutofill(
    kind: TextFieldKind,
    onFill: (String) -> Unit,
): Modifier = composed {
    val types = autofillTypesFor(kind)
    if (types.isEmpty()) {
        return@composed this
    }
    val autofill = LocalAutofill.current
    val node = remember(types, onFill) { AutofillNode(autofillTypes = types, onFill = onFill) }
    val tree = LocalAutofillTree.current
    DisposableEffect(tree, node) {
        tree += node
        onDispose { tree.children.remove(node.id) }
    }
    onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
        .onFocusChanged {
            if (it.isFocused) autofill?.requestAutofillForNode(node) else autofill?.cancelAutofillForNode(node)
        }
}

@Composable
internal fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isPassword: Boolean = false,
    kind: TextFieldKind = if (isPassword) TextFieldKind.PASSWORD else TextFieldKind.TEXT,
    imeAction: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().withAutofill(kind, onValueChange),
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardTypeFor(kind), imeAction = imeAction),
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "Hide $label" else "Show $label",
                    )
                }
            }
        } else {
            null
        },
        colors = goldTextFieldColors(),
    )
}
