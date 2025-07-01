package com.example.noteeditor

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.noteeditor.composables.*
import java.io.File
import java.util.Objects

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    // [MỚI] State để hiển thị bottom sheet chọn nguồn ảnh
    var showImageSourceSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // [MỚI] State để giữ Uri của ảnh chụp từ camera
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        Log.d("NoteEditorDebug", "NoteEditorScreen recomposed.")
    }

    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            viewModel.toggleTextFormatToolbar(false)
            viewModel.onImageClick("")
        }
    }

    // [MỚI] Hàm trợ giúp để tạo Uri cho tệp ảnh mới
    fun createImageUri(context: Context): Uri {
        val file = File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_",
            ".jpg",
            context.externalCacheDir
        )
        return FileProvider.getUriForFile(
            Objects.requireNonNull(context),
            // Authority này phải khớp với khai báo trong AndroidManifest.xml
            "com.example.noteeditor.provider",
            file
        )
    }

    // Trình khởi chạy để chọn ảnh từ thư viện (không đổi)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.addImageAtCursor(it) }
    }

    // [MỚI] Trình khởi chạy để chụp ảnh bằng camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let { viewModel.addImageAtCursor(it) }
        }
    }

    Scaffold(
        topBar = {
            NoteEditorTopAppBar(
                onBackClick = onBack,
                onSaveClick = {
                    focusManager.clearFocus()
                    viewModel.saveNote()
                },
                onUndoClick = { viewModel.undo() },
                onRedoClick = { viewModel.redo() },
                canUndo = canUndo,
                canRedo = canRedo
            )
        },
        containerColor = Color.White,
        floatingActionButton = {
            if (showAddMoreMenu) {
                ModalBottomSheet(onDismissRequest = { showAddMoreMenu = false }) {
                    Column(Modifier.padding(bottom = 32.dp)) {
                        ListItem(
                            headlineContent = { Text("Thêm Radio Button Group") },
                            leadingContent = { Icon(Icons.Default.RadioButtonChecked, null) },
                            modifier = Modifier.clickable { viewModel.addRadioGroup(); showAddMoreMenu = false }
                        )
                        ListItem(
                            headlineContent = { Text("Thêm Toggle Switch") },
                            leadingContent = { Icon(Icons.Default.ToggleOn, null) },
                            modifier = Modifier.clickable { viewModel.addToggleSwitch(); showAddMoreMenu = false }
                        )
                        ListItem(
                            headlineContent = { Text("Thêm Accordion") },
                            leadingContent = { Icon(Icons.Default.KeyboardArrowDown, null) },
                            modifier = Modifier.clickable { viewModel.addAccordion(); showAddMoreMenu = false }
                        )
                    }
                }
            }
        },
        bottomBar = {
            TransformingBottomToolbar(
                isKeyboardVisible = isKeyboardVisible,
                isFormattingMode = uiState.isTextFormatToolbarVisible,
                activeStyles = uiState.activeStyles,
                onToggleFormattingMode = { viewModel.toggleTextFormatToolbar(!uiState.isTextFormatToolbarVisible) },
                // [THAY ĐỔI] Hiển thị bottom sheet thay vì mở trực tiếp thư viện
                onAddImageClick = { showImageSourceSheet = true },
                onAddCheckboxClick = { viewModel.addCheckbox() },
                onAddAudioClick = { viewModel.addAudioBlock() },
                onAddMoreClick = { showAddMoreMenu = it },
                onStyleChange = { viewModel.toggleStyle(it) },
                onTextAlignChange = { viewModel.setTextAlign(it) },
                onListStyleChange = { viewModel.toggleListStyle() },
                onAddSeparator = { viewModel.addSeparator() },
                onFontSizeChange = { viewModel.setFontSize(it) },
                onTextColorChange = { viewModel.setTextColor(it) },
                onTextBgColorChange = { viewModel.setTextBackgroundColor(it) }
            )
        }
    ) { paddingValues ->
        // [MỚI] Bottom sheet để chọn nguồn ảnh
        if (showImageSourceSheet) {
            ModalBottomSheet(onDismissRequest = { showImageSourceSheet = false }) {
                Column(Modifier.padding(bottom = 32.dp)) {
                    ListItem(
                        headlineContent = { Text("Chụp ảnh") },
                        leadingContent = { Icon(Icons.Default.PhotoCamera, null) },
                        modifier = Modifier.clickable {
                            showImageSourceSheet = false
                            val uri = createImageUri(context)
                            tempImageUri = uri
                            cameraLauncher.launch(uri)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Chọn từ thư viện") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                        modifier = Modifier.clickable {
                            showImageSourceSheet = false
                            imagePickerLauncher.launch("image/*")
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = { /* TODO: Category selection logic */ },
                        label = { Text(uiState.category) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = Color.Gray) },
                        shape = RoundedCornerShape(20.dp),
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF2F2F2)),
                        border = null
                    )
                }
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    BasicTextField(
                        value = uiState.title,
                        onValueChange = { viewModel.onTitleChange(it) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    viewModel.commitActionForUndo()
                                }
                            },
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (uiState.title.isEmpty()) {
                                Text("Tiêu đề", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            itemsIndexed(uiState.content, key = { _, block -> block.id }) { index, block ->
                when (block) {
                    is TextBlock -> TextBlockComposable(block,
                        onValueChange = { viewModel.onContentBlockChange(block.id, it) },
                        onFocusChange = { focusState ->
                            if (focusState.isFocused) {
                                viewModel.setFocus(block.id)
                            } else {
                                viewModel.commitActionForUndo()
                            }
                        }
                    )
                    is ImageBlock -> ImageBlockComposable(block, uiState.selectedImageId == block.id,
                        onImageClick = {
                            viewModel.onImageClick(block.id)
                            focusManager.clearFocus()
                        },
                        onResize = { viewModel.resizeImage(block.id) },
                        onDelete = { viewModel.deleteBlock(block.id) },
                        onDescriptionChange = { newDesc -> viewModel.updateImageDescription(block.id, newDesc) }
                    )
                    is CheckboxBlock -> CheckboxBlockComposable(block,
                        onCheckedChange = { viewModel.onCheckboxCheckedChange(block.id, it) },
                        onValueChange = { viewModel.onContentBlockChange(block.id, it) },
                        onFocusChange = { focusState ->
                            if (focusState.isFocused) {
                                viewModel.setFocus(block.id)
                            } else {
                                viewModel.commitActionForUndo()
                            }
                        }
                    )
                    is SeparatorBlock -> SeparatorBlockComposable()
                    is AudioBlock -> AudioBlockComposable(block, onDelete = { viewModel.deleteBlock(block.id) })
                    is AccordionBlock -> AccordionBlockComposable(block, onToggle = { viewModel.onAccordionToggled(block.id) })
                    is ToggleSwitchBlock -> ToggleSwitchBlockComposable(block, onToggle = { viewModel.onToggleSwitchChanged(block.id, it) })
                    is RadioGroupBlock -> RadioGroupBlockComposable(block, onSelectionChange = { viewModel.onRadioSelectionChanged(block.id, it) })
                }
                if (index < uiState.content.lastIndex) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}