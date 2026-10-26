package com.lliony.poseatlas.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun UpdateSection(vm: UpdateViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var showNotes by remember { mutableStateOf(false) }
    fun install() {
        val file = vm.state.value.apk ?: return
        if (!file.exists()) { vm.message("安装包已被清理，请重新下载"); return }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { vm.message("无法打开安装界面，请检查安装权限或重新下载") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install()
        else vm.message("未允许安装应用。开启权限后可再次点击安装。")
    }
    Text("姿势图鉴 ${vm.currentVersion}", style = MaterialTheme.typography.titleMedium)
    state.release?.let {
        Text("发现新版本 ${it.version} · ${"%.1f".format(it.size / 1048576.0)} MB", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { showNotes = true }) { Text("查看更新说明") }
    }
    state.progress?.let {
        LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
        Text("下载中 $it%", style = MaterialTheme.typography.bodySmall)
    }
    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) TextButton(onClick = vm::cancel) { Text(if (state.progress == null) "取消检查" else "取消下载") }
        else {
            OutlinedButton(onClick = vm::check) { Text("检查更新") }
            if (state.release != null) Button(onClick = {
                if (state.apk == null || state.apk?.exists() != true) vm.download()
                else if (context.packageManager.canRequestPackageInstalls()) install()
                else {
                    vm.message("请允许姿势图鉴安装应用，返回后继续安装")
                    try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                    catch (_: Exception) { vm.message("无法打开权限设置，请在系统设置中允许此应用安装未知应用") }
                }
            }) { Text(if (state.apk == null) "下载更新" else "安装更新") }
        }
    }
    if (state.busy && state.progress == null) Text("正在检查更新…", style = MaterialTheme.typography.bodySmall)
    if (showNotes) AlertDialog(onDismissRequest = { showNotes = false }, title = { Text("更新说明") },
        text = { Text(state.release?.notes?.ifBlank { "此版本未提供更新说明" }.orEmpty(), Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showNotes = false }) { Text("关闭") } })
}
