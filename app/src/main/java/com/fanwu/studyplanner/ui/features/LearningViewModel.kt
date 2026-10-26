package com.fanwu.studyplanner.ui.features

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream

class LearningViewModel(private val context: Context) : ViewModel() {
    val db = AppDatabase.getInstance(context)
    val repository = LearningRepository(db)
    val backup = BackupRepository(db)
    val documents = db.documentDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val events = db.calendarEventDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val intervals = db.focusDao().observeIntervals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessions = db.focusDao().observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val failure = MutableStateFlow(false)
    val restorePreview = MutableStateFlow<RestorePreview?>(null)
    val coursePreview = MutableStateFlow<CoursePreview?>(null)

    fun run(action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true; message.value = null; failure.value = false
            try { withContext(Dispatchers.IO) { action() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure.value = true; message.value = e.message ?: "操作失败，请重试" }
            finally { busy.value = false }
        }
    }

    fun export(uri: Uri) = run {
        val text = backup.export()
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入所选文件")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        message.value = "备份已导出；API Key 和系统密钥不在备份内"
    }

    fun readBackup(uri: Uri) = run {
        restorePreview.value = null
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
        val bytes = input.use {
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val n = it.read(buffer); if (n < 0) break
                require(out.size() + n <= BackupRepository.MAX_BYTES) { "备份文件不能超过 10 MB" }; out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        restorePreview.value = backup.preview(backup.parse(bytes.toString(Charsets.UTF_8)))
    }

    companion object {
        fun factory(context: Context) = viewModelFactory { initializer { LearningViewModel(context.applicationContext) } }
    }
}
