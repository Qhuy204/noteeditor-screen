package com.example.noteeditor

import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noteeditor.composables.Style
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

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

    // [MỚI] State để lưu các style sẽ được áp dụng cho văn bản tiếp theo
    private val _pendingStyles = MutableStateFlow<Set<Style>>(emptySet())
    val pendingStyles: StateFlow<Set<Style>> = _pendingStyles.asStateFlow()

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

    /**
     * [THAY ĐỔI] Xử lý việc áp dụng các style đang chờ (pending styles) khi người dùng gõ.
     */
    fun onContentBlockChange(blockId: String, newValue: TextFieldValue) {
        val block = _uiState.value.content.find { it.id == blockId }
        when (block) {
            is TextBlock -> {
                val oldValue = block.value

                // Nếu văn bản không đổi, người dùng chỉ thay đổi vị trí con trỏ.
                // Ta sẽ xóa các style đang chờ để tránh áp dụng chúng ở vị trí mới.
                if (oldValue.text == newValue.text) {
                    block.value = oldValue.copy(
                        selection = newValue.selection,
                        composition = newValue.composition
                    )
                    // [MỚI] Xóa pending styles khi di chuyển con trỏ
                    if (_pendingStyles.value.isNotEmpty()) {
                        _pendingStyles.value = emptySet()
                    }
                    return
                }

                // Nếu văn bản đã thay đổi (gõ, dán, xóa, etc.).
                val builder = AnnotatedString.Builder(newValue.annotatedString)

                // Mang tất cả các style đã có từ trước sang.
                oldValue.annotatedString.spanStyles.forEach {
                    builder.addStyle(it.item, it.start, it.end)
                }

                // [MỚI] Nếu người dùng đang gõ thêm văn bản và có style đang chờ, áp dụng chúng.
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

    /**
     * [MỚI] Helper function để tạo một SpanStyle duy nhất từ một Set các Style enums.
     */
    private fun createCombinedSpanStyle(styles: Set<Style>): SpanStyle {
        var combined = SpanStyle()
        if (styles.contains(Style.BOLD)) combined = combined.merge(SpanStyle(fontWeight = FontWeight.Bold))
        if (styles.contains(Style.ITALIC)) combined = combined.merge(SpanStyle(fontStyle = FontStyle.Italic))
        if (styles.contains(Style.UNDERLINE)) combined = combined.merge(SpanStyle(textDecoration = TextDecoration.Underline))
        if (styles.contains(Style.STRIKETHROUGH)) combined = combined.merge(SpanStyle(textDecoration = TextDecoration.LineThrough))
        return combined
    }


    /**
     * [THAY ĐỔI] Áp dụng hoặc bật/tắt một style.
     * - Nếu có text được chọn, áp dụng style cho vùng chọn đó.
     * - Nếu không có text được chọn, bật/tắt style đó trong danh sách pending.
     */
    fun toggleStyle(style: Style) {
        val focusedId = _uiState.value.focusedBlockId ?: return
        val block = _uiState.value.content.find { it.id == focusedId } as? TextBlock ?: return
        val selection = block.value.selection

        // Trường hợp 1: Có văn bản được chọn -> áp dụng style như cũ
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
        }
        // Trường hợp 2: Không có văn bản được chọn -> Bật/tắt pending style
        else {
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
                // [MỚI] Nếu không có vùng chọn, không làm gì cả
                // Chức năng pending style được xử lý bởi toggleStyle()
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
            if (newBlock !is SeparatorBlock) {
                val nextTextBlock = TextBlock()
                state.content.add(insertionPoint + 1, nextTextBlock)
                state.focusedBlockId = nextTextBlock.id
            }
            state.selectedImageId = null
        }
    }

    fun addImageAtCursor(uri: Uri) = addBlockAtCursor(ImageBlock(uri = uri))
    fun addCheckbox() = addBlockAtCursor(CheckboxBlock())
    fun addSeparator() = addBlockAtCursor(SeparatorBlock())
    fun addAudioBlock() = addBlockAtCursor(AudioBlock())
    fun addToggleSwitch() = addBlockAtCursor(ToggleSwitchBlock())
    fun addAccordion() = addBlockAtCursor(AccordionBlock())
    fun addRadioGroup() = addBlockAtCursor(RadioGroupBlock())

    fun onImageClick(imageId: String) {
        val state = _uiState.value
        state.selectedImageId = if (state.selectedImageId == imageId) null else imageId
        setFocus(null) // Bỏ focus khỏi text block
    }

    fun deleteBlock(blockId: String) {
        performUndoableAction {
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
            // [MỚI] Khi focus thay đổi, xóa các style đang chờ.
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
}