package com.example.noteeditor

import android.net.Uri
import android.util.Log
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
// Cập nhật ContentBlock để có thể truyền id vào constructor
sealed class ContentBlock(val id: String = UUID.randomUUID().toString()) {
    abstract fun deepCopy(): ContentBlock
}

@Stable
class TextBlock(
    initialValue: TextFieldValue = TextFieldValue(AnnotatedString("")),
    initialParagraphStyle: ParagraphStyle = ParagraphStyle(textAlign = TextAlign.Start),
    initialIsListItem: Boolean = false,
    id: String = UUID.randomUUID().toString() // Thêm id vào constructor và truyền lên lớp cha
) : ContentBlock(id) {
    var value by mutableStateOf(initialValue)
    var paragraphStyle by mutableStateOf(initialParagraphStyle)
    var isListItem by mutableStateOf(initialIsListItem)

    override fun deepCopy(): ContentBlock {
        // Truyền id của khối hiện tại vào bản sao sâu
        return TextBlock(value, paragraphStyle, isListItem, id)
    }
}

@Stable
class ImageBlock(val uri: Uri, initialDescription: String = "", initialIsResized: Boolean = false, id: String = UUID.randomUUID().toString()) : ContentBlock(id) {
    var description by mutableStateOf(initialDescription)
    var isResized by mutableStateOf(initialIsResized)
    override fun deepCopy(): ContentBlock = ImageBlock(uri, description, isResized, id)
}

@Stable
class CheckboxBlock(initialValue: TextFieldValue = TextFieldValue(""), initialIsChecked: Boolean = false, id: String = UUID.randomUUID().toString()) : ContentBlock(id) {
    var value by mutableStateOf(initialValue)
    var isChecked by mutableStateOf(initialIsChecked)
    override fun deepCopy(): ContentBlock = CheckboxBlock(value, isChecked, id)
}

@Stable
class AudioBlock(
    val uri: Uri? = null,
    initialDuration: String = "00:00",
    initialIsPlaying: Boolean = false,
    initialIsRecording: Boolean = false,
    initialFilePath: String? = null,
    initialRecordingTimeMillis: Long = 0L,
    initialAmplitudes: List<Int> = emptyList(), // [MỚI] Thêm tham số cho biên độ
    id: String = UUID.randomUUID().toString()
) : ContentBlock(id) {
    var duration by mutableStateOf(initialDuration)
    var isPlaying by mutableStateOf(initialIsPlaying)
    var isRecording by mutableStateOf(initialIsRecording)
    var filePath by mutableStateOf(initialFilePath)
    var recordingTimeMillis by mutableStateOf(initialRecordingTimeMillis)
    // [MỚI] Danh sách lưu trữ biên độ sóng âm
    val amplitudes: SnapshotStateList<Int> = initialAmplitudes.toMutableStateList()

    override fun deepCopy(): ContentBlock {
        // [CẬP NHẬT] Sao chép cả danh sách biên độ và truyền id
        return AudioBlock(uri, duration, isPlaying, isRecording, filePath, recordingTimeMillis, amplitudes.toMutableStateList(), id)
    }
}

@Stable
class RadioGroupBlock(val items: List<RadioButtonItem> = listOf(RadioButtonItem("Option 1"), RadioButtonItem("Option 2")), initialSelectedId: String? = items.firstOrNull()?.id, id: String = UUID.randomUUID().toString()) : ContentBlock(id) {
    var selectedId by mutableStateOf(initialSelectedId)
    override fun deepCopy(): ContentBlock = RadioGroupBlock(items.map { it.copy() }, selectedId, id)
}

data class RadioButtonItem(val label: String, val id: String = UUID.randomUUID().toString())

@Stable
class ToggleSwitchBlock(val text: String = "Toggle Switch", initialIsOn: Boolean = false, id: String = UUID.randomUUID().toString()) : ContentBlock(id) {
    var isOn by mutableStateOf(initialIsOn)
    override fun deepCopy(): ContentBlock = ToggleSwitchBlock(text, isOn, id)
}

@Stable
class AccordionBlock(val title: String = "Accordion Title", val content: String = "Collapsible content.", initialIsExpanded: Boolean = false, id: String = UUID.randomUUID().toString()) : ContentBlock(id) {
    var isExpanded by mutableStateOf(initialIsExpanded)
    override fun deepCopy(): ContentBlock = AccordionBlock(title, content, isExpanded, id)
}

@Stable
class SeparatorBlock(id: String = UUID.randomUUID().toString()) : ContentBlock(id) {
    override fun deepCopy(): ContentBlock = SeparatorBlock(id)
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

    // [MỚI] Thêm trạng thái cho tính năng kéo thả
    var draggingBlockId by mutableStateOf<String?>(null)
    var dropTargetIndex by mutableStateOf<Int?>(null)


    fun deepCopy(): NoteState {
        val new = NoteState()
        new.title = this.title
        // Khi sao chép nội dung, đảm bảo deepCopy() của từng ContentBlock được gọi
        // để tạo bản sao mới của các thuộc tính có thể thay đổi, nhưng giữ nguyên ID.
        val copiedContent = this.content.map { it.deepCopy() }
        new.content.clear()
        new.content.addAll(copiedContent)
        new.selectedImageId = this.selectedImageId
        new.focusedBlockId = this.focusedBlockId
        // [ĐÃ SỬA] Không sao chép trạng thái toolbar để không ảnh hưởng đến undo/redo
        // new.isTextFormatToolbarVisible = this.isTextFormatToolbarVisible
        new.category = this.category
        // [ĐÃ SỬA] Không sao chép trạng thái activeStyles để không ảnh hưởng đến undo/redo
        // new.activeStyles = this.activeStyles
        new.isRecordingActive = this.isRecordingActive
        // deepCopy của AudioBlock sẽ tự động sao chép amplitudes
        new.currentRecordingAudioBlock = this.currentRecordingAudioBlock?.deepCopy() as? AudioBlock
        new.draggingBlockId = this.draggingBlockId // Sao chép trạng thái kéo
        new.dropTargetIndex = this.dropTargetIndex // Sao chép trạng thái vị trí thả
        return new
    }

    // [MỚI] Hàm để so sánh nội dung của hai NoteState
    fun isContentEqual(other: NoteState): Boolean {
        if (this.title != other.title) {
            Log.d("ContentEqual", "Title changed. This: '${this.title}', Other: '${other.title}'")
            return false
        }
        if (this.content.size != other.content.size) {
            Log.d("ContentEqual", "Content size changed. This: ${this.content.size}, Other: ${other.content.size}")
            return false
        }

        // So sánh sâu các khối nội dung
        for (i in this.content.indices) {
            val thisBlock = this.content[i]
            val otherBlock = other.content[i]

            // [FIX] ID của khối phải giống nhau để được coi là cùng một khối.
            // Nếu ID khác nhau, coi như khác biệt để tránh các trường hợp phức tạp
            if (thisBlock.id != otherBlock.id) {
                Log.d("ContentEqual", "Block ID changed at index $i. This: ${thisBlock.id}, Other: ${otherBlock.id}")
                return false
            }

            when (thisBlock) {
                is TextBlock -> {
                    if (otherBlock !is TextBlock) {
                        Log.d("ContentEqual", "TextBlock: Type mismatch at index $i.")
                        return false
                    }
                    if (thisBlock.value != otherBlock.value) {
                        Log.d("ContentEqual", "TextBlock: Value changed at index $i. This: '${thisBlock.value.text}', Other: '${otherBlock.value.text}'")
                        Log.d("ContentEqual", "This selection: ${thisBlock.value.selection}, Other selection: ${otherBlock.value.selection}")
                        Log.d("ContentEqual", "This spanStyles size: ${thisBlock.value.annotatedString.spanStyles.size}, Other spanStyles size: ${otherBlock.value.annotatedString.spanStyles.size}")
                        return false
                    }
                    if (thisBlock.paragraphStyle != otherBlock.paragraphStyle) {
                        Log.d("ContentEqual", "TextBlock: Paragraph style changed at index $i.")
                        return false
                    }
                    if (thisBlock.isListItem != otherBlock.isListItem) {
                        Log.d("ContentEqual", "TextBlock: List item status changed at index $i.")
                        return false
                    }
                }
                is ImageBlock -> {
                    if (otherBlock !is ImageBlock || thisBlock.uri != otherBlock.uri || thisBlock.description != otherBlock.description || thisBlock.isResized != otherBlock.isResized) {
                        Log.d("ContentEqual", "ImageBlock: Properties changed at index $i.")
                        return false
                    }
                }
                is CheckboxBlock -> {
                    if (otherBlock !is CheckboxBlock || thisBlock.value != otherBlock.value || thisBlock.isChecked != otherBlock.isChecked) {
                        Log.d("ContentEqual", "CheckboxBlock: Properties changed at index $i.")
                        return false
                    }
                }
                is AudioBlock -> {
                    // So sánh các thuộc tính quan trọng của AudioBlock
                    if (otherBlock !is AudioBlock || thisBlock.uri != otherBlock.uri || thisBlock.duration != otherBlock.duration || thisBlock.filePath != otherBlock.filePath || thisBlock.recordingTimeMillis != otherBlock.recordingTimeMillis || thisBlock.amplitudes != otherBlock.amplitudes) {
                        Log.d("ContentEqual", "AudioBlock: Properties changed at index $i.")
                        return false
                    }
                }
                is RadioGroupBlock -> {
                    if (otherBlock !is RadioGroupBlock || thisBlock.selectedId != otherBlock.selectedId || thisBlock.items != otherBlock.items) {
                        Log.d("ContentEqual", "RadioGroupBlock: Properties changed at index $i.")
                        return false
                    }
                }
                is ToggleSwitchBlock -> {
                    if (otherBlock !is ToggleSwitchBlock || thisBlock.text != otherBlock.text || thisBlock.isOn != otherBlock.isOn) {
                        Log.d("ContentEqual", "ToggleSwitchBlock: Properties changed at index $i.")
                        return false
                    }
                }
                is AccordionBlock -> {
                    if (otherBlock !is AccordionBlock || thisBlock.title != otherBlock.title || thisBlock.content != otherBlock.content || thisBlock.isExpanded != otherBlock.isExpanded) {
                        Log.d("ContentEqual", "AccordionBlock: Properties changed at index $i.")
                        return false
                    }
                }
                is SeparatorBlock -> {
                    if (otherBlock !is SeparatorBlock) {
                        Log.d("ContentEqual", "SeparatorBlock: Type mismatch at index $i.")
                        return false
                    }
                }
            }
        }
        Log.d("ContentEqual", "States are content equal.")
        return true
    }
}
