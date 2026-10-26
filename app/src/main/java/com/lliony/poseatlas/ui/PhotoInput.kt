package com.lliony.poseatlas.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** 共用图片来源入口，拍摄原图导入后删除临时文件。 */
@Composable internal fun rememberPhotoInput(
    vm: AtlasViewModel,
    multiple: Boolean = false,
    onPhotos: suspend (List<Uri>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    // 系统相机可能触发 Activity 重建，返回时仍需找到原图。
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val latestOnPhotos by rememberUpdatedState(onPhotos)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.action { latestOnPhotos(listOf(uri)) }
    }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(com.lliony.poseatlas.domain.logic.MAX_IMAGES_PER_CARD)) { uris ->
        if (uris.isNotEmpty()) vm.action { latestOnPhotos(uris) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        if (file != null) {
            if (success && !vm.busy.value) vm.action {
                try { latestOnPhotos(listOf(FileProvider.getUriForFile(context, "${context.packageName}.files", file))) }
                finally { file.delete() }
            } else file.delete()
        }
    }
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false }, title = { Text("添加图片") },
        text = { Column {
            TextButton(onClick = {
                choosing = false
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (multiple) multiPicker.launch(request) else picker.launch(request)
            }, modifier = Modifier.fillMaxWidth()) { Text("从相册选择") }
            TextButton(onClick = {
                choosing = false
                try {
                    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File.createTempFile("capture-", ".jpg", directory)
                    pendingPath = file.absolutePath
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                } catch (_: Exception) {
                    pendingPath?.let { File(it).delete() }
                    pendingPath = null
                    vm.message("无法打开相机，请检查相机是否可用，或从相册选择图片")
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("拍摄照片") }
        } },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("取消") } },
    )
    return { choosing = true }
}
