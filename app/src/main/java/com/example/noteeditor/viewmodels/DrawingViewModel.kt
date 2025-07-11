package com.example.noteeditor.viewmodels

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.raed.rasmview.brushtool.data.Brush
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel cho Drawing Canvas, được điều chỉnh cho thư viện RasmView.
 *
 * ViewModel này chủ yếu giữ trạng thái UI cho các điều khiển vẽ,
 * như màu sắc, độ rộng nét vẽ, và loại bút vẽ hiện tại. Logic vẽ thực tế
 * được xử lý bởi RasmContext trong Composable, vì RasmView là một View Android truyền thống.
 */
class DrawingViewModel : ViewModel() {

    // --- StateFlow cho các điều khiển UI ---

    private val _currentColor = MutableStateFlow(Color.Black)
    val currentColor = _currentColor.asStateFlow()

    // RasmView sử dụng một thang đo khác cho kích thước (ví dụ: 0.0f đến 1.0f)
    private val _currentStrokeWidth = MutableStateFlow(0.25f)
    val currentStrokeWidth = _currentStrokeWidth.asStateFlow()

    private val _currentBrush = MutableStateFlow(Brush.Pen)
    val currentBrush = _currentBrush.asStateFlow()

    // --- Trạng thái cho khả năng hiển thị của nút Undo/Redo ---

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    // --- Các hàm public được gọi từ UI ---

    fun changeColor(newColor: Color) {
        _currentColor.value = newColor
    }

    fun changeStrokeWidth(newWidth: Float) {
        // Giới hạn giá trị trong một khoảng hợp lý cho RasmView
        _currentStrokeWidth.value = newWidth.coerceIn(0.01f, 1.0f)
    }

    fun changeBrush(newBrush: Brush) {
        _currentBrush.value = newBrush
    }

    /**
     * Hàm này được gọi từ Composable để cập nhật trạng thái undo/redo
     * của ViewModel từ RasmContext.
     */
    fun updateUndoRedoState(canUndo: Boolean, canRedo: Boolean) {
        _canUndo.value = canUndo
        _canRedo.value = canRedo
    }
}
