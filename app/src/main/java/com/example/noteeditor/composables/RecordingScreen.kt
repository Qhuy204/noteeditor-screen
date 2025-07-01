package com.example.noteeditor.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.noteeditor.AudioBlock // Assuming AudioBlock is accessible

@Composable
fun RecordingScreen(
    currentAudioBlock: AudioBlock?, // The AudioBlock currently being recorded
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSaveRecording: () -> Unit
) {
    val accentColor = Color(0xFFFFC700) // Màu vàng/cam cho biểu tượng và sóng âm
    val redColor = Color(0xFFE53935) // Màu đỏ cho nút dừng

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // Nền trắng cho toàn màn hình ghi âm
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround // Phân bổ không gian đều
    ) {
        Spacer(modifier = Modifier.weight(1f)) // Đẩy nội dung xuống giữa

        // Biểu tượng micro lớn
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Recording Microphone",
            tint = accentColor,
            modifier = Modifier.size(96.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Đồng hồ đếm thời gian
        Text(
            text = currentAudioBlock?.duration ?: "00:00", // Hiển thị thời gian từ AudioBlock
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Sóng âm động (giả lập)
        val waveformHeights = remember {
            List(40) {
                (10..60).random().dp // Chiều cao ngẫu nhiên cho các thanh sóng
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth(0.8f) // Chiếm 80% chiều rộng
                .height(80.dp), // Chiều cao lớn hơn cho sóng âm
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            waveformHeights.forEach { height ->
                Box(
                    modifier = Modifier
                        .width(3.dp) // Chiều rộng thanh sóng
                        .height(height)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f)) // Đẩy nội dung xuống giữa

        // Nút điều khiển (Dừng, Hủy, Lưu)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nút Hủy
            FloatingActionButton(
                onClick = onCancelRecording,
                containerColor = Color.LightGray,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.Close, "Cancel Recording", modifier = Modifier.size(36.dp))
            }

            // Nút Dừng/Tạm dừng (lớn hơn)
            FloatingActionButton(
                onClick = onStopRecording, // Sẽ dừng ghi âm
                containerColor = redColor,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(Icons.Default.Square, "Stop Recording", modifier = Modifier.size(48.dp))
            }

            // Nút Lưu
            FloatingActionButton(
                onClick = onSaveRecording,
                containerColor = accentColor,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.Check, "Save Recording", modifier = Modifier.size(36.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp)) // Khoảng cách với đáy màn hình
    }
}