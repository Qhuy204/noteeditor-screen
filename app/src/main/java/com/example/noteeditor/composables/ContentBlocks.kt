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
import coil.request.ImageRequest // Import mới
import coil.size.OriginalSize // Import mới
import androidx.compose.ui.platform.LocalContext // Import mới
import com.example.noteeditor.* // Import all classes from noteeditor package
import java.util.concurrent.TimeUnit // Import TimeUnit for duration formatting

/**
 * [ĐÃ SỬA LẦN 2] Cấu trúc lại Composable để căn lề hoạt động chính xác.
 *
 * Vấn đề trước đây là vùng soạn thảo bên trong (`innerTextField`) không được cấp đủ chiều rộng.
 * Giải pháp này đặt toàn bộ layout (bao gồm dấu đầu dòng và vùng nhập liệu) vào trong `decorationBox`.
 * `BasicTextField` được set `fillMaxWidth()` để đảm bảo `decorationBox` có không gian để phân phối.
 * Bên trong `decorationBox`, một `Row` được sử dụng. Vùng nhập liệu (`innerTextField`) được đặt trong một `Box`
 * với `Modifier.weight(1f)`, đảm bảo nó chiếm hết không gian còn lại, cho phép `textAlign` hoạt động đúng.
 */
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
    onImageClick: () -> Unit,
    onResize: () -> Unit,
    onDelete: () -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    val imageSizeFraction = if (block.isResized) 0.5f else 1f
    var isDescriptionVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current // Lấy context cho ImageRequest

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
            // [CẬP NHẬT] Sử dụng ImageRequest với OriginalSize để giữ nguyên chất lượng ảnh
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context) // Sử dụng LocalContext
                    .data(block.uri)
                    .size(OriginalSize) // Yêu cầu tải ảnh với kích thước gốc
                    .build()
            )
            val aspectRatio = with(painter.intrinsicSize) {
                if (isSpecified && height != 0f) width / height else 16f / 9f
            }

            Image(
                painter = painter,
                contentDescription = block.description,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
                contentScale = ContentScale.Fit // Giữ nguyên chất lượng ảnh
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
