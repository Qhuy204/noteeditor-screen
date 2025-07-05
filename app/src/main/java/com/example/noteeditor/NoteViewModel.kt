// File: NoteViewModel.kt
package com.example.noteeditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noteeditor.composables.Style // Import Style enum
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class NoteViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NoteState())
    val uiState: StateFlow<NoteState> = _uiState.asStateFlow()

    // --- Undo/Redo ---
    private val undoStack = mutableListOf<NoteState>()
    private val redoStack = mutableListOf<NoteState>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _pendingStyles = MutableStateFlow<Set<Style>>(emptySet())
    val pendingStyles: StateFlow<Set<Style>> = _pendingStyles.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFilePath: String? = null
    private var currentPlayingAudioBlockId: String? = null
    private var recordingJob: Job? = null

    // Giới hạn số lượng trạng thái undo/redo
    private val MAX_STACK_SIZE = 50

    init {
        // Đẩy trạng thái khởi tạo vào undo stack
        undoStack.add(_uiState.value.deepCopy())
        updateUndoRedoButtons()
    }

    // Cập nhật trạng thái của các nút Undo/Redo
    private fun updateUndoRedoButtons() {
        _canUndo.value = undoStack.size > 1
        _canRedo.value = redoStack.size > 0
        Log.d("UndoRedoButtons", "canUndo: ${_canUndo.value}, canRedo: ${_canRedo.value}")
    }

    // Thêm trạng thái vào undo stack, giới hạn kích thước
    private fun addToUndoStack(state: NoteState) {
        undoStack.add(state)
        if (undoStack.size > MAX_STACK_SIZE) {
            undoStack.removeAt(0) // Xóa phần tử cũ nhất
        }
        Log.d("UndoRedoStack", "Added to undoStack. Size: ${undoStack.size}")
    }

    // Thêm trạng thái vào redo stack, giới hạn kích thước
    private fun addToRedoStack(state: NoteState) {
        redoStack.add(state)
        if (redoStack.size > MAX_STACK_SIZE) {
            redoStack.removeAt(0) // Xóa phần tử cũ nhất
        }
        Log.d("UndoRedoStack", "Added to redoStack. Size: ${redoStack.size}")
    }

    // Lưu trạng thái hiện tại vào undo stack nếu có sự khác biệt đáng kể
    private fun saveStateForUndoInternal(stateToSave: NoteState) {
        val lastSavedState = undoStack.lastOrNull()

        // Chỉ thêm vào stack nếu trạng thái hiện tại khác biệt đáng kể so với trạng thái cuối cùng đã lưu
        if (lastSavedState == null) {
            Log.d("UndoRedo", "Saving initial state (lastSavedState is null).")
            addToUndoStack(stateToSave)
            redoStack.clear() // Xóa redo stack khi có thao tác mới
        } else if (!lastSavedState.isContentEqual(stateToSave)) {
            Log.d("UndoRedo", "Content diff detected. Saving new state.")
            addToUndoStack(stateToSave)
            redoStack.clear() // Xóa redo stack khi có thao tác mới (không phải từ undo/redo)
        } else {
            Log.d("UndoRedo", "State is content equal, skipping save.")
        }
        updateUndoRedoButtons()
    }

    // Hàm này được gọi khi một khối mất tiêu điểm hoặc một hành động hoàn tất
    fun commitActionForUndo() {
        val currentState = _uiState.value.deepCopy()
        val lastSavedState = undoStack.lastOrNull()
        if (lastSavedState == null || !lastSavedState.isContentEqual(currentState)) {
            saveStateForUndoInternal(currentState)
        } else {
            Log.d("UndoRedo", "Commit action: State is equal, no save needed.")
        }
    }

    // Thực hiện thao tác Undo
    fun undo() {
        // Chỉ undo nếu có ít nhất 2 trạng thái (trạng thái hiện tại và trạng thái trước đó)
        if (undoStack.size > 1) {
            // Lưu trạng thái hiện tại vào redo stack trước khi undo
            val currentState = _uiState.value.deepCopy()
            addToRedoStack(currentState)

            // Xóa trạng thái hiện tại khỏi undo stack
            undoStack.removeAt(undoStack.lastIndex)

            // Cập nhật UI về trạng thái trước đó
            _uiState.value = undoStack.last().deepCopy()

            updateUndoRedoButtons()
            Log.d("UndoRedo", "Undo performed. UndoStack size: ${undoStack.size}, RedoStack size: ${redoStack.size}")
        } else {
            Log.d("UndoRedo", "Cannot undo. UndoStack size: ${undoStack.size}")
        }
    }

    // Thực hiện thao tác Redo
    fun redo() {
        if (redoStack.isNotEmpty()) {
            // Lưu trạng thái hiện tại vào undo stack trước khi redo
            val currentState = _uiState.value.deepCopy()
            addToUndoStack(currentState)

            // Lấy trạng thái tiếp theo từ redo stack
            val nextState = redoStack.removeAt(redoStack.lastIndex)
            _uiState.value = nextState

            updateUndoRedoButtons()
            Log.d("UndoRedo", "Redo performed. UndoStack size: ${undoStack.size}, RedoStack size: ${redoStack.size}")
        } else {
            Log.d("UndoRedo", "Cannot redo. RedoStack is empty.")
        }
    }

    // Hàm hỗ trợ để thực hiện một hành động có thể hoàn tác
    private fun performUndoableAction(action: () -> Unit) {
        // Lưu trạng thái *trước khi* hành động được thực hiện
        saveStateForUndoInternal(_uiState.value.deepCopy())
        action()
        // Sau khi hành động hoàn tất, cập nhật lại trạng thái nút
        updateUndoRedoButtons()
    }

    // Đảm bảo onTitleChange cũng gọi saveStateForUndoInternal
    fun onTitleChange(newTitle: String) {
        // Chỉ lưu trạng thái nếu tiêu đề thực sự thay đổi
        if (_uiState.value.title != newTitle) {
            saveStateForUndoInternal(_uiState.value.deepCopy()) // Lưu trước khi thay đổi
            _uiState.value.title = newTitle
            updateUndoRedoButtons()
        }
    }

    // Xử lý thay đổi nội dung của một ContentBlock
    fun onContentBlockChange(blockId: String, newValue: TextFieldValue) {
        val block = _uiState.value.content.find { it.id == blockId }
        when (block) {
            is TextBlock -> {
                val oldValue = block.value
                // Lưu trạng thái nếu TextFieldValue đã thay đổi (bao gồm text, selection, span/paragraph styles)
                if (oldValue != newValue) {
                    Log.d("ContentChange", "TextBlock value changed. Saving state.")
                    saveStateForUndoInternal(_uiState.value.deepCopy())
                }

                val builder = AnnotatedString.Builder(newValue.annotatedString)

                // Re-apply existing styles to the new text range
                // This ensures styles persist correctly when text is inserted/deleted
                val textDiff = newValue.text.length - oldValue.text.length
                oldValue.annotatedString.spanStyles.forEach {
                    val adjustedStart = (it.start + textDiff).coerceAtLeast(0)
                    val adjustedEnd = (it.end + textDiff).coerceAtLeast(0)
                    // Only add style if the range is valid and not empty
                    if (adjustedStart < adjustedEnd) {
                        builder.addStyle(it.item, adjustedStart, adjustedEnd)
                    }
                }

                val textAdded = newValue.text.length > oldValue.text.length
                if (textAdded && _pendingStyles.value.isNotEmpty()) {
                    // Apply pending styles to the newly typed text
                    val start = oldValue.selection.start
                    val end = newValue.selection.end
                    if (start < end) { // This condition is for the selected range where new text is inserted
                        val combinedStyle = createCombinedSpanStyle(_pendingStyles.value)
                        builder.addStyle(combinedStyle, start, end)
                    }
                }

                block.value = TextFieldValue(
                    annotatedString = builder.toAnnotatedString(),
                    selection = newValue.selection
                )

                // Clear pending styles only if they were actually applied or if text changed
                if (textAdded || _pendingStyles.value.isNotEmpty()) {
                    _pendingStyles.value = emptySet()
                }
            }
            is CheckboxBlock -> {
                // Kiểm tra nếu giá trị checkbox thực sự thay đổi
                if (block.value != newValue || block.isChecked != _uiState.value.content.find { it.id == blockId }?.let { (it as? CheckboxBlock)?.isChecked } ?: false) {
                    saveStateForUndoInternal(_uiState.value.deepCopy()) // Lưu trước khi thay đổi
                    block.value = newValue
                }
            }
            else -> { /* Do nothing */ }
        }
        updateUndoRedoButtons() // Cập nhật trạng thái nút sau mỗi thay đổi nội dung
    }

    // Tạo SpanStyle kết hợp từ một tập hợp các Style
    private fun createCombinedSpanStyle(styles: Set<Style>): SpanStyle {
        var combined = SpanStyle()
        if (styles.contains(Style.BOLD)) combined = combined.merge(SpanStyle(fontWeight = FontWeight.Bold))
        if (styles.contains(Style.ITALIC)) combined = combined.merge(SpanStyle(fontStyle = FontStyle.Italic))
        if (styles.contains(Style.UNDERLINE)) combined = combined.merge(SpanStyle(textDecoration = TextDecoration.Underline))
        if (styles.contains(Style.STRIKETHROUGH)) combined = combined.merge(SpanStyle(textDecoration = TextDecoration.LineThrough))
        return combined
    }

    // Bật/tắt một kiểu định dạng (Bold, Italic, Underline, Strikethrough)
    fun toggleStyle(style: Style) {
        val focusedId = _uiState.value.focusedBlockId ?: return
        val block = _uiState.value.content.find { it.id == focusedId } as? TextBlock ?: return
        val selection = block.value.selection

        if (!selection.collapsed) { // Nếu có văn bản được chọn
            performUndoableAction {
                val builder = AnnotatedString.Builder(block.value.annotatedString)
                val existingStyles = block.value.annotatedString.spanStyles.filter {
                    maxOf(it.start, selection.start) < minOf(it.end, selection.end)
                }
                val hasStyle = when (style) {
                    Style.BOLD -> existingStyles.any { it.item.fontWeight == FontWeight.Bold }
                    Style.ITALIC -> existingStyles.any { it.item.fontStyle == FontStyle.Italic }
                    Style.UNDERLINE -> existingStyles.any { it.item.textDecoration?.contains(TextDecoration.Underline) == true }
                    Style.STRIKETHROUGH -> existingStyles.any { it.item.textDecoration?.contains(TextDecoration.LineThrough) == true }
                }

                val newSpanStyle = if (hasStyle) {
                    when (style) {
                        Style.BOLD -> SpanStyle(fontWeight = FontWeight.Normal)
                        Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Normal)
                        Style.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.None)
                        Style.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.None)
                    }
                } else {
                    when (style) {
                        Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                        Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                        Style.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                        Style.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    }
                }
                builder.addStyle(newSpanStyle, selection.start, selection.end)
                block.value = block.value.copy(annotatedString = builder.toAnnotatedString())
            }
        } else { // Nếu không có văn bản được chọn (chỉ có con trỏ)
            val currentStyles = _pendingStyles.value
            _pendingStyles.value = if (currentStyles.contains(style)) {
                currentStyles - style
            } else {
                currentStyles + style
            }
            // Không cần saveStateForUndoInternal ở đây, vì pendingStyles sẽ được áp dụng khi gõ hoặc khi mất focus
            updateUndoRedoButtons() // Cập nhật trạng thái nút
        }
    }

    // Áp dụng SpanStyle cho vùng chọn
    private fun applySpanStyleToSelection(styleToApply: SpanStyle) {
        performUndoableAction {
            val focusedId = _uiState.value.focusedBlockId ?: return@performUndoableAction
            val block = _uiState.value.content.find { it.id == focusedId } as? TextBlock ?: return@performUndoableAction

            val selection = block.value.selection
            if (selection.collapsed) {
                return@performUndoableAction
            }

            val builder = AnnotatedString.Builder(block.value.annotatedString)
            builder.addStyle(styleToApply, selection.start, selection.end)

            block.value = block.value.copy(annotatedString = builder.toAnnotatedString())
        }
    }

    // Đặt căn lề văn bản
    fun setTextAlign(textAlign: TextAlign) {
        performUndoableAction {
            val focusedId = _uiState.value.focusedBlockId ?: return@performUndoableAction
            val block = _uiState.value.content.find { it.id == focusedId } as? TextBlock ?: return@performUndoableAction
            block.paragraphStyle = ParagraphStyle(textAlign = textAlign)
        }
    }

    // Đặt kích thước chữ
    fun setFontSize(size: TextUnit) = applySpanStyleToSelection(SpanStyle(fontSize = size))
    // Đặt màu chữ
    fun setTextColor(color: Color) = applySpanStyleToSelection(SpanStyle(color = color))
    // Đặt màu nền chữ
    fun setTextBackgroundColor(color: Color) = applySpanStyleToSelection(SpanStyle(background = color))

    // Bật/tắt kiểu danh sách (bulleted list)
    fun toggleListStyle() {
        performUndoableAction {
            val focusedId = _uiState.value.focusedBlockId ?: return@performUndoableAction
            val block = _uiState.value.content.find { it.id == focusedId } as? TextBlock ?: return@performUndoableAction
            block.isListItem = !block.isListItem
        }
    }

    // Xử lý thay đổi trạng thái của Checkbox
    fun onCheckboxCheckedChange(blockId: String, isChecked: Boolean) {
        performUndoableAction {
            (_uiState.value.content.find { it.id == blockId } as? CheckboxBlock)?.isChecked = isChecked
        }
    }

    // Thêm một khối mới tại vị trí con trỏ
    private fun addBlockAtCursor(newBlock: ContentBlock) {
        performUndoableAction {
            val state = _uiState.value
            val focusedIndex = state.content.indexOfFirst { it.id == state.focusedBlockId }
            val insertionPoint = if (focusedIndex != -1) focusedIndex + 1 else state.content.size

            state.content.add(insertionPoint, newBlock)
            if (newBlock !is SeparatorBlock && newBlock !is AudioBlock && newBlock !is ImageBlock) { // Thêm ImageBlock vào điều kiện này
                val nextTextBlock = TextBlock()
                state.content.add(insertionPoint + 1, nextTextBlock)
                state.focusedBlockId = nextTextBlock.id
            } else if (newBlock is AudioBlock || newBlock is ImageBlock) { // Cập nhật để ImageBlock cũng được focus
                state.focusedBlockId = newBlock.id
            }
            state.selectedImageId = null
        }
    }

    // Thêm khối ảnh tại vị trí con trỏ
    fun addImageAtCursor(uri: Uri) = addBlockAtCursor(ImageBlock(uri = uri))

    // [CẬP NHẬT] Bắt đầu ghi âm mới
    fun startNewAudioRecording(context: Context) {
        if (_uiState.value.isRecordingActive) {
            return // Bỏ qua nếu đang ghi âm
        }

        val outputFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp3")
        currentRecordingFilePath = outputFile.absolutePath

        // Tạo một khối âm thanh tạm thời, chưa thêm vào nội dung chính
        val tempAudioBlock = AudioBlock(
            uri = Uri.fromFile(outputFile),
            initialIsRecording = true,
            initialFilePath = outputFile.absolutePath
        )

        // Cập nhật trạng thái để hiển thị UI ghi âm
        _uiState.update { currentState ->
            currentState.deepCopy().apply {
                this.currentRecordingAudioBlock = tempAudioBlock
                this.isRecordingActive = true
                this.isTextFormatToolbarVisible = false
            }
        }
        startRecordingInternal(currentRecordingFilePath!!, context)
    }

    // Bắt đầu ghi âm nội bộ
    private fun startRecordingInternal(filePath: String, context: Context) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder = recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(filePath)
            try {
                prepare()
                start()
                recordingJob = viewModelScope.launch {
                    val startTime = System.currentTimeMillis()
                    while (_uiState.value.isRecordingActive) {
                        val elapsedMillis = System.currentTimeMillis() - startTime
                        // Cập nhật thời gian trên khối tạm thời
                        _uiState.value.currentRecordingAudioBlock?.recordingTimeMillis = elapsedMillis
                        _uiState.value.currentRecordingAudioBlock?.duration = formatDuration(elapsedMillis)
                        delay(1000)
                    }
                }
                Log.d("AudioRecording", "Recording started: $filePath")
            } catch (e: IOException) {
                Log.e("AudioRecording", "Recording failed: ${e.message}")
                cancelRecording() // Hủy nếu không thể bắt đầu
            }
        }
    }

    // [CẬP NHẬT] Lưu bản ghi âm
    fun saveRecordedAudio() {
        // Không lưu các bản ghi quá ngắn (ví dụ: dưới 1 giây)
        val recordedMillis = _uiState.value.currentRecordingAudioBlock?.recordingTimeMillis ?: 0L
        if (recordedMillis < 1000) {
            cancelRecording()
            return
        }

        // Dừng quá trình ghi
        recordingJob?.cancel()
        recordingJob = null

        val blockToSave = _uiState.value.currentRecordingAudioBlock
        val path = currentRecordingFilePath

        try {
            mediaRecorder?.stop()
            Log.d("AudioRecording", "Recording stopped for saving: $path")
        } catch (e: Exception) {
            Log.e("AudioRecording", "Error stopping recorder, cancelling.", e)
            path?.let { File(it).delete() } // Xóa file tạm nếu dừng lỗi
            resetRecordingState() // Đặt lại trạng thái
            return
        } finally {
            releaseRecorder()
        }

        // Hoàn thiện khối âm thanh và thêm vào ghi chú
        if (blockToSave != null && path != null) {
            val finalDuration = getFileDuration(path)
            blockToSave.duration = finalDuration
            blockToSave.isRecording = false

            // Thêm khối đã hoàn tất vào nội dung (hành động này có thể hoàn tác)
            addBlockAtCursor(blockToSave)
        }

        // Đặt lại trạng thái ghi âm
        resetRecordingState()
    }

    // [CẬP NHẬT] Hủy ghi âm
    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w("AudioRecording", "Exception on stopping recorder during cancellation: ${e.message}")
        }
        releaseRecorder()

        // Xóa tệp âm thanh tạm thời
        currentRecordingFilePath?.let { File(it).delete() }

        // Đặt lại trạng thái mà không thêm bất cứ thứ gì vào ghi chú
        resetRecordingState()
        Log.d("AudioRecording", "Recording cancelled.")
    }

    // Hàm tiện ích để đặt lại trạng thái ghi âm
    private fun resetRecordingState() {
        _uiState.update {
            it.deepCopy().apply {
                isRecordingActive = false
                currentRecordingAudioBlock = null
            }
        }
        currentRecordingFilePath = null
    }

    // Lấy thời lượng của tệp âm thanh
    private fun getFileDuration(path: String?): String {
        if (path == null) return "00:00"
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            return "00:00"
        }

        val player = MediaPlayer()
        return try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            val durationMillis = player.duration.toLong()
            formatDuration(durationMillis)
        } catch (e: Exception) {
            Log.e("AudioDuration", "Could not get duration for $path", e)
            "00:00"
        } finally {
            player.release()
        }
    }

    // Kiểm tra xem có đang ghi âm không
    fun isAnyAudioRecording(): Boolean {
        return _uiState.value.isRecordingActive
    }

    // Thêm khối Checkbox
    fun addCheckbox() = addBlockAtCursor(CheckboxBlock())
    // Thêm khối Separator
    fun addSeparator() = addBlockAtCursor(SeparatorBlock())
    // Thêm khối Toggle Switch
    fun addToggleSwitch() = addBlockAtCursor(ToggleSwitchBlock())
    // Thêm khối Accordion
    fun addAccordion() = addBlockAtCursor(AccordionBlock())
    // Thêm khối Radio Group
    fun addRadioGroup() = addBlockAtCursor(RadioGroupBlock())

    // Xử lý click vào ảnh
    fun onImageClick(imageId: String) {
        val state = _uiState.value
        state.selectedImageId = if (state.selectedImageId == imageId) null else imageId
        // Khi chọn hoặc bỏ chọn ảnh, đảm bảo không có khối văn bản nào đang được focus
        setFocus(null)
    }

    // Xóa một khối
    fun deleteBlock(blockId: String) {
        performUndoableAction {
            val blockToDelete = _uiState.value.content.find { it.id == blockId }
            if (blockToDelete is AudioBlock) {
                if (currentPlayingAudioBlockId == blockId) {
                    stopPlaying()
                }
                if (blockToDelete.isRecording && _uiState.value.currentRecordingAudioBlock?.id == blockId) {
                    cancelRecording()
                }
                blockToDelete.filePath?.let { File(it).delete() }
            }
            _uiState.value.content.removeAll { it.id == blockId }
            if (_uiState.value.selectedImageId == blockId) {
                _uiState.value.selectedImageId = null
            }
            // Nếu khối bị xóa là khối đang vẽ, đặt lại trạng thái vẽ
            if (_uiState.value.drawingImageId == blockId) {
                _uiState.value.drawingImageId = null
            }
        }
    }

    // Thay đổi kích thước ảnh
    fun resizeImage(blockId: String) {
        performUndoableAction {
            val block = _uiState.value.content.find { it.id == blockId } as? ImageBlock
            if (block != null) {
                block.isResized = !block.isResized
            }
        }
    }

    // Cập nhật mô tả ảnh
    fun updateImageDescription(blockId: String, description: String) {
        // Không cần performUndoableAction ở đây nếu chỉ là thay đổi mô tả mà không muốn lưu vào undo stack mỗi khi gõ
        // Nếu muốn lưu, cần thêm logic saveStateForUndoInternal tương tự TextBlock
        (_uiState.value.content.find { it.id == blockId } as? ImageBlock)?.description = description
    }

    // Bật/tắt chế độ vẽ trên ảnh
    fun toggleDrawingMode(imageId: String?) {
        _uiState.update { currentState ->
            currentState.deepCopy().apply {
                drawingImageId = if (drawingImageId == imageId) null else imageId
                // Đảm bảo không có ảnh nào được chọn khi đang vẽ
                if (drawingImageId != null) {
                    selectedImageId = null
                }
            }
        }
    }

    // Copy ảnh
    fun copyImage(context: Context, blockId: String) {
        val imageBlock = _uiState.value.content.find { it.id == blockId } as? ImageBlock
        imageBlock?.uri?.let { uri ->
            try {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newUri(context.contentResolver, "Image", uri)
                clipboardManager.setPrimaryClip(clipData)
                Log.d("NoteViewModel", "Image URI copied to clipboard: $uri")
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Failed to copy image URI to clipboard: ${e.message}", e)
            }
        } ?: Log.e("NoteViewModel", "Image block or URI not found for ID: $blockId")
    }

    // Mở ảnh trong thư viện (logic placeholder)
    fun openImageInGallery(blockId: String) {
        Log.d("NoteViewModel", "Opening image with ID: $blockId in gallery (Placeholder)")
        // Trong một ứng dụng thực tế, bạn sẽ tạo một Intent để mở ảnh bằng ứng dụng thư viện
        // val uri = (_uiState.value.content.find { it.id == blockId } as? ImageBlock)?.uri
        // uri?.let {
        //     val intent = Intent(Intent.ACTION_VIEW).apply {
        //         setDataAndType(it, "image/*")
        //         addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        //     }
        //     if (intent.resolveActivity(context.packageManager) != null) {
        //         context.startActivity(intent)
        //     } else {
        //         Log.e("NoteViewModel", "No app found to open image.")
        //     }
        // }
    }

    // Đặt tiêu điểm vào một khối
    fun setFocus(blockId: String?) {
        val state = _uiState.value
        if (state.focusedBlockId != blockId) {
            // Khi tiêu điểm thay đổi, commit hành động trước đó để lưu trạng thái
            if (state.focusedBlockId != null) {
                commitActionForUndo()
            }
            state.focusedBlockId = blockId
            // Khi đặt focus vào một khối, đảm bảo không có ảnh nào đang được chọn hoặc vẽ
            state.selectedImageId = null
            state.drawingImageId = null
            _pendingStyles.value = emptySet()
        }
        updateUndoRedoButtons() // Cập nhật trạng thái nút khi tiêu điểm thay đổi
    }

    // Bật/tắt thanh công cụ định dạng văn bản
    fun toggleTextFormatToolbar(isVisible: Boolean) {
        _uiState.value.isTextFormatToolbarVisible = isVisible
    }

    // Xử lý bật/tắt Accordion
    fun onAccordionToggled(blockId: String) {
        performUndoableAction {
            val block = _uiState.value.content.find { it.id == blockId } as? AccordionBlock
            if (block != null) {
                block.isExpanded = !block.isExpanded
            }
        }
    }

    // Xử lý thay đổi trạng thái Toggle Switch
    fun onToggleSwitchChanged(blockId: String, isOn: Boolean) {
        performUndoableAction {
            (_uiState.value.content.find { it.id == blockId } as? ToggleSwitchBlock)?.isOn = isOn
        }
    }

    // Xử lý thay đổi lựa chọn Radio Group
    fun onRadioSelectionChanged(groupId: String, selectedItemId: String) {
        performUndoableAction {
            (_uiState.value.content.find { it.id == groupId } as? RadioGroupBlock)?.selectedId = selectedItemId
        }
    }

    // Lưu ghi chú
    fun saveNote() {
        viewModelScope.launch {
            commitActionForUndo() // Đảm bảo trạng thái cuối cùng được lưu
            Log.d("NoteEditorDebug", "Note Saved: Title='${_uiState.value.title}', Content size=${_uiState.value.content.size}")
        }
    }

    // Bật/tắt phát âm thanh
    fun togglePlaying(audioBlockId: String, filePath: String?) {
        if (filePath == null) return

        val audioBlock = _uiState.value.content.find { it.id == audioBlockId } as? AudioBlock
        if (audioBlock == null) return

        if (audioBlock.isPlaying) {
            stopPlaying()
        } else {
            stopPlaying()

            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(filePath)
                    prepare()
                    start()
                    currentPlayingAudioBlockId = audioBlockId
                    audioBlock.isPlaying = true
                    Log.d("AudioPlaying", "Playback started: $filePath")

                    setOnCompletionListener {
                        audioBlock.isPlaying = false
                        releasePlayer()
                    }
                } catch (e: IOException) {
                    Log.e("AudioPlaying", "Audio playback failed: ${e.message}")
                    audioBlock.isPlaying = false
                    releasePlayer()
                }
            }
        }
    }

    // Dừng phát âm thanh
    fun stopPlaying() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            releasePlayer()
            currentPlayingAudioBlockId?.let { id ->
                (_uiState.value.content.find { it.id == id } as? AudioBlock)?.isPlaying = false
            }
            currentPlayingAudioBlockId = null
        }
    }

    // Giải phóng MediaPlayer
    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // Giải phóng MediaRecorder
    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    // Định dạng thời lượng từ mili giây sang MM:SS
    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    // Xử lý khi ViewModel bị xóa
    override fun onCleared() {
        super.onCleared()
        releaseRecorder()
        releasePlayer()
        recordingJob?.cancel()
    }

    // Hàm để di chuyển một ContentBlock
    fun moveContentBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex < 0 || fromIndex >= _uiState.value.content.size ||
            toIndex < 0 || toIndex > _uiState.value.content.size) {
            return
        }
        performUndoableAction {
            val contentList = _uiState.value.content
            val movedBlock = contentList.removeAt(fromIndex)
            contentList.add(toIndex, movedBlock)
            // Đảm bảo có một TextBlock trống sau khi di chuyển nếu cần
            if (movedBlock !is TextBlock && toIndex + 1 < contentList.size && contentList[toIndex + 1] !is TextBlock) {
                contentList.add(toIndex + 1, TextBlock())
            } else if (movedBlock !is TextBlock && toIndex == contentList.lastIndex) {
                contentList.add(TextBlock())
            }
            Log.d("NoteEditorDebug", "Moved block from $fromIndex to $toIndex")
        }
    }

    // Các hàm hỗ trợ kéo thả
    fun setDraggingBlockId(id: String?) {
        _uiState.update { it.deepCopy().apply { draggingBlockId = id } }
    }

    fun setDropTargetIndex(index: Int?) {
        _uiState.update { it.deepCopy().apply { dropTargetIndex = index } }
    }

    // Hàm tiện ích để debug undo/redo stacks
    fun getUndoRedoStackInfo(): String {
        return "UndoStack: ${undoStack.size}, RedoStack: ${redoStack.size}"
    }
}
