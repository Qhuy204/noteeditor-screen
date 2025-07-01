package com.example.noteeditor.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.noteeditor.*

/**
 * [REWORK] The Composable now uses the block's paragraphStyle for the overall text field
 * style, and relies on the AnnotatedString within the TextFieldValue to render
 * character-specific styles (bold, color, etc.).
 */
@Composable
fun TextBlockComposable(
    block: TextBlock,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChange: (FocusState) -> Unit
) {
    // The base style for the text field. It respects the paragraph style (e.g., alignment).
    // Character styles will be overridden by the spans in the AnnotatedString.
    val baseTextStyle = ComposeTextStyle.Default.merge(block.paragraphStyle)

    BasicTextField(
        value = block.value, // The value now contains the AnnotatedString with all its styles
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged(onFocusChange)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        textStyle = baseTextStyle, // Apply the base paragraph style
        decorationBox = { innerTextField ->
            Row(verticalAlignment = Alignment.Top) {
                if (block.isListItem) {
                    Text(
                        text = "• ",
                        modifier = Modifier.padding(end = 8.dp),
                        // The bullet point should inherit the base text style for consistency
                        style = baseTextStyle
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    // Placeholder logic
                    if (block.value.annotatedString.isEmpty()) {
                        Text("Gõ nội dung...", color = Color.Gray, style = baseTextStyle)
                    }
                    innerTextField()
                }
            }
        }
    )
}


// --- Other Composables (Image, Checkbox, etc.) remain unchanged ---

@Composable
fun ImageBlockComposable(
    block: ImageBlock,
    isSelected: Boolean,
    onImageClick: () -> Unit,
    onResize: () -> Unit,
    onDelete: () -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    val imageSizeFraction = if (block.isResized) 0.5f else 1f
    var isDescriptionVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(imageSizeFraction)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onImageClick() }
                .then(
                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    else Modifier
                )
        ) {
            val painter = rememberAsyncImagePainter(model = block.uri)
            val aspectRatio = with(painter.intrinsicSize) {
                if (isSpecified && height != 0f) width / height else 16f / 9f
            }

            Image(
                painter = painter,
                contentDescription = block.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
                contentScale = ContentScale.Fit
            )

            if (isSelected) {
                ImageActionMenu(
                    isResized = block.isResized,
                    onDescribe = { isDescriptionVisible = !isDescriptionVisible },
                    onDraw = { /* TODO: Mở màn hình vẽ */ },
                    onResize = onResize,
                    onCopy = { /* TODO: Copy ảnh vào clipboard */ },
                    onDelete = onDelete
                )
            }
        }
        AnimatedVisibility(visible = isDescriptionVisible || block.description.isNotEmpty()) {
            OutlinedTextField(
                value = block.description,
                onValueChange = onDescriptionChange,
                modifier = Modifier
                    .fillMaxWidth(imageSizeFraction)
                    .padding(top = 4.dp),
                placeholder = { Text("Nhập mô tả ảnh...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Gray,
                    unfocusedBorderColor = Color.LightGray
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
fun BoxScope.ImageActionMenu(
    isResized: Boolean, onDescribe: () -> Unit, onDraw: () -> Unit,
    onResize: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val iconColor = Color.White
        IconButton(onClick = onDescribe) { Icon(Icons.Default.Description, "Description", tint = iconColor) }
        IconButton(onClick = onDraw) { Icon(Icons.Default.Draw, "Draw", tint = iconColor) }
        IconButton(onClick = onResize) {
            Icon(if(isResized) Icons.Default.ZoomInMap else Icons.Default.ZoomOutMap, "Resize", tint = iconColor)
        }
        IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy", tint = iconColor) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = iconColor) }
    }
}

@Composable
fun CheckboxBlockComposable(
    block: CheckboxBlock,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChange: (FocusState) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = block.isChecked, onCheckedChange = onCheckedChange)
        val textStyle = if (block.isChecked) {
            ComposeTextStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray)
        } else {
            ComposeTextStyle(color = Color.Black)
        }
        BasicTextField(
            value = block.value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged(onFocusChange),
            textStyle = textStyle,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.weight(1f)) {
                    if (block.value.text.isEmpty()) {
                        Text("Mục danh sách", color = Color.Gray)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun SeparatorBlockComposable() {
    Divider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun AudioBlockComposable(block: AudioBlock, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.Default.PlayCircle, contentDescription = "Play", modifier = Modifier.size(32.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .padding(horizontal = 12.dp)
                    .background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            )
            Text(block.duration, style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { /*TODO: Play*/ }) {
                Icon(Icons.Default.VolumeUp, "Play/Pause", tint = Color.Gray)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, "Delete", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun AccordionBlockComposable(block: AccordionBlock, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(block.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(
                    imageVector = if (block.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle"
                )
            }
            AnimatedVisibility(visible = block.isExpanded) {
                Text(block.content, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun ToggleSwitchBlockComposable(block: ToggleSwitchBlock, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(block.text, modifier = Modifier.weight(1f))
            Switch(checked = block.isOn, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun RadioGroupBlockComposable(block: RadioGroupBlock, onSelectionChange: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            block.items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (block.selectedId == item.id),
                            onClick = { onSelectionChange(item.id) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (block.selectedId == item.id),
                        onClick = { onSelectionChange(item.id) }
                    )
                    Text(
                        text = item.label,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
