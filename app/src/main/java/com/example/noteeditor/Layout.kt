package com.example.noteeditor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTakingScreen() {
    // State holders for the text fields
    var text1 by remember { mutableStateOf("Tiêu đề...") }
    var text2 by remember { mutableStateOf("Nội dung ghi chú...") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* No title */ },
                // Thiết lập màu nền trong suốt cho TopAppBar
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = { /* Handle back action */ }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Nút Undo
                    IconButton(onClick = { /* Handle undo action */ }) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo"
                        )
                    }
                    // Nút Redo
                    IconButton(onClick = { /* Handle redo action */ }) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Redo"
                        )
                    }
                    // Nút Save
                    TextButton(onClick = { /* Handle save action */ }) {
                        Text("Save", color = Color.Blue, fontSize = 16.sp)
                    }
                }
            )
        },
        // Thiết lập màu nền trong suốt cho toàn bộ màn hình
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            // Áp dụng padding từ Scaffold và padding tùy chỉnh
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 15.dp)
        ) {
            // Khối BasicTextField đầu tiên
            item {
                BasicTextField(
                    value = text1,
                    onValueChange = { text1 = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textStyle = TextStyle(fontSize = 24.sp),
                    // Giữ cho nền của TextField trong suốt
                    cursorBrush = SolidColor(LocalContentColor.current)
                )
            }

            // Vạch phân cách
            item {
                Divider(
                    color = Color.Black,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Khối BasicTextField thứ hai
            item {
                BasicTextField(
                    value = text2,
                    onValueChange = { text2 = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textStyle = TextStyle(fontSize = 18.sp),
                    // Giữ cho nền của TextField trong suốt
                    cursorBrush = SolidColor(LocalContentColor.current)
                )
            }

            // Spacer ở dưới cùng để tạo lề
            item {
                Spacer(modifier = Modifier.height(300.dp))
            }
        }
    }
}
