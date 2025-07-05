package com.example.noteeditor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.noteeditor.composables.*
import java.io.File
import java.util.Objects
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor // Import RichTextEditor

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val focusManager = LocalFocusManager.current
    val isKeyboardVisible = WindowInsets.isImeVisible
    val lazyListState = rememberLazyListState()
    var showAddMoreMenu by remember { mutableStateOf(false) }

    var showImageSourceSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    // Trạng thái cho kéo thả
    var draggingBlockIndex by remember { mutableStateOf<Int?>(null) }
    var currentDropTargetIndex by remember { mutableStateOf<Int?>(null) }
    val localDensity = LocalDensity.current

    val requestRecordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("NoteEditorScreen", "RECORD_AUDIO permission granted. Starting recording.")
            viewModel.startNewAudioRecording(context)
        } else {
            Log.w("NoteEditorScreen", "RECORD_AUDIO permission denied.")
        }
    }

    // Launcher để mở ảnh trong thư viện
    val openImageInGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Không cần xử lý kết quả ở đây vì chỉ mở ứng dụng khác
        Log.d("NoteEditorScreen", "Attempted to open image in gallery. Result: ${result.resultCode}")
    }

    LaunchedEffect(Unit) {
        Log.d("NoteEditorDebug", "NoteEditorScreen recomposed.")
    }

    LaunchedEffect(isKeyboardVisible) {
        Log.d("NoteEditorScreen", "Keyboard visibility changed: $isKeyboardVisible")
        if (!isKeyboardVisible) {
            viewModel.onImageClick("") // Bỏ chọn ảnh khi bàn phím ẩn
            viewModel.stopPlaying()
            viewModel.toggleDrawingMode(null) // Tắt chế độ vẽ khi bàn phím ẩn
            Log.d("NoteEditorScreen", "Keyboard hidden. Cleared image selection, stopped audio, and exited drawing mode.")
        }
    }

    fun createImageUri(context: Context): Uri {
        val file = File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_",
            ".jpg",
            context.externalCacheDir
        )
        Log.d("NoteEditorScreen", "Created temp image URI: ${file.absolutePath}")
        return FileProvider.getUriForFile(
            Objects.requireNonNull(context),
            "com.example.noteeditor.provider",
            file
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.addImageAtCursor(it)
            Log.d("NoteEditorScreen", "Image selected from gallery: $it")
        } ?: Log.d("NoteEditorScreen", "Image selection cancelled.")
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let {
                viewModel.addImageAtCursor(it)
                Log.d("NoteEditorScreen", "Image captured by camera: $it")
            }
        } else {
            Log.d("NoteEditorScreen", "Image capture cancelled or failed.")
        }
    }

    Scaffold(
        topBar = {
            NoteEditorTopAppBar(
                onBackClick = onBack,
                onSaveClick = {
                    focusManager.clearFocus()
                    viewModel.saveNote()
                    Log.d("NoteEditorScreen", "Save button clicked. Note saved.")
                },
                onUndoClick = {
                    viewModel.undo()
                    Log.d("NoteEditorScreen", "Undo button clicked. Current undo/redo info: ${viewModel.getUndoRedoStackInfo()}")
                },
                onRedoClick = {
                    viewModel.redo()
                    Log.d("NoteEditorScreen", "Redo button clicked. Current undo/redo info: ${viewModel.getUndoRedoStackInfo()}")
                },
                canUndo = canUndo,
                canRedo = canRedo
            )
        },
        containerColor = Color.White,
        floatingActionButton = {
            if (showAddMoreMenu) {
                ModalBottomSheet(onDismissRequest = {
                    showAddMoreMenu = false
                    Log.d("NoteEditorScreen", "Add More menu dismissed.")
                }) {
                    Column(Modifier.padding(bottom = 32.dp)) {
                        ListItem(
                            headlineContent = { Text("Thêm Radio Button Group") },
                            leadingContent = { Icon(Icons.Default.RadioButtonChecked, null) },
                            modifier = Modifier.clickable {
                                viewModel.addRadioGroup()
                                showAddMoreMenu = false
                                Log.d("NoteEditorScreen", "Added Radio Button Group.")
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Thêm Toggle Switch") },
                            leadingContent = { Icon(Icons.Default.ToggleOn, null) },
                            modifier = Modifier.clickable {
                                viewModel.addToggleSwitch()
                                showAddMoreMenu = false
                                Log.d("NoteEditorScreen", "Added Toggle Switch.")
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Thêm Accordion") },
                            leadingContent = { Icon(Icons.Default.KeyboardArrowDown, null) },
                            modifier = Modifier.clickable {
                                viewModel.addAccordion()
                                showAddMoreMenu = false
                                Log.d("NoteEditorScreen", "Added Accordion.")
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Thêm Tiêu đề phụ") },
                            leadingContent = { Icon(Icons.Default.Title, null) },
                            modifier = Modifier.clickable {
                                viewModel.addSectionHeader()
                                showAddMoreMenu = false
                                Log.d("NoteEditorScreen", "Added Section Header.")
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Thêm Danh sách đánh số") },
                            leadingContent = { Icon(Icons.Default.FormatListNumbered, null) },
                            modifier = Modifier.clickable {
                                viewModel.addNumberedListItem()
                                showAddMoreMenu = false
                                Log.d("NoteEditorScreen", "Added Numbered List Item.")
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                TransformingBottomToolbar(
                    isKeyboardVisible = isKeyboardVisible,
                    isFormattingMode = uiState.isTextFormatToolbarVisible,
                    // Truyền RichTextState trực tiếp để thanh công cụ có thể tương tác
                    richTextState = uiState.currentRichTextState,
                    onToggleFormattingMode = {
                        viewModel.toggleTextFormatToolbar(!uiState.isTextFormatToolbarVisible)
                        Log.d(
                            "NoteEditorScreen",
                            "Toggled formatting toolbar visibility to ${!uiState.isTextFormatToolbarVisible}"
                        )
                    },
                    onToggleBold = { viewModel.toggleBold() },
                    onToggleItalic = { viewModel.toggleItalic() },
                    onToggleUnderline = { viewModel.toggleUnderline() },
                    onToggleStrikethrough = { viewModel.toggleStrikethrough() },
                    onTextAlignChange = { viewModel.setTextAlign(it) },
                    onToggleBulletList = { viewModel.toggleBulletList() },
                    onToggleNumberedList = { viewModel.toggleNumberedList() },
//                    onIndent = { viewModel.indent() }, // Sửa lỗi TODO
//                    onOutdent = { viewModel.outdent() }, // Sửa lỗi TODO
                    onAddSeparator = {
                        viewModel.addSeparator()
                        Log.d("NoteEditorScreen", "Added separator.")
                    },
                    onFontSizeChange = {
                        viewModel.setFontSize(it)
                        Log.d("NoteEditorScreen", "Set font size to: $it")
                    },
                    onTextColorChange = {
                        viewModel.setTextColor(it)
                        Log.d("NoteEditorScreen", "Set text color to: $it")
                    },
                    onTextBgColorChange = {
                        viewModel.setTextBackgroundColor(it)
                        Log.d("NoteEditorScreen", "Set text background color to: $it")
                    },
                    onAddImageClick = {
                        showImageSourceSheet = true
                        Log.d("NoteEditorScreen", "Image source sheet opened.")
                    },
                    onAddCheckboxClick = {
                        viewModel.addCheckbox()
                        Log.d("NoteEditorScreen", "Added checkbox.")
                    },
                    onAddMoreClick = {
                        showAddMoreMenu = it
                        Log.d("NoteEditorScreen", "Add More menu visibility: $it")
                    },
                    isRecordingActive = uiState.isRecordingActive,
                    recordingDuration = uiState.currentRecordingAudioBlock?.duration ?: "00:00",
                    onAddAudioClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.startNewAudioRecording(context)
                        } else {
                            requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            Log.d("NoteEditorScreen", "Requesting RECORD_AUDIO permission.")
                        }
                    },
                    onSaveRecording = {
                        viewModel.saveRecordedAudio()
                        Log.d("NoteEditorScreen", "Saved recorded audio.")
                    },
                    onCancelRecording = {
                        viewModel.cancelRecording()
                        Log.d("NoteEditorScreen", "Cancelled recording.")
                    },
                    modifier = Modifier // Đặt modifier thành Modifier.fillMaxWidth() hoặc Modifier.wrapContentSize()
                )
            }
        }
    ) { paddingValues ->
        if (showImageSourceSheet) {
            ModalBottomSheet(onDismissRequest = {
                showImageSourceSheet = false
                Log.d("NoteEditorScreen", "Image source sheet dismissed.")
            }) {
                Column(Modifier.padding(bottom = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("Chụp ảnh") },
                        leadingContent = { Icon(Icons.Default.PhotoCamera, null) },
                        modifier = Modifier.clickable {
                            showImageSourceSheet = false
                            val uri = createImageUri(context)
                            tempImageUri = uri
                            cameraLauncher.launch(uri)
                            Log.d("NoteEditorScreen", "Launched camera for image capture.")
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Chọn từ thư viện") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                        modifier = Modifier.clickable {
                            showImageSourceSheet = false
                            imagePickerLauncher.launch("image/*")
                            Log.d("NoteEditorScreen", "Launched image picker for gallery.")
                        }
                    )
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween // Added to space out items
                ) {
                    Text(
                        text = uiState.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontWeight = FontWeight(500),
                        fontSize = 13.sp // Fixed: using .sp for font size
                    )

                    Box(
                        modifier = Modifier, // Changed to allow the AssistChip to control its width
                        contentAlignment = Alignment.CenterEnd // Align to the end of the Row
                    ) {
                        AssistChip(
                            onClick = { /* TODO: Implement click logic for category chip */ },
                            label = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 0.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = uiState.category,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .width(160.dp)
                                .height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF2F2F2)),
                            border = null
                        )

                    }
                }
                Spacer(Modifier.height(4.dp))


                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    BasicTextField(
                        value = uiState.title,
                        onValueChange = {
                            viewModel.onTitleChange(it)
                            Log.d("NoteEditorScreen", "Title changed to: $it")
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    viewModel.commitActionForUndo()
                                    Log.d("NoteEditorScreen", "Title text field lost focus. Committed for undo.")
                                } else {
                                    Log.d("NoteEditorScreen", "Title text field gained focus.")
                                }
                            },
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (uiState.title.isEmpty()) {
                                Text("Tiêu đề", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            itemsIndexed(uiState.content, key = { _, block -> block.id }) { index, block ->
                val isDragging = draggingBlockIndex == index
                val isDropTarget = currentDropTargetIndex == index

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { layoutCoordinates ->
                            // Cập nhật vị trí của từng item để tính toán drop target
                            // Lưu ý: Trong một ứng dụng thực tế, bạn sẽ cần một cách quản lý vị trí tốt hơn
                            // ví dụ: một map từ ID khối đến Rect/Offset
                            // Để đơn giản, ở đây ta sẽ dùng index và chiều cao
                            val itemHeight = layoutCoordinates.size.height.toFloat()
                            val itemTop = layoutCoordinates.positionInWindow().y
                            val itemBottom = itemTop + itemHeight

                            // Logic để xác định drop target
                            if (uiState.draggingBlockId != null) {
                                val dragPosition = uiState.dropTargetIndex?.let {
                                    lazyListState.layoutInfo.visibleItemsInfo.getOrNull(it)?.offset?.toFloat()
                                } ?: 0f // Giả định vị trí kéo

                                // Nếu vị trí kéo nằm trong khoảng của block hiện tại
                                if (dragPosition >= itemTop && dragPosition < itemBottom) {
                                    currentDropTargetIndex = index
                                    Log.d("NoteEditorScreen", "Drop target updated to index: $index")
                                }
                            }
                        }
                        .combinedClickable(
                            onLongClick = {
                                // Bắt đầu kéo
                                draggingBlockIndex = index
                                viewModel.setDraggingBlockId(block.id)
                                focusManager.clearFocus()
                                Log.d("NoteEditorScreen", "Started dragging block with ID: ${block.id} at index: $index")
                            },
                            onClick = {
                                // Xử lý click thông thường
                                if (block is ImageBlock) {
                                    viewModel.onImageClick(block.id)
                                    focusManager.clearFocus()
                                } else if (block is TextBlock || block is CheckboxBlock || block is SubHeaderBlock || block is NumberedListItemBlock) {
                                    viewModel.setFocus(block.id)
                                }
                            }
                        )
                        .then(
                            if (isDragging) Modifier.alpha(0.5f) else Modifier
                        )
                ) {
                    when (block) {
                        is TextBlock -> TextBlockComposable(block,
                            onValueChange = {
                                // Truyền RichTextState trực tiếp
                                viewModel.onTextBlockChange(block.id, it)
                                Log.d("NoteEditorScreen", "TextBlock content changed for ID: ${block.id}")
                            },
                            onFocusChange = { focusState ->
                                if (focusState.isFocused) {
                                    viewModel.setFocus(block.id)
                                    Log.d("NoteEditorScreen", "TextBlock gained focus for ID: ${block.id}")
                                } else {
                                    viewModel.commitActionForUndo()
                                    Log.d("NoteEditorScreen", "TextBlock lost focus for ID: ${block.id}. Committed for undo.")
                                }
                            },
                            richTextState = uiState.currentRichTextState // Truyền RichTextState của khối đang focus
                        )
                        is SubHeaderBlock -> SubHeaderBlockComposable(block,
                            onValueChange = {
                                // Truyền TextFieldValue
                                viewModel.onOtherBlockChange(block.id, it)
                                Log.d("NoteEditorScreen", "SubHeaderBlock content changed for ID: ${block.id}")
                            },
                            onFocusChange = { focusState ->
                                if (focusState.isFocused) {
                                    viewModel.setFocus(block.id)
                                    Log.d("NoteEditorScreen", "SubHeaderBlock gained focus for ID: ${block.id}")
                                } else {
                                    viewModel.commitActionForUndo()
                                    Log.d("NoteEditorScreen", "SubHeaderBlock lost focus for ID: ${block.id}. Committed for undo.")
                                }
                            }
                        )
                        is NumberedListItemBlock -> NumberedListItemBlockComposable(block,
                            index = index, // Truyền index để hiển thị số thứ tự
                            onValueChange = {
                                // Truyền TextFieldValue
                                viewModel.onOtherBlockChange(block.id, it)
                                Log.d("NoteEditorScreen", "NumberedListItemBlock content changed for ID: ${block.id}")
                            },
                            onFocusChange = { focusState ->
                                if (focusState.isFocused) {
                                    viewModel.setFocus(block.id)
                                    Log.d("NoteEditorScreen", "NumberedListItemBlock gained focus for ID: ${block.id}")
                                } else {
                                    viewModel.commitActionForUndo()
                                    Log.d("NoteEditorScreen", "NumberedListItemBlock lost focus for ID: ${block.id}. Committed for undo.")
                                }
                            }
                        )
                        is ImageBlock -> ImageBlockComposable(
                            block,
                            isSelected = uiState.selectedImageId == block.id,
                            isDrawing = uiState.drawingImageId == block.id, // Truyền trạng thái vẽ
                            onImageClick = {
                                viewModel.onImageClick(block.id)
                                focusManager.clearFocus()
                                Log.d("NoteEditorScreen", "ImageBlock clicked: ${block.id}")
                            },
                            onResize = {
                                viewModel.resizeImage(block.id)
                                Log.d("NoteEditorScreen", "Resized ImageBlock: ${block.id}")
                            },
                            onDelete = {
                                viewModel.deleteBlock(block.id)
                                Log.d("NoteEditorScreen", "Deleted ImageBlock: ${block.id}")
                            },
                            onDescriptionChange = { newDesc ->
                                viewModel.updateImageDescription(block.id, newDesc)
                                Log.d("NoteEditorScreen", "Updated description for ImageBlock ${block.id}: $newDesc")
                            },
                            onDraw = { imageIdToDraw -> // Callback để bật/tắt chế độ vẽ
                                viewModel.toggleDrawingMode(imageIdToDraw)
                                Log.d("NoteEditorScreen", "Toggled drawing mode for ImageBlock ${block.id}. New state: $imageIdToDraw")
                            },
                            onCopy = { imageIdToCopy -> // Callback copy ảnh
                                viewModel.copyImage(context, imageIdToCopy) // Truyền context vào đây
                                Log.d("NoteEditorScreen", "Copied ImageBlock: $imageIdToCopy")
                            },
                            onOpenInGallery = { imageIdToOpen -> // Callback mở ảnh trong thư viện
                                val imageBlock = uiState.content.find { it.id == imageIdToOpen } as? ImageBlock
                                imageBlock?.uri?.let { uri ->
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        openImageInGalleryLauncher.launch(intent)
                                        Log.d("NoteEditorScreen", "Opened ImageBlock ${block.id} in gallery: $uri")
                                    } else {
                                        Log.e("NoteEditorScreen", "No app found to open image for ID: $imageIdToOpen")
                                    }
                                }
                            }
                        )
                        is CheckboxBlock -> CheckboxBlockComposable(block,
                            onCheckedChange = {
                                viewModel.onCheckboxCheckedChange(block.id, it)
                                Log.d("NoteEditorScreen", "Checkbox state changed for ID ${block.id}: $it")
                            },
                            onValueChange = {
                                // Truyền TextFieldValue
                                viewModel.onOtherBlockChange(block.id, it)
                                Log.d("NoteEditorScreen", "CheckboxBlock content changed for ID: ${block.id}")
                            },
                            onFocusChange = { focusState ->
                                if (focusState.isFocused) {
                                    viewModel.setFocus(block.id)
                                    Log.d("NoteEditorScreen", "CheckboxBlock gained focus for ID: ${block.id}")
                                } else {
                                    viewModel.commitActionForUndo()
                                    Log.d("NoteEditorScreen", "CheckboxBlock lost focus for ID: ${block.id}. Committed for undo.")
                                }
                            }
                        )
                        is SeparatorBlock -> SeparatorBlockComposable()
                        is AudioBlock -> AudioBlockComposable(
                            block,
                            onDelete = {
                                viewModel.deleteBlock(block.id)
                                Log.d("NoteEditorScreen", "Deleted AudioBlock: ${block.id}")
                            },
                            onTogglePlaying = { id, path ->
                                viewModel.togglePlaying(id, path)
                                Log.d("NoteEditorScreen", "Toggled playing for AudioBlock ${block.id}. Is playing: ${block.isPlaying}")
                            },
                            onStopRecording = {
                                viewModel.saveRecordedAudio()
                                Log.d("NoteEditorScreen", "Stopped recording from AudioBlock.")
                            }
                        )
                        is AccordionBlock -> AccordionBlockComposable(block, onToggle = {
                            viewModel.onAccordionToggled(block.id)
                            Log.d("NoteEditorScreen", "Toggled AccordionBlock: ${block.id}")
                        })
                        is ToggleSwitchBlock -> ToggleSwitchBlockComposable(block, onToggle = {
                            viewModel.onToggleSwitchChanged(block.id, it)
                            Log.d("NoteEditorScreen", "ToggleSwitchBlock changed for ID ${block.id}: $it")
                        })
                        is RadioGroupBlock -> RadioGroupBlockComposable(block, onSelectionChange = {
                            viewModel.onRadioSelectionChanged(block.id, it)
                            Log.d("NoteEditorScreen", "RadioGroupBlock selected item for ID ${block.id}: $it")
                        })
                    }

                    // [MỚI] Hiển thị đường kẻ khi kéo
                    if (isDropTarget && draggingBlockIndex != null && draggingBlockIndex != index) {
                        Divider(
                            color = MaterialTheme.colorScheme.primary,
                            thickness = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Log.d("NoteEditorScreen", "Displaying drop target divider at index: $index")
                    }
                }
                if (index < uiState.content.lastIndex) {
                    Spacer(Modifier.height(8.dp))
                }
            }

            // [MỚI] Thêm một item giả để cho phép thả vào cuối danh sách
            item {
                if (uiState.draggingBlockId != null && currentDropTargetIndex == uiState.content.lastIndex) {
                    Divider(
                        color = MaterialTheme.colorScheme.primary,
                        thickness = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Log.d("NoteEditorScreen", "Displaying drop target divider at end of list.")
                }
                Spacer(Modifier.height(56.dp)) // Đủ không gian cho FAB
            }
        }
    }
}
