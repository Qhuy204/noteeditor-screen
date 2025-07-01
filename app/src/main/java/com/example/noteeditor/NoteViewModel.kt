// File: NoteViewModel.kt
package com.example.noteeditor

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

    private fun saveForUndo() {
        undoStack.add(_uiState.value.deepCopy())
        redoStack.clear()
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        _canUndo.value = true
        _canRedo.value = false
    }

    fun commitActionForUndo() {
        val currentState = _uiState.value
        if (undoStack.isEmpty() || undoStack.last().content.size != currentState.content.size || undoStack.last().title != currentState.title) {
            Log.d("NoteEditorDebug", "Committing action for undo.")
            saveForUndo()
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_uiState.value.deepCopy())
            _uiState.value = undoStack.removeAt(undoStack.lastIndex)
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_uiState.value.deepCopy())
            _uiState.value = redoStack.removeAt(redoStack.lastIndex)
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
        }
    }

    private fun performUndoableAction(action: () -> Unit) {
        saveForUndo()
        action()
    }

    fun onTitleChange(newTitle: String) {
        _uiState.value.title = newTitle
    }

    fun onContentBlockChange(blockId: String, newValue: TextFieldValue) {
        val block = _uiState.value.content.find { it.id == blockId }
        when (block) {
            is TextBlock -> {
                val oldValue = block.value

                if (oldValue.text == newValue.text) {
                    block.value = oldValue.copy(
                        selection = newValue.selection,
                        composition = newValue.composition
                    )
                    if (_pendingStyles.value.isNotEmpty()) {
                        _pendingStyles.value = emptySet()
                    }
                    return
                }

                val builder = AnnotatedString.Builder(newValue.annotatedString)

                oldValue.annotatedString.spanStyles.forEach {
                    builder.addStyle(it.item, it.start, it.end)
                }

                val textAdded = newValue.text.length > oldValue.text.length
                if (textAdded && _pendingStyles.value.isNotEmpty()) {
                    val start = oldValue.selection.start
                    val end = newValue.selection.end
                    if (start < end) {
                        val combinedStyle = createCombinedSpanStyle(_pendingStyles.value)
                        builder.addStyle(combinedStyle, start, end)
                    }
                }

                block.value = TextFieldValue(
                    annotatedString = builder.toAnnotatedString(),
                    selection = newValue.selection
                )
            }
            is CheckboxBlock -> block.value = newValue
            else -> { /* Do nothing */ }
        }
    }

    private fun createCombinedSpanStyle(styles: Set<Style>): SpanStyle {
        var combined = SpanStyle()
        if (styles.contains(Style.BOLD)) combined = combined.merge(SpanStyle(fontWeight = FontWeight.Bold))
        if (styles.contains(Style.ITALIC)) combined = combined.merge(SpanStyle(fontStyle = FontStyle.Italic))
        if (styles.contains(Style.UNDERLINE)) combined = combined.merge(SpanStyle(textDecoration = TextDecoration.Underline))
        if (styles.contains(Style.STRIKETHROUGH)) combined = combined.merge(SpanStyle(textDecoration = TextDecoration.LineThrough))
        return combined
    }

    fun toggleStyle(style: Style) {
        val focusedId = _uiState.value.focusedBlockId ?: return
        val block = _uiState.value.content.find { it.id == focusedId } as? TextBlock ?: return
        val selection = block.value.selection

        if (!selection.collapsed) {
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
        } else {
            val currentStyles = _pendingStyles.value
            _pendingStyles.value = if (currentStyles.contains(style)) {
                currentStyles - style
            } else {
                currentStyles + style
            }
        }
    }

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

    fun setTextAlign(textAlign: TextAlign) {
        performUndoableAction {
            val focusedId = _uiState.value.focusedBlockId ?: return@performUndoableAction
            val block = _uiState.value.content.find { it.id == focusedId } as? TextBlock ?: return@performUndoableAction
            block.paragraphStyle = ParagraphStyle(textAlign = textAlign)
        }
    }

    fun setFontSize(size: TextUnit) = applySpanStyleToSelection(SpanStyle(fontSize = size))
    fun setTextColor(color: Color) = applySpanStyleToSelection(SpanStyle(color = color))
    fun setTextBackgroundColor(color: Color) = applySpanStyleToSelection(SpanStyle(background = color))

    fun toggleListStyle() {
        performUndoableAction {
            val focusedId = _uiState.value.focusedBlockId ?: return@performUndoableAction
            val block = _uiState.value.content.find { it.id == focusedId } as? TextBlock ?: return@performUndoableAction
            block.isListItem = !block.isListItem
        }
    }

    fun onCheckboxCheckedChange(blockId: String, isChecked: Boolean) {
        performUndoableAction {
            (_uiState.value.content.find { it.id == blockId } as? CheckboxBlock)?.isChecked = isChecked
        }
    }

    private fun addBlockAtCursor(newBlock: ContentBlock) {
        performUndoableAction {
            val state = _uiState.value
            val focusedIndex = state.content.indexOfFirst { it.id == state.focusedBlockId }
            val insertionPoint = if (focusedIndex != -1) focusedIndex + 1 else state.content.size

            state.content.add(insertionPoint, newBlock)
            if (newBlock !is SeparatorBlock && newBlock !is AudioBlock) {
                val nextTextBlock = TextBlock()
                state.content.add(insertionPoint + 1, nextTextBlock)
                state.focusedBlockId = nextTextBlock.id
            } else if (newBlock is AudioBlock) {
                state.focusedBlockId = newBlock.id
            }
            state.selectedImageId = null
        }
    }

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

    fun isAnyAudioRecording(): Boolean {
        return _uiState.value.isRecordingActive
    }

    fun addCheckbox() = addBlockAtCursor(CheckboxBlock())
    fun addSeparator() = addBlockAtCursor(SeparatorBlock())
    fun addToggleSwitch() = addBlockAtCursor(ToggleSwitchBlock())
    fun addAccordion() = addBlockAtCursor(AccordionBlock())
    fun addRadioGroup() = addBlockAtCursor(RadioGroupBlock())

    fun onImageClick(imageId: String) {
        val state = _uiState.value
        state.selectedImageId = if (state.selectedImageId == imageId) null else imageId
        setFocus(null)
    }

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
        }
    }

    fun resizeImage(blockId: String) {
        performUndoableAction {
            val block = _uiState.value.content.find { it.id == blockId } as? ImageBlock
            if (block != null) {
                block.isResized = !block.isResized
            }
        }
    }

    fun updateImageDescription(blockId: String, description: String) {
        (_uiState.value.content.find { it.id == blockId } as? ImageBlock)?.description = description
    }

    fun setFocus(blockId: String?) {
        val state = _uiState.value
        if (state.focusedBlockId != blockId) {
            state.focusedBlockId = blockId
            state.selectedImageId = null
            _pendingStyles.value = emptySet()
        }
    }

    fun toggleTextFormatToolbar(isVisible: Boolean) {
        _uiState.value.isTextFormatToolbarVisible = isVisible
    }

    fun onAccordionToggled(blockId: String) {
        performUndoableAction {
            val block = _uiState.value.content.find { it.id == blockId } as? AccordionBlock
            if (block != null) {
                block.isExpanded = !block.isExpanded
            }
        }
    }

    fun onToggleSwitchChanged(blockId: String, isOn: Boolean) {
        performUndoableAction {
            (_uiState.value.content.find { it.id == blockId } as? ToggleSwitchBlock)?.isOn = isOn
        }
    }

    fun onRadioSelectionChanged(groupId: String, selectedItemId: String) {
        performUndoableAction {
            (_uiState.value.content.find { it.id == groupId } as? RadioGroupBlock)?.selectedId = selectedItemId
        }
    }

    fun saveNote() {
        viewModelScope.launch {
            commitActionForUndo()
            Log.d("NoteEditorDebug", "Note Saved: Title='${_uiState.value.title}', Content size=${_uiState.value.content.size}")
        }
    }

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

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        releaseRecorder()
        releasePlayer()
        recordingJob?.cancel()
    }
}
