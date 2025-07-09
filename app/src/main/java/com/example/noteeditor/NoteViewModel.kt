package com.example.noteeditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
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
import com.mohamedrejeb.richeditor.model.RichTextState
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
import kotlin.math.abs
import kotlin.math.log10

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
    private var audioRecord: AudioRecord? = null // [NEW] AudioRecord instance
    private var currentRecordingFilePath: String? = null
    private var currentPlayingAudioBlockId: String? = null
    private var recordingJob: Job? = null
    private var waveformJob: Job? = null // [NEW] Job for waveform collection

    // Limit the number of undo/redo states
    private val MAX_STACK_SIZE = 50

    // [NEW] Map to store RichTextState for each TextBlock
    private val richTextStates: MutableMap<String, RichTextState> = mutableMapOf()

    // [NEW] StateFlow to track RichTextState of the currently focused block (for Toolbar)
    private val _activeRichTextState = MutableStateFlow(RichTextState())
    val activeRichTextState: StateFlow<RichTextState> = _activeRichTextState.asStateFlow()

    init {
        // Push initial state to undo stack
        val initialState = _uiState.value.deepCopy()
        // Initialize RichTextState for initial TextBlocks
        initialState.content.filterIsInstance<TextBlock>().forEach { textBlock ->
            richTextStates[textBlock.id] = RichTextState().apply { setHtml(textBlock.htmlContent) }
        }
        undoStack.add(initialState)
        updateUndoRedoButtons()
    }

    // Update the state of Undo/Redo buttons
    private fun updateUndoRedoButtons() {
        _canUndo.value = undoStack.size > 1
        _canRedo.value = redoStack.size > 0
        Log.d("UndoRedoButtons", "canUndo: ${_canUndo.value}, canRedo: ${_canRedo.value}")
    }

    // Add state to undo stack, limit size
    private fun addToUndoStack(state: NoteState) {
        undoStack.add(state)
        if (undoStack.size > MAX_STACK_SIZE) {
            undoStack.removeAt(0) // Remove the oldest element
        }
        Log.d("UndoRedoStack", "Added to undoStack. Size: ${undoStack.size}")
    }

    // Add state to redo stack, limit size
    private fun addToRedoStack(state: NoteState) {
        redoStack.add(state)
        if (redoStack.size > MAX_STACK_SIZE) {
            redoStack.removeAt(0) // Remove the oldest element
        }
        Log.d("UndoRedoStack", "Added to redoStack. Size: ${redoStack.size}")
    }

    // Save current state to undo stack if there's a significant difference
    private fun saveStateForUndoInternal(stateToSave: NoteState) {
        val lastSavedState = undoStack.lastOrNull()

        // Only add to stack if current state is significantly different from the last saved state
        if (lastSavedState == null) {
            Log.d("UndoRedo", "Saving initial state (lastSavedState is null).")
            addToUndoStack(stateToSave)
            redoStack.clear() // Clear redo stack when a new operation occurs
        } else if (!lastSavedState.isContentEqual(stateToSave)) {
            Log.d("UndoRedo", "Content diff detected. Saving new state.")
            addToUndoStack(stateToSave)
            redoStack.clear() // Clear redo stack when a new operation occurs (not from undo/redo)
        } else {
            Log.d("UndoRedo", "State is content equal, skipping save.")
        }
        updateUndoRedoButtons()
    }

    // This function is called when a block loses focus or an action is completed
    fun commitActionForUndo() {
        val currentState = _uiState.value.deepCopy()
        val lastSavedState = undoStack.lastOrNull()
        if (lastSavedState == null || !lastSavedState.isContentEqual(currentState)) {
            saveStateForUndoInternal(currentState)
        } else {
            Log.d("UndoRedo", "Commit action: State is equal, no save needed.")
        }
    }

    // Perform Undo operation
    fun undo() {
        // Only undo if there are at least 2 states (current state and previous state)
        if (undoStack.size > 1) {
            // Save current state to redo stack before undoing
            val currentState = _uiState.value.deepCopy()
            addToRedoStack(currentState)

            // Remove current state from undo stack
            undoStack.removeAt(undoStack.lastIndex)

            // Update UI to previous state
            val restoredState = undoStack.last().deepCopy()
            _uiState.value = restoredState

            // [NEW] Update RichTextState in map based on restored state
            updateRichTextStatesFromNoteState(restoredState)

            updateUndoRedoButtons()
            Log.d("UndoRedo", "Undo performed. UndoStack size: ${undoStack.size}, RedoStack size: ${redoStack.size}")
        } else {
            Log.d("UndoRedo", "Cannot undo. UndoStack size: ${undoStack.size}")
        }
    }

    // Perform Redo operation
    fun redo() {
        if (redoStack.isNotEmpty()) {
            // Save current state to undo stack before redoing
            val currentState = _uiState.value.deepCopy()
            addToUndoStack(currentState)

            // Get the next state from redo stack
            val nextState = redoStack.removeAt(redoStack.lastIndex)
            _uiState.value = nextState

            // [NEW] Update RichTextState in map based on restored state
            updateRichTextStatesFromNoteState(nextState)

            updateUndoRedoButtons()
            Log.d("UndoRedo", "Redo performed. UndoStack size: ${undoStack.size}, RedoStack size: ${redoStack.size}")
        } else {
            Log.d("UndoRedo", "Cannot redo. RedoStack is empty.")
        }
    }

    // [NEW] Function to update RichTextState in map when NoteState changes (undo/redo)
    private fun updateRichTextStatesFromNoteState(noteState: NoteState) {
        val newRichTextStates = mutableMapOf<String, RichTextState>()
        noteState.content.filterIsInstance<TextBlock>().forEach { textBlock ->
            val existingState = richTextStates[textBlock.id]
            if (existingState != null) {
                // If RichTextState already exists, update its content
                existingState.setHtml(textBlock.htmlContent)
                newRichTextStates[textBlock.id] = existingState
            } else {
                // If it doesn't exist, create a new one
                newRichTextStates[textBlock.id] = RichTextState().apply { setHtml(textBlock.htmlContent) }
            }
        }
        // Remove unnecessary RichTextStates
        richTextStates.clear()
        richTextStates.putAll(newRichTextStates)

        // Update activeRichTextState if the focused block is a TextBlock
        _uiState.value.focusedBlockId?.let { focusedId ->
            if (richTextStates.containsKey(focusedId)) {
                _activeRichTextState.value = richTextStates[focusedId]!!
            } else {
                _activeRichTextState.value = RichTextState() // Reset if focused block is no longer a TextBlock
            }
        } ?: run { _activeRichTextState.value = RichTextState() } // Reset if no block is focused
    }

    // Helper function to perform an undoable action
    private fun performUndoableAction(action: () -> Unit) {
        // Save state *before* the action is performed
        saveStateForUndoInternal(_uiState.value.deepCopy())
        action()
        // After the action is complete, update button states
        updateUndoRedoButtons()
    }

    // Ensure onTitleChange also calls saveStateForUndoInternal
    fun onTitleChange(newTitle: String) {
        // Only save state if title actually changes
        if (_uiState.value.title != newTitle) {
            saveStateForUndoInternal(_uiState.value.deepCopy()) // Save before changing
            _uiState.value.title = newTitle
            updateUndoRedoButtons()
        }
    }

    // [MODIFIED] Handle TextBlock content changes (receives HTML string)
    fun onTextBlockChange(blockId: String, newHtmlContent: String) {
        val block = _uiState.value.content.find { it.id == blockId } as? TextBlock
        if (block != null) {
            if (block.htmlContent != newHtmlContent) {
                // Only save state if HTML content actually changes
                saveStateForUndoInternal(_uiState.value.deepCopy())
                block.htmlContent = newHtmlContent
            }
        }
        updateUndoRedoButtons() // Update button states after each content change
    }

    // Handle content changes for other blocks (using TextFieldValue)
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
        updateUndoRedoButtons() // Update button states after each content change
    }

    // FIX: Text formatting functions have been updated to use the new API
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

//    fun indent() {
//        // FIX: Replace 'indent' with 'increaseIndent'
//        _activeRichTextState.value.increaseIndent()
//        Log.d("Formatting", "Indented text.")
//    }
//
//    fun outdent() {
//        // FIX: Replace 'outdent' with 'decreaseIndent'
//        _activeRichTextState.value.decreaseIndent()
//        Log.d("Formatting", "Outdented text.")
//    }

    // Handle Checkbox state changes
    fun onCheckboxCheckedChange(blockId: String, isChecked: Boolean) {
        performUndoableAction {
            (_uiState.value.content.find { it.id == blockId } as? CheckboxBlock)?.isChecked = isChecked
        }
    }

    // Add a new block at the cursor position
    private fun addBlockAtCursor(newBlock: ContentBlock) {
        performUndoableAction {
            val state = _uiState.value
            val focusedIndex = state.content.indexOfFirst { it.id == state.focusedBlockId }
            val insertionPoint = if (focusedIndex != -1) focusedIndex + 1 else state.content.size

            state.content.add(insertionPoint, newBlock)

            // [NEW] If it's a TextBlock, add RichTextState to map
            if (newBlock is TextBlock) {
                richTextStates[newBlock.id] = RichTextState().apply { setHtml(newBlock.htmlContent) }
            }

            // Ensure there's an empty TextBlock after insertion if the new block is not text
            if (newBlock !is TextBlock && newBlock !is SubHeaderBlock && newBlock !is NumberedListItemBlock && newBlock !is SeparatorBlock && newBlock !is AudioBlock && newBlock !is ImageBlock) {
                val newTextBlock = TextBlock()
                state.content.add(insertionPoint + 1, newTextBlock)
                richTextStates[newTextBlock.id] = RichTextState().apply { setHtml(newTextBlock.htmlContent) } // Add to map
                state.focusedBlockId = newTextBlock.id
            } else if (newBlock is TextBlock || newBlock is SubHeaderBlock || newBlock is NumberedListItemBlock || newBlock is AudioBlock || newBlock is ImageBlock) {
                state.focusedBlockId = newBlock.id
            } else {
                // If it's a SeparatorBlock, set focus back to the previous block if any
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

    // Add image block at cursor position
    fun addImageAtCursor(uri: Uri) = addBlockAtCursor(ImageBlock(uri = uri))

    // [UPDATE] Start new audio recording
    fun startNewAudioRecording(context: Context) {
        if (_uiState.value.isRecordingActive) {
            return // Ignore if already recording
        }

        val outputFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.mp3")
        currentRecordingFilePath = outputFile.absolutePath

        // Create a temporary audio block, not yet added to main content
        val tempAudioBlock = AudioBlock(
            uri = Uri.fromFile(outputFile),
            initialIsRecording = true,
            initialFilePath = outputFile.absolutePath
        )

        // Update state to display recording UI
        _uiState.update { currentState ->
            currentState.deepCopy().apply {
                this.currentRecordingAudioBlock = tempAudioBlock
                this.isRecordingActive = true
                this.isTextFormatToolbarVisible = false
            }
        }
        startRecordingInternal(currentRecordingFilePath!!, context)
    }

    // Start internal recording
    private fun startRecordingInternal(filePath: String, context: Context) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder = recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            // FIX: Use constants from MediaRecorder class
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
                        // Update time on temporary block
                        _uiState.value.currentRecordingAudioBlock?.recordingTimeMillis = elapsedMillis
                        _uiState.value.currentRecordingAudioBlock?.duration = formatDuration(elapsedMillis)
                        delay(1000)
                    }
                }
                // [NEW] Start collecting waveform data
                startWaveformCollection()
                Log.d("AudioRecording", "Recording started: $filePath")
            } catch (e: IOException) {
                Log.e("AudioRecording", "Recording failed: ${e.message}")
                cancelRecording() // Cancel if unable to start
            }
        }
    }

    // [NEW] Start collecting waveform data
    private fun startWaveformCollection() {
        val sampleRate = 44100 // Common sample rate
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("Waveform", "AudioRecord.getMinBufferSize failed.")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("Waveform", "AudioRecord initialization failed.")
            releaseAudioRecord()
            return
        }

        audioRecord?.startRecording()

        val audioBuffer = ShortArray(bufferSize / 2) // Using ShortArray for 16-bit PCM

        waveformJob = viewModelScope.launch {
            while (_uiState.value.isRecordingActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (read > 0) {
                    // Calculate amplitude
                    var maxAmplitude = 0
                    for (i in 0 until read) {
                        maxAmplitude = maxOf(maxAmplitude, abs(audioBuffer[i].toInt()))
                    }

                    // Normalize amplitude to a range (e.g., 0-100 or 0-50 for visual representation)
                    // A simple normalization: log scale for better visual distinction of quiet sounds
                    val normalizedAmplitude = if (maxAmplitude > 0) {
                        (20 * log10(maxAmplitude.toDouble() / 32767.0) + 90).toInt().coerceIn(0, 100) / 2 // Normalize to 0-50
                    } else {
                        0
                    }

                    // Update the AudioBlock's amplitudes list
                    _uiState.value.currentRecordingAudioBlock?.amplitudes?.add(normalizedAmplitude)
                    // Limit the number of amplitudes to keep the waveform visually manageable
                    // For example, keep the last 100 amplitudes
                    if (_uiState.value.currentRecordingAudioBlock?.amplitudes?.size ?: 0 > 100) {
                        _uiState.value.currentRecordingAudioBlock?.amplitudes?.removeAt(0)
                    }
                }
                delay(50) // Adjust delay to control waveform update frequency
            }
            releaseAudioRecord()
        }
    }

    // [UPDATE] Save recorded audio
    fun saveRecordedAudio() {
        // Do not save recordings that are too short (e.g., less than 1 second)
        val recordedMillis = _uiState.value.currentRecordingAudioBlock?.recordingTimeMillis ?: 0L
        if (recordedMillis < 1000) {
            cancelRecording()
            return
        }

        // Stop recording process
        recordingJob?.cancel()
        recordingJob = null
        waveformJob?.cancel() // [NEW] Cancel waveform job
        waveformJob = null

        val blockToSave = _uiState.value.currentRecordingAudioBlock
        val path = currentRecordingFilePath

        try {
            mediaRecorder?.stop()
            Log.d("AudioRecording", "Recording stopped for saving: $path")
        } catch (e: Exception) {
            Log.e("AudioRecording", "Error stopping recorder, cancelling.", e)
            path?.let { File(it).delete() } // Delete temporary file if stopping fails
            resetRecordingState() // Reset state
            return
        } finally {
            releaseRecorder()
            releaseAudioRecord() // [NEW] Release AudioRecord
        }

        // Finalize audio block and add to note
        if (blockToSave != null && path != null) {
            val finalDuration = getFileDuration(path)
            blockToSave.duration = finalDuration
            blockToSave.isRecording = false

            // Add the finalized block to content (this action is undoable)
            addBlockAtCursor(blockToSave)
        }

        // Reset recording state
        resetRecordingState()
    }

    // [UPDATE] Cancel recording
    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        waveformJob?.cancel() // [NEW] Cancel waveform job
        waveformJob = null

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w("AudioRecording", "Exception on stopping recorder during cancellation: ${e.message}")
        }
        releaseRecorder()
        releaseAudioRecord() // [NEW] Release AudioRecord

        // Delete temporary audio file
        currentRecordingFilePath?.let { File(it).delete() }

        // Reset state without adding anything to the note
        resetRecordingState()
        Log.d("AudioRecording", "Recording cancelled.")
    }

    // Utility function to reset recording state
    private fun resetRecordingState() {
        _uiState.update {
            it.deepCopy().apply {
                isRecordingActive = false
                currentRecordingAudioBlock = null
            }
        }
        currentRecordingFilePath = null
    }

    // Get audio file duration
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

    // Check if any audio is recording
    fun isAnyAudioRecording(): Boolean {
        return _uiState.value.isRecordingActive
    }

    // Add Checkbox block
    fun addCheckbox() = addBlockAtCursor(CheckboxBlock())
    // Add Separator block
    fun addSeparator() = addBlockAtCursor(SeparatorBlock())
    // Add Toggle Switch block
    fun addToggleSwitch() = addBlockAtCursor(ToggleSwitchBlock())
    // Add Accordion block
    fun addAccordion() = addBlockAtCursor(AccordionBlock())
    // Add Radio Group block
    fun addRadioGroup() = addBlockAtCursor(RadioGroupBlock())
    // Add SubHeader block
    fun addSectionHeader() = addBlockAtCursor(SubHeaderBlock())
    // Add Numbered List Item block
    fun addNumberedListItem() = addBlockAtCursor(NumberedListItemBlock())

    // Handle image click
    fun onImageClick(imageId: String) {
        val state = _uiState.value
        state.selectedImageId = if (state.selectedImageId == imageId) null else imageId
        // When selecting or deselecting an image, ensure no text block is focused
        setFocus(null)
    }

    // Delete a block
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
            } else if (blockToDelete is TextBlock) { // [NEW] Remove RichTextState from map when TextBlock is deleted
                richTextStates.remove(blockId)
            }
            _uiState.value.content.removeAll { it.id == blockId }
            if (_uiState.value.selectedImageId == blockId) {
                _uiState.value.selectedImageId = null
            }
            // If the deleted block is the drawing block, reset drawing state
            if (_uiState.value.drawingImageId == blockId) {
                _uiState.value.drawingImageId = null
            }
            // If the deleted block is the focused block, reset focus
            if (_uiState.value.focusedBlockId == blockId) {
                setFocus(null)
            }
        }
    }

    // Resize image
    fun resizeImage(blockId: String) {
        performUndoableAction {
            val block = _uiState.value.content.find { it.id == blockId } as? ImageBlock
            if (block != null) {
                block.isResized = !block.isResized
            }
        }
    }

    // Update image description
    fun updateImageDescription(blockId: String, description: String) {
        // No need for performUndoableAction here if it's just a description change that doesn't need to be saved to undo stack every time it's typed
        // If you want to save, you need to add saveStateForUndoInternal logic similar to TextBlock
        (_uiState.value.content.find { it.id == blockId } as? ImageBlock)?.description = description
    }

    // Toggle drawing mode on image
    fun toggleDrawingMode(imageId: String?) {
        _uiState.update { currentState ->
            currentState.deepCopy().apply {
                drawingImageId = if (drawingImageId == imageId) null else imageId
                // Ensure no image is selected when drawing
                if (drawingImageId != null) {
                    selectedImageId = null
                }
            }
        }
    }

    // Copy image
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

    // Open image in gallery (placeholder logic)
    fun openImageInGallery(blockId: String) {
        Log.d("NoteViewModel", "Opening image with ID: $blockId in gallery (Placeholder)")
    }

    // Set focus to a block
    fun setFocus(blockId: String?) {
        val state = _uiState.value
        if (state.focusedBlockId != blockId) {
            // When focus changes, commit previous action to save state
            if (state.focusedBlockId != null) {
                commitActionForUndo()
            }
            state.focusedBlockId = blockId
            // When setting focus to a block, ensure no image is selected or drawing
            state.selectedImageId = null
            state.drawingImageId = null

            // [NEW] Update activeRichTextState when focus changes
            if (blockId != null) {
                val focusedBlock = state.content.find { it.id == blockId }
                if (focusedBlock is TextBlock) {
                    // Get RichTextState from map or create new if not present (though shouldn't happen)
                    _activeRichTextState.value = richTextStates.getOrPut(focusedBlock.id) {
                        RichTextState().apply { setHtml(focusedBlock.htmlContent) }
                    }
                } else {
                    _activeRichTextState.value = RichTextState() // Reset if focused block is not TextBlock
                }
            } else {
                _activeRichTextState.value = RichTextState() // Reset if no block is focused
            }
        }
        updateUndoRedoButtons() // Update button states when focus changes
    }

    // Toggle text format toolbar visibility
    fun toggleTextFormatToolbar(isVisible: Boolean) {
        _uiState.value.isTextFormatToolbarVisible = isVisible
    }

    // Handle Accordion toggle
    fun onAccordionToggled(blockId: String) {
        performUndoableAction {
            val block = _uiState.value.content.find { it.id == blockId } as? AccordionBlock
            if (block != null) {
                block.isExpanded = !block.isExpanded
            }
        }
    }

    // Handle Toggle Switch state change
    fun onToggleSwitchChanged(blockId: String, isOn: Boolean) {
        performUndoableAction {
            (_uiState.value.content.find { it.id == blockId } as? ToggleSwitchBlock)?.isOn = isOn
        }
    }

    // Handle Radio Group selection change
    fun onRadioSelectionChanged(groupId: String, selectedItemId: String) {
        performUndoableAction {
            (_uiState.value.content.find { it.id == groupId } as? RadioGroupBlock)?.selectedId = selectedItemId
        }
    }

    // Save note
    fun saveNote() {
        viewModelScope.launch {
            commitActionForUndo() // Ensure final state is saved
            Log.d("NoteEditorDebug", "Note Saved: Title='${_uiState.value.title}', Content size=${_uiState.value.content.size}")
        }
    }

    // Toggle audio playback
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

    // Stop audio playback
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

    // Release MediaPlayer
    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // Release MediaRecorder
    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    // [NEW] Release AudioRecord
    private fun releaseAudioRecord() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    // Format duration from milliseconds to MM:SS
    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    // Handle ViewModel being cleared
    override fun onCleared() {
        super.onCleared()
        releaseRecorder()
        releasePlayer()
        releaseAudioRecord() // [NEW] Release AudioRecord on clear
        recordingJob?.cancel()
        waveformJob?.cancel() // [NEW] Cancel waveform job on clear
        richTextStates.clear() // Clear all RichTextState when ViewModel is cleared
    }

    // Function to move a ContentBlock
    fun moveContentBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex < 0 || fromIndex >= _uiState.value.content.size ||
            toIndex < 0 || toIndex > _uiState.value.content.size) {
            return
        }
        performUndoableAction {
            val contentList = _uiState.value.content
            val movedBlock = contentList.removeAt(fromIndex)
            contentList.add(toIndex, movedBlock)

            // [NEW] When moving, ensure RichTextState of TextBlock is still correctly linked
            if (movedBlock is TextBlock) {
                // No special action needed here as RichTextState is managed by ID
                // and block ID does not change when moving.
            }

            // Ensure there's an empty TextBlock after moving if needed
            if (movedBlock !is TextBlock && toIndex + 1 < contentList.size && contentList[toIndex + 1] !is TextBlock) {
                val newTextBlock = TextBlock()
                contentList.add(toIndex + 1, newTextBlock)
                richTextStates[newTextBlock.id] = RichTextState().apply { setHtml(newTextBlock.htmlContent) } // Add to map
            } else if (movedBlock !is TextBlock && toIndex == contentList.lastIndex) {
                val newTextBlock = TextBlock()
                contentList.add(newTextBlock)
                richTextStates[newTextBlock.id] = RichTextState().apply { setHtml(newTextBlock.htmlContent) } // Add to map
            }
            Log.d("NoteEditorDebug", "Moved block from $fromIndex to $toIndex")
        }
    }

    // Helper functions for drag and drop
    fun setDraggingBlockId(id: String?) {
        _uiState.update { it.deepCopy().apply { draggingBlockId = id } }
    }

    fun setDropTargetIndex(index: Int?) {
        _uiState.update { it.deepCopy().apply { dropTargetIndex = index } }
    }

    // Utility function to debug undo/redo stacks
    fun getUndoRedoStackInfo(): String {
        return "UndoStack: ${undoStack.size}, RedoStack: ${redoStack.size}"
    }

    // [NEW] Function to get RichTextState for a specific TextBlock
    fun getOrCreateRichTextState(blockId: String, initialHtml: String): RichTextState {
        return richTextStates.getOrPut(blockId) {
            RichTextState().apply { setHtml(initialHtml) }
        }
    }
}