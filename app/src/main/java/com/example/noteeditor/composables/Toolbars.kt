package com.example.noteeditor.composables

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorTopAppBar(
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean
) {
    TopAppBar(
        title = { },
        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back") } },
        actions = {
            IconButton(onClick = onUndoClick, enabled = canUndo) {
                Icon(Icons.Default.Undo, "Undo", tint = if (canUndo) LocalContentColor.current else Color.Gray)
            }
            IconButton(onClick = onRedoClick, enabled = canRedo) {
                Icon(Icons.Default.Redo, "Redo", tint = if (canRedo) LocalContentColor.current else Color.Gray)
            }
            TextButton(onClick = onSaveClick) { Text("Lưu") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TransformingBottomToolbar(
    modifier: Modifier = Modifier,
    isKeyboardVisible: Boolean,
    isFormattingMode: Boolean,
    activeStyles: Set<Style>, // [NEW] Receive the set of active styles
    onToggleFormattingMode: () -> Unit,
    onAddImageClick: () -> Unit,
    onAddCheckboxClick: () -> Unit,
    onAddAudioClick: () -> Unit,
    onAddMoreClick: (Boolean) -> Unit,
    onStyleChange: (Style) -> Unit,
    onTextAlignChange: (TextAlign) -> Unit,
    onListStyleChange: () -> Unit,
    onAddSeparator: () -> Unit,
    onFontSizeChange: (TextUnit) -> Unit,
    onTextColorChange: (Color) -> Unit,
    onTextBgColorChange: (Color) -> Unit
) {
    val animSpec = tween<Dp>(durationMillis = 200)
    val horizontalPadding by animateDpAsState(targetValue = if (isKeyboardVisible) 0.dp else 24.dp, animationSpec = animSpec, label = "HorizontalPadding")
    val cornerRadius by animateDpAsState(targetValue = if (isKeyboardVisible) 0.dp else 32.dp, animationSpec = animSpec, label = "CornerRadius")
    val surfaceBottomPadding by animateDpAsState(targetValue = if (isKeyboardVisible) 0.dp else 24.dp, animationSpec = animSpec, label = "SurfaceBottomPadding")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = horizontalPadding)
                .padding(bottom = surfaceBottomPadding),
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            AnimatedContent(
                targetState = isFormattingMode,
                label = "ToolbarStateAnimation",
                transitionSpec = {
                    (slideInHorizontally(animationSpec = tween(250)) { w -> w } + fadeIn(tween(250)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(250)) { w -> -w } + fadeOut(tween(250)))
                        .using(SizeTransform(clip = false))
                }
            ) { isFormatting ->
                if (isFormatting) {
                    FormattingToolbarContent(
                        activeStyles = activeStyles, // [NEW] Pass down active styles
                        onStyleChange = onStyleChange,
                        onTextAlignChange = onTextAlignChange,
                        onListStyleChange = onListStyleChange,
                        onAddSeparator = onAddSeparator,
                        onClose = onToggleFormattingMode,
                        onFontSizeChange = onFontSizeChange,
                        onTextColorChange = onTextColorChange,
                        onTextBgColorChange = onTextBgColorChange
                    )
                } else {
                    MainToolbarContent(
                        onAddImageClick = onAddImageClick,
                        onAddCheckboxClick = onAddCheckboxClick,
                        onAddAudioClick = onAddAudioClick,
                        onToggleFormatting = onToggleFormattingMode,
                        onAddMoreClick = { onAddMoreClick(true) }
                    )
                }
            }
        }
    }
}


@Composable
private fun MainToolbarContent(
    onToggleFormatting: () -> Unit,
    onAddImageClick: () -> Unit,
    onAddCheckboxClick: () -> Unit,
    onAddAudioClick: () -> Unit,
    onAddMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarIconButton(icon = Icons.Default.TextFields, contentDescription = "Text Options", onClick = onToggleFormatting)
        ToolbarIconButton(icon = Icons.Default.Image, contentDescription = "Add Image", onClick = onAddImageClick)
        ToolbarIconButton(icon = Icons.Default.Mic, contentDescription = "Record Audio", onClick = onAddAudioClick)
        ToolbarIconButton(icon = Icons.Default.Checklist, contentDescription = "Add Checkbox", onClick = onAddCheckboxClick)
        ToolbarIconButton(icon = Icons.Default.AddCircleOutline, contentDescription = "Add More", onClick = onAddMoreClick)
    }
}

@Composable
private fun FormattingToolbarContent(
    activeStyles: Set<Style>, // [NEW] Receive active styles
    onClose: () -> Unit,
    onStyleChange: (Style) -> Unit,
    onTextAlignChange: (TextAlign) -> Unit,
    onListStyleChange: () -> Unit,
    onAddSeparator: () -> Unit,
    onFontSizeChange: (TextUnit) -> Unit,
    onTextColorChange: (Color) -> Unit,
    onTextBgColorChange: (Color) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rotation by animateFloatAsState(targetValue = 360f, animationSpec = tween(400), label = "CloseIconRotation")
        ToolbarIconButton(icon = Icons.Default.Close, contentDescription = "Close Formatting", onClick = onClose, modifier = Modifier.rotate(rotation))

        VerticalDivider()

        // [NEW] Check if style is active and pass it to the button
        ToolbarIconButton(icon = Icons.Default.FormatBold, "Bold", onClick = { onStyleChange(Style.BOLD) }, isToggled = activeStyles.contains(Style.BOLD))
        ToolbarIconButton(icon = Icons.Default.FormatItalic, "Italic", onClick = { onStyleChange(Style.ITALIC) }, isToggled = activeStyles.contains(Style.ITALIC))
        ToolbarIconButton(icon = Icons.Default.FormatUnderlined, "Underline", onClick = { onStyleChange(Style.UNDERLINE) }, isToggled = activeStyles.contains(Style.UNDERLINE))
        ToolbarIconButton(icon = Icons.Default.FormatStrikethrough, "Strikethrough", onClick = { onStyleChange(Style.STRIKETHROUGH) }, isToggled = activeStyles.contains(Style.STRIKETHROUGH))

        VerticalDivider()

        FontSizeSelector(onFontSizeChange = onFontSizeChange)

        VerticalDivider()

        ColorSelector(icon = Icons.Default.FormatColorText, onColorSelected = onTextColorChange)
        ColorSelector(icon = Icons.Default.FormatColorFill, onColorSelected = onTextBgColorChange)

        VerticalDivider()

        ToolbarIconButton(icon = Icons.AutoMirrored.Filled.FormatAlignLeft, "Align Left", onClick = { onTextAlignChange(TextAlign.Start) })
        ToolbarIconButton(icon = Icons.Default.FormatAlignCenter, "Align Center", onClick = { onTextAlignChange(TextAlign.Center) })
        ToolbarIconButton(icon = Icons.AutoMirrored.Filled.FormatAlignRight, "Align Right", onClick = { onTextAlignChange(TextAlign.End) })

        VerticalDivider()

        ToolbarIconButton(icon = Icons.Default.FormatListBulleted, "Bulleted List", onClick = onListStyleChange)
        ToolbarIconButton(icon = Icons.Default.HorizontalRule, "Separator", onClick = onAddSeparator)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSizeSelector(onFontSizeChange: (TextUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val fontSizes = listOf(12.sp, 14.sp, 16.sp, 18.sp, 22.sp, 26.sp)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        ToolbarIconButton(
            icon = Icons.Default.FormatSize,
            contentDescription = "Font Size",
            onClick = { expanded = true },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fontSizes.forEach { size ->
                DropdownMenuItem(
                    text = { Text(size.value.toInt().toString()) },
                    onClick = {
                        onFontSizeChange(size)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorSelector(icon: ImageVector, onColorSelected: (Color) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = listOf(
        Color.Black, Color.Gray, Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Transparent
    )

    Box {
        ToolbarIconButton(icon = icon, contentDescription = "Select Color", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable {
                                onColorSelected(color)
                                expanded = false
                            }
                            .border(1.dp, Color.LightGray, CircleShape)
                    )
                }
            }
        }
    }
}


/**
 * [REWORKED] ToolbarIconButton now accepts an isToggled parameter to change its color.
 */
@Composable
fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isToggled: Boolean = false // [NEW]
) {
    // [NEW] Change tint color if the button is toggled
    val tint = if (isToggled) Color(0xFFFFC700) else LocalContentColor.current
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription, tint = tint)
    }
}

@Composable
fun VerticalDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            .padding(vertical = 12.dp)
    )
}

enum class Style {
    BOLD, ITALIC, UNDERLINE, STRIKETHROUGH
}
