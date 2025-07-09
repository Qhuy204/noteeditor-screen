package com.example.noteeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat // Import WindowCompat

import com.example.noteeditor.ui.theme.NoteeditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 4. Dùng WindowCompat.setDecorFitsSystemWindows(window, false) trong MainActivity.
        // Điều này cho phép ứng dụng của bạn vẽ dưới các thanh hệ thống (thanh trạng thái, thanh điều hướng),
        // cho phép IME (bàn phím ảo) điều chỉnh bố cục một cách mượt mà hơn.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NoteeditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NoteEditorScreen(
                        onBack = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}
