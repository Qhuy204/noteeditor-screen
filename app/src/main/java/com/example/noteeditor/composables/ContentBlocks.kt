package com.example.noteeditor.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest // New import
import coil.size.OriginalSize // New import
import androidx.compose.ui.input.pointer.pointerInput // Required import for pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange // Required import for PointerInputChange
import androidx.compose.ui.ExperimentalComposeUiApi // Required annotation
import com.example.noteeditor.* // Import all classes from noteeditor package
import com.example.noteeditor.composables.BoxScopeInstance.ImageActionMenu
import java.util.concurrent.TimeUnit // Import TimeUnit for duration formatting
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor   // Import RichTextEditor
import com.mohamedrejeb.richeditor.model.RichTextState // Import RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import com.linc.audiowaveform.AudioWaveform // [NEW] Import AudioWaveform library
import com.linc.audiowaveform.model.AmplitudeType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextBlockComposable(
    block: TextBlock,
    richTextState: RichTextState, // Receive RichTextState directly from ViewModel
    onHtmlContentChange: (String) -> Unit, // Callback to return changed HTML content
    onFocusChange: (FocusState) -> Unit,
) {
    val textStyle = ComposeTextStyle.Default.merge(block.paragraphStyle)
    val customColors = RichTextEditorDefaults.outlinedRichTextEditorColors(
        focusedBorderColor = Color(0xFFFFFFFF),
        unfocusedBorderColor = Color(0xFFFFFFFF),
    )

    // Listen for richTextState content changes and send back to ViewModel as HTML
    LaunchedEffect(richTextState) {
        snapshotFlow { richTextState.toHtml() }
            .collect { newHtml ->
                // Only call callback if HTML content actually changes
                // This is to capture changes directly from user typing in the editor
                if (newHtml != block.htmlContent) { // Compare with block.htmlContent
                    onHtmlContentChange(newHtml)
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        OutlinedRichTextEditor(
            state = richTextState, // Use local RichTextState
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged(onFocusChange),
            textStyle = textStyle,
            colors = customColors
        )

        // Placeholder text
        if (richTextState.annotatedString.isEmpty()) {
            Text(
                "",
                color = Color.Gray,
                style = textStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(start = if (block.isListItem) 24.dp else 0.dp)
            )
        }
    }
}


@Composable
fun SubHeaderBlockComposable(
    block: SubHeaderBlock,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChange: (FocusState) -> Unit
) {
    val textStyle = ComposeTextStyle.Default
        .merge(block.paragraphStyle)
        .merge(block.spanStyle)

    BasicTextField(
        value = block.value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged(onFocusChange),
        textStyle = textStyle,
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (block.value.annotatedString.isEmpty()) {
                    Text(
                        "Subheader...",
                        color = Color.Gray,
                        style = textStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun NumberedListItemBlockComposable(
    block: NumberedListItemBlock,
    index: Int, // Need index to display order number
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChange: (FocusState) -> Unit
) {
    val textStyle = ComposeTextStyle.Default.merge(block.paragraphStyle)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${index + 1}. ", // Display order number
            style = textStyle,
            modifier = Modifier.padding(end = 8.dp)
        )
        BasicTextField(
            value = block.value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged(onFocusChange),
            textStyle = textStyle,
            decorationBox = { innerTextField ->
                if (block.value.annotatedString.isEmpty()) {
                    Text(
                        "List item...",
                        color = Color.Gray,
                        style = textStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                innerTextField()
            }
        )
    }
}


@Composable
fun ImageBlockComposable(
    block: ImageBlock,
    isSelected: Boolean,
    isDrawing: Boolean, // [NEW] Drawing state
    onImageClick: () -> Unit,
    onResize: () -> Unit,
    onDelete: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDraw: (String?) -> Unit, // [NEW] Callback to toggle drawing mode
    onCopy: (String) -> Unit, // [NEW] Callback to copy image
    onOpenInGallery: (String) -> Unit // [NEW] Callback to open image in gallery
) {
    val targetImageSizeFraction = if (block.isResized) 0.5f else 1f
    val animatedImageSizeFraction by animateFloatAsState(
        targetValue = targetImageSizeFraction,
        animationSpec = tween(durationMillis = 300) // Smooth animation
    )
    var isDescriptionVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current // Get context for ImageRequest

    // Declare imageAspectRatio here, outside the painter block, but within the Composable's scope
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(block.uri)
            .size(OriginalSize)
            .build()
    )
    val imageAspectRatio = with(painter.intrinsicSize) {
        if (isSpecified && height != 0f) width / height else 16f / 9f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        // Align left when image is resized, center when image is 100%
        horizontalAlignment = if (block.isResized) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedImageSizeFraction) // Use animation value
                .clip(RoundedCornerShape(12.dp))
                .clickable { onImageClick() }
                .then(
                    if (isSelected) Modifier.border(2.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp)) // Orange border
                    else Modifier
                )
        ) {
            Image(
                painter = painter,
                contentDescription = block.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio), // Use renamed variable
                contentScale = ContentScale.Fit // Maintain image quality
            )

            if (isSelected && !isDrawing) { // Only show menu when selected and not drawing
                ImageActionMenu( // [FIX] Corrected call to ImageActionMenu
                    isResized = block.isResized,
                    onDescribe = { isDescriptionVisible = !isDescriptionVisible },
                    onDraw = { onDraw(block.id) }, // Enable drawing mode for this image
                    onResize = onResize, // Pass onResize here
                    onCopy = { onCopy(block.id) },
                    onOpenInGallery = { onOpenInGallery(block.id) },
                    onDelete = onDelete
                )
            }
        }

        // Image description editor
        AnimatedVisibility(visible = isDescriptionVisible || block.description.isNotEmpty()) {
            ImageDescriptionEditor(
                description = block.description,
                onDescriptionChange = onDescriptionChange,
                modifier = Modifier
                    .fillMaxWidth(animatedImageSizeFraction) // Size according to image
                    .padding(top = 4.dp)
            )
        }

        // Drawing canvas on image
        AnimatedVisibility(visible = isDrawing) {
            ImageDrawingCanvas(
                modifier = Modifier
                    .fillMaxWidth(animatedImageSizeFraction) // Size according to image
                    .aspectRatio(imageAspectRatio) // Use renamed variable
                    .padding(top = 4.dp)
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                onCloseDrawing = { onDraw(null) } // Turn off drawing mode
            )
        }
    }
}

// Helper object to provide BoxScope for ImageActionMenu
object BoxScopeInstance {
    @Composable
    fun BoxScope.ImageActionMenu(
        isResized: Boolean, onDescribe: () -> Unit, onDraw: () -> Unit,
        onResize: () -> Unit, // Added onResize here
        onCopy: () -> Unit, onOpenInGallery: () -> Unit, onDelete: () -> Unit
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
            IconButton(onClick = onOpenInGallery) { Icon(Icons.Default.PhotoLibrary, "Open in Gallery", tint = iconColor) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = iconColor) }
        }
    }
}


@Composable
fun ImageDescriptionEditor(
    description: String,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        modifier = modifier,
        placeholder = { Text("Nhập mô tả ảnh...") },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Gray,
            unfocusedBorderColor = Color.LightGray
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = false,
        maxLines = 3
    )
}

@OptIn(ExperimentalComposeUiApi::class) // Add this annotation
@Composable
fun ImageDrawingCanvas(
    modifier: Modifier = Modifier,
    onCloseDrawing: () -> Unit
) {
    var path by remember { mutableStateOf(Path()) }
    var currentPathColor by remember { mutableStateOf(Color.Red) }
    var currentStrokeWidth by remember { mutableStateOf(5f) }

    Column(modifier = modifier) {
        // Drawing tools toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color picker
            ColorPickerButton(currentColor = currentPathColor) { color ->
                currentPathColor = color
            }

            // Stroke width picker
            StrokeWidthPickerButton(currentStrokeWidth = currentStrokeWidth) { width ->
                currentStrokeWidth = width
            }

            // Clear button
            IconButton(onClick = { path = Path() }) {
                Icon(Icons.Default.Clear, "Clear Drawing", tint = Color.Black)
            }

            // Close button
            IconButton(onClick = onCloseDrawing) {
                Icon(Icons.Default.Done, "Done Drawing", tint = Color.Black)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Drawing Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Take the remaining space
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            path.moveTo(offset.x, offset.y)
                        },
                        onDragEnd = {
                            // Optionally save the path or convert to image
                        },
                        onDragCancel = {
                            // Handle cancel
                        },
                        onDrag = { change: PointerInputChange, dragAmount: Offset -> // Specify types for change and dragAmount
                            change.consume() // Use consume() from PointerInputChange
                            path.lineTo(change.position.x, change.position.y)
                        }
                    )
                }
        ) {
            drawPath(
                path = path,
                color = currentPathColor,
                style = Stroke(width = currentStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun ColorPickerButton(currentColor: Color, onColorSelected: (Color) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.White, Color.Yellow)

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Palette, "Select Color", tint = currentColor)
        }
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

@Composable
fun StrokeWidthPickerButton(currentStrokeWidth: Float, onWidthSelected: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val widths = listOf(2f, 5f, 10f, 15f)

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.LineWeight, "Select Stroke Width", tint = Color.Black)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(modifier = Modifier.padding(8.dp)) {
                widths.forEach { width ->
                    Text(
                        text = width.toInt().toString(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onWidthSelected(width)
                                expanded = false
                            }
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }
        }
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
fun AudioBlockComposable(
    block: AudioBlock,
    onDelete: () -> Unit,
    onTogglePlaying: (String, String?) -> Unit,
    onStopRecording: () -> Unit // Keep this callback if you want to show stop button in block
) {
    val backgroundColor = Color(0xFFFFF7E6) // Changed back to the old color
    val accentColor = Color(0xFFFFC700) // Yellow/orange color for icon and waveform

    // Function to format duration (for local use in Composable if needed)
    fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp), // Larger rounded corners
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 2.dp) // Increase padding for wider space
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // [UPDATE UI] Only display playback UI for saved audio blocks
            // Recording UI will be in a separate RecordingScreen
            IconButton(
                onClick = { onTogglePlaying(block.id, block.filePath) },
                enabled = block.filePath != null
            ) {
                Icon(
                    imageVector = if (block.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, // Play/Pause
                    contentDescription = if (block.isPlaying) "Pause" else "Play",
                    tint = accentColor, // Yellow/orange color
                    modifier = Modifier.size(32.dp)
                )
            }


            Spacer(Modifier.width(12.dp))

            // Duration and waveform
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = block.duration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor,
                    modifier = Modifier.padding(end = 4.dp)
                )

                var waveformProgress by remember { mutableStateOf(0F) }

                // [NEW] Using AudioWaveform library with direct parameters
//                AudioWaveform(
//                    modifier = Modifier
//                        .height(24.dp)
//                        .weight(1f),
//                    amplitudes = block.amplitudes,
//                    progress = 0f, // Placeholder for now
//                    onProgressChange = { waveformProgress = it },
//                    waveformBrush = SolidColor(accentColor),
//                    progressBrush = SolidColor(accentColor.copy(alpha = 0.4f)),
//                    spikeWidth = 1.dp,
//                    spikeRadius = 0.5.dp,
//                    spikePadding = 1.dp
//                )

                AudioWaveform(
                    modifier = Modifier.fillMaxWidth(),
                    // Spike DrawStyle: Fill or Stroke
                    style = Fill,
//                    waveformAlignment = WaveformAlignment.Center,
                    amplitudeType = AmplitudeType.Avg,
                    // Colors could be updated with Brush API
                    progressBrush = SolidColor(accentColor.copy(alpha = 0.4f)),
                    waveformBrush = SolidColor(accentColor),
                    spikeWidth = 4.dp,
                    spikePadding = 2.dp,
                    spikeRadius = 4.dp,
                    progress = waveformProgress,
                    amplitudes = block.amplitudes,
                    onProgressChange = { waveformProgress = it },
                    onProgressChangeFinished = {}
                )

            }

            Spacer(Modifier.width(12.dp))

            // Delete button (only visible when not recording)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = Color.Gray)
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
