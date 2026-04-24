package com.akiba.app.ui.components.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.akiba.app.ui.theme.*

@Composable
fun AkibaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    isPassword: Boolean = false,
    error: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val primary     = MaterialTheme.colorScheme.primary
    val errorColor  = MaterialTheme.colorScheme.error
    val surface     = MaterialTheme.colorScheme.surface
    val onSurface   = MaterialTheme.colorScheme.onSurface
    val glassBorder = MaterialTheme.akibaColors.glassBorder

    var isFocused       by remember { mutableStateOf(false) }
    var showPassword    by remember { mutableStateOf(false) }

    // Animate border color: error → focused → idle
    val borderColor by animateColorAsState(
        targetValue = when {
            error != null -> errorColor
            isFocused     -> primary
            else          -> glassBorder
        },
        animationSpec = tween(200),
        label = "borderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused || error != null) 2.dp else 1.dp,
        label = "borderWidth",
    )

    // Floating label animation
    val labelScale by animateFloatAsState(
        targetValue = if (isFocused || value.isNotEmpty()) 0.82f else 1f,
        label = "labelScale",
    )
    val labelOffsetY by animateDpAsState(
        targetValue = if (isFocused || value.isNotEmpty()) (-24).dp else 0.dp,
        label = "labelOffsetY",
    )
    val labelColor by animateColorAsState(
        targetValue = if (isFocused) primary else onSurface.copy(alpha = 0.5f),
        label = "labelColor",
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AkibaShapes.input)
                .background(surface.copy(alpha = 0.6f))
                .border(borderWidth, borderColor, AkibaShapes.input)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Floating label sits above the input content
            if (label.isNotEmpty()) {
                Text(
                    text     = label,
                    color    = labelColor,
                    fontSize = (14 * labelScale).sp,
                    modifier = Modifier.offset(y = labelOffsetY),
                    fontFamily = DmSansFontFamily,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = if (label.isNotEmpty()) 12.dp else 0.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint     = if (isFocused) primary else onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }

                BasicTextField(
                    value          = value,
                    onValueChange  = onValueChange,
                    enabled        = enabled,
                    singleLine     = singleLine,
                    cursorBrush    = SolidColor(primary),
                    textStyle      = MaterialTheme.typography.bodyLarge.copy(
                        color      = MaterialTheme.colorScheme.onSurface,
                        fontFamily = DmSansFontFamily,
                    ),
                    visualTransformation = when {
                        isPassword && !showPassword -> PasswordVisualTransformation()
                        else -> VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction    = imeAction,
                    ),
                    keyboardActions = KeyboardActions(
                        onAny = { onImeAction() }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused },
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text       = placeholder,
                                    color      = onSurface.copy(alpha = 0.3f),
                                    fontSize   = 15.sp,
                                    fontFamily = DmSansFontFamily,
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Password toggle or custom trailing icon
                if (isPassword) {
                    TextButton(
                        onClick = { showPassword = !showPassword },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text     = if (showPassword) "Hide" else "Show",
                            color    = primary,
                            fontSize = 12.sp,
                            fontFamily = DmSansFontFamily,
                        )
                    }
                } else {
                    trailingIcon?.invoke()
                }
            }
        }

        // Error message slides in below the field
        AnimatedVisibility(
            visible = error != null,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint               = errorColor,
                    modifier           = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text       = error ?: "",
                    color      = errorColor,
                    fontSize   = 11.sp,
                    fontFamily = DmSansFontFamily,
                )
            }
        }
    }
}
