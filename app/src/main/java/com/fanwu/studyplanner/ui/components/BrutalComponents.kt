package com.fanwu.studyplanner.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fanwu.studyplanner.ui.theme.*

@Composable
fun brutalBorder(): Dp = if (LocalConfiguration.current.screenWidthDp >= 600) 4.dp else 2.dp

@Composable
fun BrutalPage(
    title: String,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = true,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxSize().background(BrutalPaper).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(BrutalWhite)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (onBack == null) 20.dp else 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onBack != null) BrutalIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
                }
                actions()
            }
            HorizontalDivider(thickness = 2.dp, color = BrutalBlack)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 1000.dp).fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp), content = content
            )
        }
    }
}

@Composable
fun BrutalPanel(
    modifier: Modifier = Modifier,
    color: Color = BrutalWhite,
    shadow: Boolean = false,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val d = if (!shadow) 0.dp else if (LocalConfiguration.current.screenWidthDp >= 600) 8.dp else 4.dp
    val border = brutalBorder()
    Box(modifier.padding(end = d, bottom = d)) {
        Column(
            Modifier.fillMaxWidth().drawBehind {
                if (d > 0.dp) drawRect(BrutalBlack, topLeft = Offset(d.toPx(), d.toPx()), size = size)
            }.background(color).border(border, BrutalBlack).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content
        )
    }
}

/** 命中区保持静止；只移动表面，按压位置始终相对静止原点计算。 */
@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = BrutalPink,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val d = if (LocalConfiguration.current.screenWidthDp >= 600) 6.dp else 4.dp
    val target = when { !enabled -> 0.dp; pressed -> d; hovered -> (-4).dp; else -> 0.dp }
    val motion by animateDpAsState(target, tween(if (pressed || !enabled) 0 else 150, easing = LinearOutSlowInEasing), label = "按钮表面")
    val faceColor = when { !enabled -> BrutalDisabled; hovered && !pressed -> BrutalYellow; else -> color }
    val ink = if (faceColor == BrutalBlack || faceColor == BrutalDanger) BrutalWhite else BrutalBlack
    Box(
        modifier.padding(4.dp).padding(end = d, bottom = d)
            .hoverable(interaction, enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                if (enabled && !pressed) drawRect(BrutalBlack, Offset(d.toPx(), d.toPx()), size)
                if (focused) {
                    val gap = 4.dp.toPx()
                    drawRect(BrutalBlack, Offset(-gap, -gap), androidx.compose.ui.geometry.Size(size.width + 2 * gap, size.height + 2 * gap), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
    ) {
        Row(
            Modifier.graphicsLayer { translationX = motion.toPx(); translationY = motion.toPx() }
                .fillMaxWidth().defaultMinSize(minHeight = 52.dp).background(faceColor)
                .border(brutalBorder(), BrutalBlack).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = ink); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = ink)
        }
    }
}

@Composable
fun BrutalIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .background(if (pressed || hovered) BrutalYellow else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, BrutalBlack) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = BrutalBlack, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun BrutalTag(text: String, color: Color = BrutalWhite, modifier: Modifier = Modifier) {
    Text(text, modifier.background(color).border(1.dp, BrutalBlack).padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = BrutalBlack)
}

@Composable
fun BrutalSection(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        trailing()
    }
}

@Composable
fun BrutalChoice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.defaultMinSize(minHeight = 48.dp).background(if (selected) BrutalYellow else BrutalWhite)
        .border(2.dp, BrutalBlack).selectable(selected, onClick = onClick, role = Role.Tab).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BrutalCheckbox(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Box(Modifier.size(48.dp).semantics { contentDescription = label }.toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp).background(if (checked) BrutalLime else BrutalWhite).border(2.dp, BrutalBlack), contentAlignment = Alignment.Center) {
            if (checked) Icon(Icons.Outlined.Check, null, tint = BrutalBlack, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun BrutalField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5, shape = RectangleShape, visualTransformation = visualTransformation,
        trailingIcon = trailing, keyboardOptions = keyboardOptions, colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrutalBlack, unfocusedBorderColor = BrutalBlack,
            focusedLabelColor = BrutalBlack, unfocusedLabelColor = BrutalMutedText,
            focusedContainerColor = BrutalWhite, unfocusedContainerColor = BrutalWhite, cursorColor = BrutalBlack
        ))
}

@Composable
fun BrutalEmpty(title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    BrutalPanel {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = BrutalMutedText)
        if (action != null) BrutalButton(action, onAction, Modifier.fillMaxWidth(), color = BrutalYellow)
    }
}

@Composable
fun BrutalMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun BrutalLinkRow(title: String, description: String, onClick: () -> Unit, badge: String? = null, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = BrutalBlack)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = BrutalMutedText)
        }
        if (badge != null) BrutalTag(badge)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp), tint = BrutalBlack)
    }
}

@Composable
fun BrutalDialog(
    title: String, onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val height = (LocalConfiguration.current.screenHeightDp * .85f).dp
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.imePadding().widthIn(max = 560.dp).fillMaxWidth().padding(20.dp)
            .heightIn(max = height).background(BrutalWhite).border(brutalBorder(), BrutalBlack).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                BrutalIconButton(Icons.Outlined.Close, "关闭", onDismiss)
            }
            HorizontalDivider(color = BrutalBlack)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
fun BrutalConfirm(title: String, description: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    BrutalDialog(title, onDismiss, actions = {
        BrutalButton(confirm, onConfirm, color = if (danger) BrutalDanger else BrutalPink)
        TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("取消", color = BrutalBlack) }
    }) { Text(description, style = MaterialTheme.typography.bodyLarge) }
}
