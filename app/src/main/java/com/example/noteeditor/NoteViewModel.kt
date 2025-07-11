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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohamedrejeb.richeditor.model.RichTextState // Import RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState // Import rememberRichTextState
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

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFilePath: String? = null
    private var currentPlayingAudioBlockId: String? = null
    private var recordingJob: Job? = null

    // Giới hạn số lượng trạng thái undo/redo
    private val MAX_STACK_SIZE = 50

    // [MỚI] Map để lưu trữ RichTextState cho từng TextBlock
    private val richTextStates: MutableMap<String, RichTextState> = mutableMapOf()

    // [MỚI] StateFlow để theo dõi RichTextState của khối đang được focus (dành cho Toolbar)
    private val _activeRichTextState = MutableStateFlow(RichTextState())
    val activeRichTextState: StateFlow<RichTextState> = _activeRichTextState.asStateFlow()

    init {
        // Đẩy trạng thái khởi tạo vào undo stack
        val initialState = _uiState.value.deepCopy()
        // Khởi tạo RichTextState cho các TextBlock ban đầu
        initialState.content.filterIsInstance<TextBlock>().forEach { textBlock ->
            richTextStates[textBlock.id] = RichTextState().apply { setHtml(textBlock.htmlContent) }
        }
        undoStack.add(initialState)
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
            val restoredState = undoStack.last().deepCopy()
            _uiState.value = restoredState

            // [MỚI] Cập nhật RichTextState trong map dựa trên trạng thái được khôi phục
            updateRichTextStatesFromNoteState(restoredState)

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

            // [MỚI] Cập nhật RichTextState trong map dựa trên trạng thái được khôi phục
            updateRichTextStatesFromNoteState(nextState)

            updateUndoRedoButtons()
            Log.d("UndoRedo", "Redo performed. UndoStack size: ${undoStack.size}, RedoStack size: ${redoStack.size}")
        } else {
            Log.d("UndoRedo", "Cannot redo. RedoStack is empty.")
        }
    }

    // [MỚI] Hàm để cập nhật RichTextState trong map khi trạng thái NoteState thay đổi (undo/redo)
    private fun updateRichTextStatesFromNoteState(noteState: NoteState) {
        val newRichTextStates = mutableMapOf<String, RichTextState>()
        noteState.content.filterIsInstance<TextBlock>().forEach { textBlock ->
            val existingState = richTextStates[textBlock.id]
            if (existingState != null) {
                // Nếu RichTextState đã tồn tại, cập nhật nội dung của nó
                existingState.setHtml(textBlock.htmlContent)
                newRichTextStates[textBlock.id] = existingState
            } else {
                // Nếu chưa tồn tại, tạo mới
                newRichTextStates[textBlock.id] = RichTextState().apply { setHtml(textBlock.htmlContent) }
            }
        }
        // Xóa các RichTextState không còn cần thiết
        richTextStates.clear()
        richTextStates.putAll(newRichTextStates)

        // Cập nhật activeRichTextState nếu khối đang focus là TextBlock
        _uiState.value.focusedBlockId?.let { focusedId ->
            if (richTextStates.containsKey(focusedId)) {
                _activeRichTextState.value = richTextStates[focusedId]!!
            } else {
                _activeRichTextState.value = RichTextState() // Reset nếu khối focus không còn là TextBlock
            }
        } ?: run { _activeRichTextState.value = RichTextState() } // Reset nếu không có khối nào focus
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

    // [ĐÃ SỬA] Xử lý thay đổi nội dung của TextBlock (nhận HTML string)
    fun onTextBlockChange(blockId: String, newHtmlContent: String) {
        val block = _uiState.value.content.find { it.id == blockId } as? TextBlock
        if (block != null) {
            if (block.htmlContent != newHtmlContent) {
                // Chỉ lưu trạng thái nếu nội dung HTML thực sự thay đổi
                saveStateForUndoInternal(_uiState.value.deepCopy())
                block.htmlContent = newHtmlContent
            }
        }
        updateUndoRedoButtons() // Cập nhật trạng thái nút sau mỗi thay đổi nội dung
    }

    // Xử lý thay đổi nội dung của các khối khác (sử dụng TextFieldValue)
    fun onOtherBlockChange(blockId: String, newValue: TextFieldValue) {
        val block = _uiState.value.content.find { it.id == blockId }
        when (block) {
            is SubHeaderBlock -> {
                val oldValue = block.value
                if (oldValue != newValue) {
                    saveStateForUndoInternal(_uiState.value.deepCopy())
                }
                block.value = newValue
            }
            is NumberedListItemBlock -> {
                val oldValue = block.value
                if (oldValue != newValue) {
                    saveStateForUndoInternal(_uiState.value.deepCopy())
                }
                block.value = newValue
            }
            is CheckboxBlock -> {
                val oldValue = block.value
                if (oldValue != newValue || block.isChecked != _uiState.value.content.find { it.id == blockId }?.let { (it as? CheckboxBlock)?.isChecked } ?: false) {
                    saveStateForUndoInternal(_uiState.value.deepCopy())
                    block.value = newValue
                }
            }
            else -> { /* Do nothing */ }
        }
        updateUndoRedoButtons() // Cập nhật trạng thái nút sau mỗi thay đổi nội dung
    }

    // SỬA LỖI: Các hàm định dạng văn bản đã được cập nhật để sử dụng API mới
    fun toggleBold() {
        _activeRichTextState.value.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        val isBold = _activeRichTextState.value.currentSpanStyle.fontWeight == FontWeight.Bold
        Log.d("Formatting", "Toggled Bold. Current isBold: $isBold")
    }

    fun toggleItalic() {
        _activeRichTextState.value.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        val isItalic = _activeRichTextState.value.currentSpanStyle.fontStyle == FontStyle.Italic
        Log.d("Formatting", "Toggled Italic. Current isItalic: $isItalic")
    }

    fun toggleUnderline() {
        _activeRichTextState.value.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        val isUnderline = _activeRichTextState.value.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true
        Log.d("Formatting", "Toggled Underline. Current isUnderline: $isUnderline")
    }

    fun toggleStrikethrough() {
        _activeRichTextState.value.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        val isStrikethrough = _activeRichTextState.value.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true
        Log.d("Formatting", "Toggled Strikethrough. Current isStrikethrough: $isStrikethrough")
    }

    fun setTextAlign(textAlign: TextAlign) {
        _activeRichTextState.value.toggleParagraphStyle(ParagraphStyle(textAlign = textAlign))
        Log.d("Formatting", "Set TextAlign: $textAlign")
    }

    fun setFontSize(size: TextUnit) {
        _activeRichTextState.value.toggleSpanStyle(SpanStyle(fontSize = size))
        Log.d("Formatting", "Set FontSize: ${size.value}sp")
    }

    fun setTextColor(color: Color) {
        _activeRichTextState.value.toggleSpanStyle(SpanStyle(color = color))
        Log.d("Formatting", "Set TextColor: $color")
    }

    fun setTextBackgroundColor(color: Color) {
        _activeRichTextState.value.toggleSpanStyle(SpanStyle(background = color))
        Log.d("Formatting", "Set TextBackgroundColor: $color")
    }

    fun toggleBulletList() {
        _activeRichTextState.value.toggleUnorderedList()
        Log.d("Formatting", "Toggled Bullet List. Current isUnorderedList: ${_activeRichTextState.value.isUnorderedList}")
    }

    fun toggleNumberedList() {
        _activeRichTextState.value.toggleOrderedList()
        Log.d("Formatting", "Toggled Numbered List. Current isOrderedList: ${_activeRichTextState.value.isOrderedList}")
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

            // [MỚI] Nếu là TextBlock, thêm RichTextState vào map
            if (newBlock is TextBlock) {
                richTextStates[newBlock.id] = RichTextState().apply { setHtml(newBlock.htmlContent) }
            }

            // Đảm bảo có một TextBlock trống sau khi chèn nếu khối mới không phải là văn bản
            if (newBlock !is TextBlock && newBlock !is SubHeaderBlock && newBlock !is NumberedListItemBlock && newBlock !is SeparatorBlock && newBlock !is AudioBlock && newBlock !is ImageBlock && newBlock !is DrawingBlock) {
                val newTextBlock = TextBlock()
                state.content.add(insertionPoint + 1, newTextBlock)
                richTextStates[newTextBlock.id] = RichTextState().apply { setHtml(newTextBlock.htmlContent) } // Thêm vào map
                state.focusedBlockId = newTextBlock.id
            } else if (newBlock is TextBlock || newBlock is SubHeaderBlock || newBlock is NumberedListItemBlock || newBlock is AudioBlock || newBlock is ImageBlock || newBlock is DrawingBlock) {
                state.focusedBlockId = newBlock.id
            } else {
                // Nếu là SeparatorBlock, đặt focus về khối trước đó nếu có
                if (focusedIndex != -1 && focusedIndex < state.content.size) {
                    state.focusedBlockId = state.content[focusedIndex].id
                } else if (state.content.isNotEmpty()) {
                    state.focusedBlockId = state.content.last().id
                } else {
                    state.focusedBlockId = null
                }
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
            // SỬA LỖI: Sử dụng hằng số từ lớp MediaRecorder
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
    // Thêm khối SubHeader
    fun addSectionHeader() = addBlockAtCursor(SubHeaderBlock())
    // Thêm khối Numbered List Item
    fun addNumberedListItem() = addBlockAtCursor(NumberedListItemBlock())

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
            } else if (blockToDelete is TextBlock) { // [MỚI] Xóa RichTextState khỏi map khi TextBlock bị xóa
                richTextStates.remove(blockId)
            }
            _uiState.value.content.removeAll { it.id == blockId }
            if (_uiState.value.selectedImageId == blockId) {
                _uiState.value.selectedImageId = null
            }
            // Nếu khối bị xóa là khối đang vẽ, đặt lại trạng thái vẽ
            if (_uiState.value.drawingImageId == blockId) {
                _uiState.value.drawingImageId = null
            }
            // Nếu khối bị xóa là khối đang focus, đặt lại focus
            if (_uiState.value.focusedBlockId == blockId) {
                setFocus(null)
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

            // [MỚI] Cập nhật activeRichTextState khi focus thay đổi
            if (blockId != null) {
                val focusedBlock = state.content.find { it.id == blockId }
                if (focusedBlock is TextBlock) {
                    // Lấy RichTextState từ map hoặc tạo mới nếu chưa có (dù không nên xảy ra)
                    _activeRichTextState.value = richTextStates.getOrPut(focusedBlock.id) {
                        RichTextState().apply { setHtml(focusedBlock.htmlContent) }
                    }
                } else {
                    _activeRichTextState.value = RichTextState() // Reset nếu khối focus không phải TextBlock
                }
            } else {
                _activeRichTextState.value = RichTextState() // Reset nếu không có khối nào focus
            }
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
        richTextStates.clear() // Xóa tất cả RichTextState khi ViewModel bị xóa
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

            // [MỚI] Khi di chuyển, đảm bảo RichTextState của TextBlock vẫn được liên kết đúng
            if (movedBlock is TextBlock) {
                // Không cần làm gì đặc biệt ở đây vì RichTextState đã được quản lý bằng ID
                // và ID của block không thay đổi khi di chuyển.
            }

            // Đảm bảo có một TextBlock trống sau khi di chuyển nếu cần
            if (movedBlock !is TextBlock && toIndex + 1 < contentList.size && contentList[toIndex + 1] !is TextBlock) {
                val newTextBlock = TextBlock()
                contentList.add(toIndex + 1, newTextBlock)
                richTextStates[newTextBlock.id] = RichTextState().apply { setHtml(newTextBlock.htmlContent) } // Thêm vào map
            } else if (movedBlock !is TextBlock && toIndex == contentList.lastIndex) {
                val newTextBlock = TextBlock()
                contentList.add(newTextBlock)
                richTextStates[newTextBlock.id] = RichTextState().apply { setHtml(newTextBlock.htmlContent) } // Thêm vào map
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

    // [MỚI] Hàm để lấy RichTextState cho một TextBlock cụ thể
    fun getOrCreateRichTextState(blockId: String, initialHtml: String): RichTextState {
        return richTextStates.getOrPut(blockId) {
            RichTextState().apply { setHtml(initialHtml) }
        }
    }

    // [MỚI] Các hàm cho Canvas Vẽ
    fun openDrawingCanvas(imageUri: Uri? = null) {
        _uiState.update {
            it.deepCopy().apply {
                isDrawingCanvasOpen = true
                imageUriForDrawing = imageUri
            }
        }
    }

    fun closeDrawingCanvas() {
        _uiState.update {
            it.deepCopy().apply {
                isDrawingCanvasOpen = false
                imageUriForDrawing = null
            }
        }
    }

    fun saveDrawing(bitmap: ImageBitmap) {
        performUndoableAction {
            addBlockAtCursor(DrawingBlock(bitmap))
        }
        closeDrawingCanvas()
    }
}
