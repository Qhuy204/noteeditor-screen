package com.example.noteeditor

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.example.noteeditor.composables.Style // Import Style enum
import java.text.SimpleDateFormat
import java.util.*

// --- STATE CLASSES ---

@Stable
sealed class ContentBlock(val id: String = UUID.randomUUID().toString()) {
    abstract fun deepCopy(): ContentBlock
}

@Stable
class TextBlock(
    initialValue: TextFieldValue = TextFieldValue(AnnotatedString("")),
    initialParagraphStyle: ParagraphStyle = ParagraphStyle(textAlign = TextAlign.Start),
    initialIsListItem: Boolean = false
) : ContentBlock() {
    var value by mutableStateOf(initialValue)
    var paragraphStyle by mutableStateOf(initialParagraphStyle)
    var isListItem by mutableStateOf(initialIsListItem)

    override fun deepCopy(): ContentBlock {
        return TextBlock(value, paragraphStyle, isListItem)
    }
}

@Stable
class ImageBlock(val uri: Uri, initialDescription: String = "", initialIsResized: Boolean = false) : ContentBlock() {
    var description by mutableStateOf(initialDescription)
    var isResized by mutableStateOf(initialIsResized)
    override fun deepCopy(): ContentBlock = ImageBlock(uri, description, isResized)
}

@Stable
class CheckboxBlock(initialValue: TextFieldValue = TextFieldValue(""), initialIsChecked: Boolean = false) : ContentBlock() {
    var value by mutableStateOf(initialValue)
    var isChecked by mutableStateOf(initialIsChecked)
    override fun deepCopy(): ContentBlock = CheckboxBlock(value, isChecked)
}

@Stable
class AudioBlock(
    val uri: Uri? = null,
    initialDuration: String = "00:00",
    initialIsPlaying: Boolean = false,
    initialIsRecording: Boolean = false,
    initialFilePath: String? = null,
    initialRecordingTimeMillis: Long = 0L,
    initialAmplitudes: List<Int> = emptyList() // [MỚI] Thêm tham số cho biên độ
) : ContentBlock() {
    var duration by mutableStateOf(initialDuration)
    var isPlaying by mutableStateOf(initialIsPlaying)
    var isRecording by mutableStateOf(initialIsRecording)
    var filePath by mutableStateOf(initialFilePath)
    var recordingTimeMillis by mutableStateOf(initialRecordingTimeMillis)
    // [MỚI] Danh sách lưu trữ biên độ sóng âm
    val amplitudes: SnapshotStateList<Int> = initialAmplitudes.toMutableStateList()

    override fun deepCopy(): ContentBlock {
        // [CẬP NHẬT] Sao chép cả danh sách biên độ
        return AudioBlock(uri, duration, isPlaying, isRecording, filePath, recordingTimeMillis, amplitudes)
    }
}

@Stable
class RadioGroupBlock(val items: List<RadioButtonItem> = listOf(RadioButtonItem("Option 1"), RadioButtonItem("Option 2")), initialSelectedId: String? = items.firstOrNull()?.id) : ContentBlock() {
    var selectedId by mutableStateOf(initialSelectedId)
    override fun deepCopy(): ContentBlock = RadioGroupBlock(items.map { it.copy() }, selectedId)
}

data class RadioButtonItem(val label: String, val id: String = UUID.randomUUID().toString())

@Stable
class ToggleSwitchBlock(val text: String = "Toggle Switch", initialIsOn: Boolean = false) : ContentBlock() {
    var isOn by mutableStateOf(initialIsOn)
    override fun deepCopy(): ContentBlock = ToggleSwitchBlock(text, isOn)
}

@Stable
class AccordionBlock(val title: String = "Accordion Title", val content: String = "Collapsible content.", initialIsExpanded: Boolean = false) : ContentBlock() {
    var isExpanded by mutableStateOf(initialIsExpanded)
    override fun deepCopy(): ContentBlock = AccordionBlock(title, content, isExpanded)
}

@Stable
class SeparatorBlock : ContentBlock() {
    override fun deepCopy(): ContentBlock = SeparatorBlock()
}


@Stable
class NoteState {
    var title by mutableStateOf("")
    val content: SnapshotStateList<ContentBlock> = mutableStateListOf(TextBlock())
    var selectedImageId by mutableStateOf<String?>(null)
    var focusedBlockId by mutableStateOf<String?>(null)
    var isTextFormatToolbarVisible by mutableStateOf(false)
    var activeStyles by mutableStateOf<Set<Style>>(emptySet())
    val date: String = SimpleDateFormat("EEEE, MMMM d,yyyy", Locale.getDefault()).format(Date())
    var category by mutableStateOf("Chưa phân loại")
    var isRecordingActive by mutableStateOf(false)
    var currentRecordingAudioBlock: AudioBlock? by mutableStateOf(null)

    fun deepCopy(): NoteState {
        val new = NoteState()
        new.title = this.title
        val copiedContent = this.content.map { it.deepCopy() }
        new.content.clear()
        new.content.addAll(copiedContent)
        new.selectedImageId = this.selectedImageId
        new.focusedBlockId = this.focusedBlockId
        new.isTextFormatToolbarVisible = this.isTextFormatToolbarVisible
        new.category = this.category
        new.activeStyles = this.activeStyles
        new.isRecordingActive = this.isRecordingActive
        // deepCopy của AudioBlock sẽ tự động sao chép amplitudes
        new.currentRecordingAudioBlock = this.currentRecordingAudioBlock?.deepCopy() as? AudioBlock
        return new
    }
}
