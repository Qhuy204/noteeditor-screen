package com.example.noteeditor.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import coil.request.ImageRequest
import com.example.noteeditor.viewmodels.DrawingViewModel
import com.raed.rasmview.RasmContext
import com.raed.rasmview.RasmView
import com.raed.rasmview.brushtool.data.Brush
import com.raed.rasmview.brushtool.data.BrushesRepository

/**
 * Dialog toàn màn hình để vẽ, được viết lại để sử dụng thư viện RasmView.
 *
 * LƯU Ý: Đảm bảo bạn đã thêm thư viện 'com.raedapps:rasmview:1.2.1'
 * vào tệp build.gradle của bạn.
 */
@Composable
fun DrawingCanvasScreen(
    drawingViewModel: DrawingViewModel = viewModel(),
    backgroundImageUri: Uri?,
    onSave: (ImageBitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // Sử dụng remember để giữ tham chiếu đến RasmContext
    val rasmContext = remember { mutableStateOf<RasmContext?>(null) }
    // Sử dụng remember để chỉ khởi tạo BrushesRepository một lần
    val brushesRepository = remember { BrushesRepository(context.resources) }

    // Lấy trạng thái từ ViewModel
    val currentColor by drawingViewModel.currentColor.collectAsState()
    val currentStrokeWidth by drawingViewModel.currentStrokeWidth.collectAsState()
    val currentBrush by drawingViewModel.currentBrush.collectAsState()
    val canUndo by drawingViewModel.canUndo.collectAsState()
    val canRedo by drawingViewModel.canRedo.collectAsState()

    // Tải ảnh nền và đặt nó vào RasmContext khi có sẵn
    LaunchedEffect(backgroundImageUri, rasmContext.value) {
        if (backgroundImageUri != null && rasmContext.value != null) {
            val request = ImageRequest.Builder(context)
                .data(backgroundImageUri)
                .allowHardware(false) // Quan trọng để render vào bitmap
                .build()
            val result = (context.imageLoader.execute(request).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            result?.let {
                rasmContext.value?.setRasm(it)
            }
        }
    }

    // Cập nhật RasmContext khi trạng thái ViewModel thay đổi
    LaunchedEffect(currentColor, rasmContext.value) {
        rasmContext.value?.brushColor = currentColor.toArgb()
    }

    LaunchedEffect(currentStrokeWidth, rasmContext.value) {
        rasmContext.value?.brushConfig?.size = currentStrokeWidth
    }

    LaunchedEffect(currentBrush, rasmContext.value) {
        rasmContext.value?.brushConfig = brushesRepository.get(currentBrush)
        // Áp dụng lại kích thước và màu sắc sau khi thay đổi bút vẽ
        rasmContext.value?.brushConfig?.size = currentStrokeWidth
        rasmContext.value?.brushColor = currentColor.toArgb()
    }


    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFFF5F5F5),
            topBar = {
                DrawingTopBar(
                    onUndo = { rasmContext.value?.state?.undo() },
                    onRedo = { rasmContext.value?.state?.redo() },
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onDone = {
                        rasmContext.value?.exportRasm()?.let {
                            onSave(it.asImageBitmap())
                        }
                    },
                    onClose = onClose
                )
            },
            bottomBar = {
                DrawingBottomBar(
                    currentColor = currentColor,
                    onColorChange = drawingViewModel::changeColor,
                    onStrokeWidthChange = drawingViewModel::changeStrokeWidth,
                    onBrushChange = drawingViewModel::changeBrush
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                // Sử dụng AndroidView để nhúng RasmView vào Compose
                AndroidView(
                    factory = { ctx ->
                        RasmView(ctx).apply {
                            // Khởi tạo RasmContext và thiết lập listener
                            val newRasmContext = this.rasmContext
                            newRasmContext.state.addOnStateChangedListener {
                                drawingViewModel.updateUndoRedoState(
                                    canUndo = newRasmContext.state.canCallUndo(),
                                    canRedo = newRasmContext.state.canCallRedo()
                                )
                            }
                            // Đặt trạng thái ban đầu
                            drawingViewModel.updateUndoRedoState(
                                canUndo = newRasmContext.state.canCallUndo(),
                                canRedo = newRasmContext.state.canCallRedo()
                            )
                            rasmContext.value = newRasmContext
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawingTopBar(
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onDone: () -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (canUndo) LocalContentColor.current else Color.Gray)
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (canRedo) LocalContentColor.current else Color.Gray)
            }
            IconButton(onClick = onDone) {
                Icon(Icons.Default.Done, contentDescription = "Done")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
private fun DrawingBottomBar(
    currentColor: Color,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onBrushChange: (Brush) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showStrokeWidthPicker by remember { mutableStateOf(false) }
    var showBrushPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { showBrushPicker = true }) {
                Icon(Icons.Default.Brush, contentDescription = "Change Brush")
            }
            IconButton(onClick = { showStrokeWidthPicker = true }) {
                Icon(Icons.Default.Create, contentDescription = "Change Stroke Width")
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .clickable { showColorPicker = true }
                    .border(1.dp, Color.Gray, CircleShape)
            )
        }

        if (showColorPicker) {
            ColorPicker(onColorSelected = {
                onColorChange(it)
                showColorPicker = false
            })
        }
        if (showStrokeWidthPicker) {
            StrokeWidthSlider(
                initialValue = 0.25f, // Giá trị ban đầu, bạn có thể lấy từ ViewModel
                onDismissRequest = { showStrokeWidthPicker = false },
                onStrokeWidthChange = onStrokeWidthChange
            )
        }
        if (showBrushPicker) {
            BrushPicker(
                onDismissRequest = { showBrushPicker = false },
                onBrushSelected = {
                    onBrushChange(it)
                    showBrushPicker = false
                }
            )
        }
    }
}

@Composable
private fun ColorPicker(onColorSelected: (Color) -> Unit) {
    val colors = listOf(
        Color.Black, Color.Gray, Color.White, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta
    )
    LazyRow(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(colors) { color ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onColorSelected(color) }
                    .border(1.dp, Color.DarkGray, CircleShape)
            )
        }
    }
}

@Composable
private fun StrokeWidthSlider(
    initialValue: Float,
    onDismissRequest: () -> Unit,
    onStrokeWidthChange: (Float) -> Unit
) {
    var sliderPosition by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Stroke Width") },
        text = {
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                valueRange = 0.01f..1f, // RasmView sử dụng float từ 0 đến 1 cho kích thước
                onValueChangeFinished = {
                    onStrokeWidthChange(sliderPosition)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("OK") }
        }
    )
}

@Composable
private fun BrushPicker(onDismissRequest: () -> Unit, onBrushSelected: (Brush) -> Unit) {
    val brushes = Brush.values()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Brush") },
        text = {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(brushes) { brush ->
                    Button(onClick = { onBrushSelected(brush) }) {
                        Text(brush.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        }
    )
}
