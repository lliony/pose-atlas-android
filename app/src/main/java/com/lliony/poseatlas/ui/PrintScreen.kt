package com.lliony.poseatlas.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.*
import android.print.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lliony.poseatlas.data.print.PrintOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable fun PrintScreen(vm: AtlasViewModel, ids: List<String>, back: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var options by remember { mutableStateOf(PrintOptions()) }
    var optionsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { options = vm.readPrintOptions(); optionsLoaded = true }
    LaunchedEffect(options, optionsLoaded) { if (optionsLoaded) vm.savePrintOptions(options) }
    var file by remember { mutableStateOf<File?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null) vm.action("PDF 已保存") { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out -> file!!.inputStream().use { it.copyTo(out) } } ?: error("无法写入文件") } } }
    LaunchedEffect(file, page) {
        val current = file ?: return@LaunchedEffect
        preview = withContext(Dispatchers.IO) { PdfRenderer(ParcelFileDescriptor.open(current, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            count = pdf.pageCount
            pdf.openPage(page.coerceIn(0, pdf.pageCount - 1)).use { p -> Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE); p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) } }
        } }
    }
    Column {
        PageTitle("打印与分享", back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceRow("纸张", listOf("a4", "a5", "a3", "letter"), options.paper, enabled = !busy && optionsLoaded) { options = options.copy(paper = it); file = null; preview = null }
            ChoiceRow("方向", listOf("横向", "纵向"), if (options.landscape) "横向" else "纵向", enabled = !busy && optionsLoaded) { options = options.copy(landscape = it == "横向"); file = null; preview = null }
            ChoiceRow("双面翻转", listOf("短边", "长边"), if (options.shortEdge) "短边" else "长边", enabled = !busy && optionsLoaded) { options = options.copy(shortEdge = it == "短边"); file = null; preview = null }
            ToggleRow("裁切虚线", options.frame, enabled = !busy && optionsLoaded) { options = options.copy(frame = it); file = null; preview = null }
            ToggleRow("生成双面校验页", options.calibration, enabled = !busy && optionsLoaded) { options = options.copy(calibration = it); file = null; preview = null }
            Text("每张图片印一张卡。App 配对正反面，打印机需选择对应双面翻转；也可手动送纸。私人备注不输出。", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { val requested = options; vm.action { val cards = ids.mapNotNull { id -> state.cards.find { it.id == id && !it.deleted } }; val generated = withContext(Dispatchers.IO) { vm.pdf.render(cards, state.categories, requested) }; if (options == requested) { file = generated; page = 0 } } }, enabled = !busy && optionsLoaded) { Text("生成并检查版面") }
            if (file != null) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { save.launch("pose-atlas.pdf") }) { Text("另存 PDF") }
                    TextButton(onClick = { shareFile(context, file!!, "application/pdf") }) { Text("分享 PDF") }
                    TextButton(onClick = { printFile(context, file!!, options) }) { Text("系统打印") }
                }
                Row { TextButton(onClick = { page-- }, enabled = page > 0) { Text("上一面") }; Text("${page + 1}/$count"); TextButton(onClick = { page++ }, enabled = page + 1 < count) { Text("下一面") } }
                preview?.let { Image(it.asImageBitmap(), "第 ${page + 1} 面打印预览", Modifier.fillMaxWidth()) }
            }
        }
    }
}

fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "分享"))
}
fun shareImages(context: Context, files: List<File>) {
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/png"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "分享卡片正反面"))
}
private fun printFile(context: Context, file: File, options: PrintOptions) {
    val paper = when (options.paper) { "a5" -> PrintAttributes.MediaSize.ISO_A5; "a3" -> PrintAttributes.MediaSize.ISO_A3; "letter" -> PrintAttributes.MediaSize.NA_LETTER; else -> PrintAttributes.MediaSize.ISO_A4 }
    val attributes = PrintAttributes.Builder().setMediaSize(if (options.landscape) paper.asLandscape() else paper.asPortrait()).setMinMargins(PrintAttributes.Margins.NO_MARGINS).setResolution(PrintAttributes.Resolution("300", "300 dpi", 300, 300)).build()
    (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("姿势图鉴", object : PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
            if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
        }
        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
            try {
                if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
                FileOutputStream(destination.fileDescriptor).use { out -> file.inputStream().use { it.copyTo(out) } }
                if (cancellationSignal.isCanceled) callback.onWriteCancelled() else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (_: Exception) { callback.onWriteFailed("无法写入打印文件") }
        }
    }, attributes)
}
