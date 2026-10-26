package com.lliony.poseatlas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lliony.poseatlas.data.update.UpdateRelease
import com.lliony.poseatlas.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

data class UpdateState(val busy: Boolean = false, val progress: Int? = null, val release: UpdateRelease? = null, val apk: File? = null, val message: String? = null)

/** 与设置页生命周期分离，旋转或暂离页面时保留下载与安装状态。 */
@HiltViewModel class UpdateViewModel @Inject constructor(private val repository: UpdateRepository) : ViewModel() {
    val currentVersion = repository.currentVersion
    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    fun check() {
        if (mutableState.value.busy) return
        mutableState.value = UpdateState(busy = true)
        operation = viewModelScope.launch {
            try {
                val release = repository.check()
                mutableState.value = UpdateState(release = release, message = if (release == null) "当前已是最新版本" else null)
            } catch (e: CancellationException) { mutableState.value = UpdateState(message = "已取消检查"); throw e }
            catch (e: Exception) { mutableState.value = UpdateState(message = "检查失败：${e.localizedMessage ?: "请检查网络后重试"}") }
        }
    }
    fun download() {
        val release = mutableState.value.release ?: return
        if (mutableState.value.busy) return
        mutableState.value = UpdateState(busy = true, progress = 0, release = release)
        operation = viewModelScope.launch {
            try {
                val file = repository.download(release) { mutableState.value = mutableState.value.copy(progress = it) }
                mutableState.value = UpdateState(release = release, apk = file, message = "下载并校验完成，可以安装")
            } catch (e: CancellationException) { mutableState.value = UpdateState(release = release, message = "已取消下载"); throw e }
            catch (e: Exception) { mutableState.value = UpdateState(release = release, message = "下载失败：${e.localizedMessage ?: "请检查网络后重试"}") }
        }
    }
    fun cancel() { operation?.cancel() }
    fun message(text: String) { mutableState.value = mutableState.value.copy(message = text) }
}
