package com.spaceclipboard.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spaceclipboard.app.domain.model.ContentType
import com.spaceclipboard.app.ui.theme.Blue
import com.spaceclipboard.app.ui.theme.Cyan
import com.spaceclipboard.app.ui.theme.Error
import com.spaceclipboard.app.ui.theme.FocusRing
import com.spaceclipboard.app.ui.theme.Outline
import com.spaceclipboard.app.ui.theme.Panel
import com.spaceclipboard.app.ui.theme.PanelFocused
import com.spaceclipboard.app.ui.theme.PanelRaised
import com.spaceclipboard.app.ui.theme.SpaceShapes
import com.spaceclipboard.app.ui.theme.Success
import com.spaceclipboard.app.ui.theme.TextMuted
import com.spaceclipboard.app.ui.theme.TextPrimary
import com.spaceclipboard.app.ui.theme.TextSecondary
import com.spaceclipboard.app.ui.theme.Warning

@Composable
fun SpacePanel(
    modifier: Modifier = Modifier,
    shape: Shape = SpaceShapes.large,
    color: Color = Panel,
    borderColor: Color = Outline,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SpaceFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = SpaceShapes.large,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    focusRequester: FocusRequester? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> Panel.copy(alpha = 0.45f)
            pressed -> PanelFocused
            focused -> PanelRaised
            else -> Panel
        },
        animationSpec = tween(90),
        label = "focusBackground",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> Outline.copy(alpha = 0.35f)
            focused -> FocusRing
            pressed -> Cyan
            else -> Outline
        },
        animationSpec = tween(90),
        label = "focusBorder",
    )
    Column(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            )
            .semantics {
                stateDescription = when {
                    !enabled -> "Indisponível"
                    focused -> "Focado"
                    else -> "Disponível"
                }
            }
            .padding(contentPadding),
        content = content,
    )
}

enum class ButtonVariant { Primary, Secondary, Ghost, Danger }

@Composable
fun SpaceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    variant: ButtonVariant = ButtonVariant.Secondary,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val baseColor = when (variant) {
        ButtonVariant.Primary -> Blue
        ButtonVariant.Secondary -> PanelRaised
        ButtonVariant.Ghost -> Color.Transparent
        ButtonVariant.Danger -> Error.copy(alpha = 0.14f)
    }
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> baseColor.copy(alpha = 0.3f)
            pressed -> when (variant) {
                ButtonVariant.Primary -> Blue.copy(alpha = 0.82f)
                ButtonVariant.Danger -> Error.copy(alpha = 0.25f)
                else -> PanelFocused
            }
            focused -> when (variant) {
                ButtonVariant.Primary -> Blue
                ButtonVariant.Danger -> Error.copy(alpha = 0.24f)
                else -> PanelFocused
            }
            else -> baseColor
        },
        animationSpec = tween(80),
        label = "buttonBackground",
    )
    val border = when {
        !enabled -> Outline.copy(alpha = 0.3f)
        focused -> FocusRing
        variant == ButtonVariant.Ghost -> Color.Transparent
        variant == ButtonVariant.Danger -> Error.copy(alpha = 0.5f)
        else -> Outline
    }
    val contentColor = when (variant) {
        ButtonVariant.Primary -> Color.White
        ButtonVariant.Danger -> Error
        ButtonVariant.Ghost -> TextSecondary
        ButtonVariant.Secondary -> TextPrimary
    }.let { if (enabled) it else it.copy(alpha = 0.5f) }

    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription ?: text
                stateDescription = if (focused) "Focado" else "Disponível"
            }
            .alpha(if (enabled) 1f else 0.72f)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Cyan) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        } else {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun TypeBadge(type: ContentType, modifier: Modifier = Modifier) {
    val color = if (type == ContentType.URL) Cyan else TextSecondary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(
                if (type == ContentType.URL) com.spaceclipboard.app.R.drawable.ic_link
                else com.spaceclipboard.app.R.drawable.ic_text,
            ),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = if (type == ContentType.URL) "LINK" else "TEXTO",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun InfoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.width(92.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CodeDisplay(code: String, modifier: Modifier = Modifier) {
    val formatted = if (code.length == 6) "${code.take(3)} ${code.takeLast(3)}" else code
    Text(
        text = formatted,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
        letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing,
        color = Cyan,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(vertical = 28.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Cyan.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Cyan, modifier = Modifier.size(28.dp))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FeedbackBanner(
    message: String,
    success: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (success) Success else Warning
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(
                if (success) com.spaceclipboard.app.R.drawable.ic_check_circle
                else com.spaceclipboard.app.R.drawable.ic_error,
            ),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (success) Success else Warning,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < 620.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                if (actions != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.focusGroup(),
                        content = actions,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                if (actions != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.focusGroup(),
                        content = actions,
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalGap(height: Dp) = Spacer(Modifier.height(height))
