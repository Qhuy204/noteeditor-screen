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
import coil.request.ImageRequest // Import mới
import coil.size.OriginalSize // Import mới
import androidx.compose.ui.input.pointer.pointerInput // Import cần thiết cho pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange // Import cần thiết cho PointerInputChange
import androidx.compose.ui.ExperimentalComposeUiApi // Annotation cần thiết
import com.example.noteeditor.* // Import all classes from noteeditor package
import java.util.concurrent.TimeUnit // Import TimeUnit for duration formatting

@Composable
fun TextBlockComposable(
    block: TextBlock,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChange: (FocusState) -> Unit
) {
    // Hợp nhất ParagraphStyle (chứa thông tin căn lề) vào TextStyle cơ sở.
    val textStyle = ComposeTextStyle.Default.merge(block.paragraphStyle)

    BasicTextField(
        value = block.value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth() // Đảm bảo toàn bộ composable chiếm đầy chiều rộng
            .onFocusChanged(onFocusChange),
        textStyle = textStyle,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Hiển thị dấu đầu dòng nếu đây là một mục danh sách
                if (block.isListItem) {
                    Text(
                        text = "• ",
                        style = textStyle,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                // Box này sẽ chiếm hết không gian còn lại trong Row
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Hiển thị placeholder nếu không có nội dung
                    if (block.value.annotatedString.isEmpty()) {
                        Text(
                            "Nội dung...",
                            color = Color.Gray,
                            // Placeholder cũng phải tuân theo kiểu căn lề
                            style = textStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        innerTextField()
                    }
                }
            }
        }
    )
}


@Composable
fun ImageBlockComposable(
    block: ImageBlock,
    isSelected: Boolean,
    isDrawing: Boolean, // [MỚI] Trạng thái đang vẽ
    onImageClick: () -> Unit,
    onResize: () -> Unit,
    onDelete: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDraw: (String?) -> Unit, // [MỚI] Callback để bật/tắt chế độ vẽ
    onCopy: (String) -> Unit, // [MỚI] Callback để copy ảnh
    onOpenInGallery: (String) -> Unit // [MỚI] Callback để mở ảnh trong thư viện
) {
    val targetImageSizeFraction = if (block.isResized) 0.5f else 1f
    val animatedImageSizeFraction by animateFloatAsState(
        targetValue = targetImageSizeFraction,
        animationSpec = tween(durationMillis = 300) // Animation mượt mà
    )
    var isDescriptionVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current // Lấy context cho ImageRequest

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
        // Căn trái khi ảnh thu nhỏ, căn giữa khi ảnh 100%
        horizontalAlignment = if (block.isResized) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedImageSizeFraction) // Sử dụng giá trị animation
                .clip(RoundedCornerShape(12.dp))
                .clickable { onImageClick() }
                .then(
                    if (isSelected) Modifier.border(2.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp)) // Viền cam
                    else Modifier
                )
        ) {
            Image(
                painter = painter,
                contentDescription = block.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio), // Sử dụng biến đã đổi tên
                contentScale = ContentScale.Fit // Giữ nguyên chất lượng ảnh
            )

            if (isSelected && !isDrawing) { // Chỉ hiện menu khi được chọn và không đang vẽ
                ImageActionMenu(
                    isResized = block.isResized,
                    onDescribe = { isDescriptionVisible = !isDescriptionVisible },
                    onDraw = { onDraw(block.id) }, // Bật chế độ vẽ cho ảnh này
                    onResize = onResize,
                    onCopy = { onCopy(block.id) },
                    onOpenInGallery = { onOpenInGallery(block.id) },
                    onDelete = onDelete
                )
            }
        }

        // Editor mô tả ảnh
        AnimatedVisibility(visible = isDescriptionVisible || block.description.isNotEmpty()) {
            ImageDescriptionEditor(
                description = block.description,
                onDescriptionChange = onDescriptionChange,
                modifier = Modifier
                    .fillMaxWidth(animatedImageSizeFraction) // Kích thước theo ảnh
                    .padding(top = 4.dp)
            )
        }

        // Canvas vẽ trên ảnh
        AnimatedVisibility(visible = isDrawing) {
            ImageDrawingCanvas(
                modifier = Modifier
                    .fillMaxWidth(animatedImageSizeFraction) // Kích thước theo ảnh
                    .aspectRatio(imageAspectRatio) // Sử dụng biến đã đổi tên
                    .padding(top = 4.dp)
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                onCloseDrawing = { onDraw(null) } // Tắt chế độ vẽ
            )
        }
    }
}

@Composable
fun BoxScope.ImageActionMenu(
    isResized: Boolean, onDescribe: () -> Unit, onDraw: () -> Unit,
    onResize: () -> Unit, onCopy: () -> Unit, onOpenInGallery: () -> Unit, onDelete: () -> Unit
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

@OptIn(ExperimentalComposeUiApi::class) // Thêm annotation này
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
                .weight(1f) // Chiếm phần còn lại của không gian
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
                        onDrag = { change: PointerInputChange, dragAmount: Offset -> // Chỉ định rõ kiểu cho change và dragAmount
                            change.consume() // Sử dụng consume() từ PointerInputChange
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
    onStopRecording: () -> Unit // Giữ lại callback này nếu muốn hiển thị nút dừng trong khối
) {
    val backgroundColor = Color(0xFFFFF7E6) // Màu nền nhạt tương tự ảnh
    val accentColor = Color(0xFFFFC700) // Màu vàng/cam cho biểu tượng và sóng âm

    // Hàm định dạng thời lượng (để sử dụng cục bộ trong Composable nếu cần)
    fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp), // Góc bo tròn lớn hơn
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 0.dp) // Tăng padding để rộng hơn
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // [CẬP NHẬT UI] Chỉ hiển thị UI phát lại cho khối âm thanh đã lưu
            // UI ghi âm sẽ nằm ở RecordingScreen riêng
            IconButton(
                onClick = { onTogglePlaying(block.id, block.filePath) },
                enabled = block.filePath != null
            ) {
                Icon(
                    imageVector = if (block.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, // Play/Pause
                    contentDescription = if (block.isPlaying) "Pause" else "Play",
                    tint = accentColor, // Màu vàng/cam
                    modifier = Modifier.size(32.dp)
                )
            }


            Spacer(Modifier.width(12.dp))

            // Thời lượng và sóng âm
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = block.duration, // Luôn hiển thị duration đã lưu
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Giả lập sóng âm (waveform)
                val waveformHeights = remember {
                    List(20) {
                        (5..20).random().dp
                    }
                }
                Row(
                    modifier = Modifier
                        .height(24.dp)
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    waveformHeights.forEach { height ->
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(height)
                                .background(accentColor, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Nút xóa (chỉ hiển thị khi không ghi âm)
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