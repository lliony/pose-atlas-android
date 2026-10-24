package com.lliony.poseatlas.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lliony.poseatlas.domain.logic.cropWindow
import com.lliony.poseatlas.domain.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

data class CropSession(val cardId: String, val photo: Photo)

@Composable fun CropScreen(vm: AtlasViewModel, target: CropSession) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.cropError.collectAsStateWithLifecycle()
    var ratioName by rememberSaveable(target.cardId, target.photo.id, target.photo.src) { mutableStateOf("原比例") }
    var zoom by rememberSaveable(target.cardId, target.photo.id, target.photo.src) { mutableFloatStateOf(1f) }
    var centerX by rememberSaveable(target.cardId, target.photo.id, target.photo.src) { mutableFloatStateOf(.5f) }
    var centerY by rememberSaveable(target.cardId, target.photo.id, target.photo.src) { mutableFloatStateOf(.5f) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    val bitmap by produceState<Bitmap?>(null, target.photo.src, target.photo.panel, attempt) {
        loading = true
        value = withContext(Dispatchers.IO) { vm.repository.images.decode(target.photo, 1024) }
        loading = false
    }
    val imageAspect = bitmap?.let { it.width.toFloat() / it.height } ?: 1f
    val ratio = if (ratioName == "原比例") imageAspect else ratioName.split(':').let { it[0].toFloat() / it[1].toFloat() }
    val window = cropWindow(imageAspect, ratio, zoom, centerX, centerY)
    fun close() { if (!busy) vm.cancelCrop() }
    BackHandler { close() }
    Column(Modifier.fillMaxSize().testTag("crop-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::close, enabled = !busy) { Icon(Icons.Default.Close, "取消裁切") }
            Text("裁切图片", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { vm.applyCrop(window) }, enabled = bitmap != null && !busy) { Text(if (busy) "保存中…" else "确认裁切") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val landscape = maxWidth > maxHeight * 1.5f
            val controlsMaxHeight = maxHeight * .5f
            @Composable fun controls(modifier: Modifier) {
                Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceRow("裁切比例", listOf("原比例", "1:1", "2:3", "3:2", "3:4", "4:3", "9:16", "16:9"), ratioName, enabled = !busy) { name ->
                        centerX = window.centerX; centerY = window.centerY; ratioName = name
                    }
                    Text("拖动画面、双指缩放，选择框内要保留的部分。", style = MaterialTheme.typography.bodySmall)
                    Text("缩放 ${"%.1f".format(zoom)}×", style = MaterialTheme.typography.labelLarge)
                    Slider(zoom, { centerX = window.centerX; centerY = window.centerY; zoom = it }, valueRange = 1f..8f, enabled = !busy && bitmap != null, modifier = Modifier.semantics { contentDescription = "裁切缩放" })
                    Row {
                        TextButton(onClick = { zoom = 1f; centerX = .5f; centerY = .5f }, enabled = !busy) { Text("重置位置") }
                        TextButton(onClick = { ratioName = "原比例"; zoom = 1f; centerX = .5f; centerY = .5f }, enabled = !busy) { Text("恢复原图范围") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            @Composable fun preview(modifier: Modifier) {
                Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
                    val source = bitmap
                    if (loading) CircularProgressIndicator()
                    else if (source == null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("图片读取失败，原图未更改", color = Color.White)
                        Button(onClick = { attempt++ }) { Text("重试读取") }
                    } else {
                        var size by remember { mutableStateOf(IntSize.Zero) }
                        val inset = 20f
                        val width = min((size.width - inset * 2).coerceAtLeast(1f), (size.height - inset * 2).coerceAtLeast(1f) * ratio)
                        val height = width / ratio
                        val frame = Rect((size.width - width) / 2, (size.height - height) / 2, (size.width + width) / 2, (size.height + height) / 2)
                        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
                        Canvas(Modifier.fillMaxSize().clipToBounds().testTag("crop-canvas")
                            .semantics { contentDescription = "图片裁切预览，可拖动和双指缩放，也可使用缩放滑块" }
                            .onSizeChanged { size = it }
                            .pointerInput(source, ratio, frame, busy) {
                                if (!busy) detectTransformGestures { centroid, pan, scale, _ ->
                                    val old = cropWindow(imageAspect, ratio, zoom, centerX, centerY)
                                    val newZoom = (zoom * scale).coerceIn(1f, 8f)
                                    val next = cropWindow(imageAspect, ratio, newZoom, old.centerX, old.centerY)
                                    val anchorX = ((centroid.x - frame.left) / frame.width).coerceIn(0f, 1f)
                                    val anchorY = ((centroid.y - frame.top) / frame.height).coerceIn(0f, 1f)
                                    val clamped = cropWindow(imageAspect, ratio, newZoom,
                                        old.centerX + (anchorX - .5f) * (old.width - next.width) - pan.x / frame.width * next.width,
                                        old.centerY + (anchorY - .5f) * (old.height - next.height) - pan.y / frame.height * next.height)
                                    zoom = newZoom; centerX = clamped.centerX; centerY = clamped.centerY
                                }
                            }) {
                            val displayedWidth = frame.width / window.width
                            val displayedHeight = frame.height / window.height
                            val left = frame.left - window.left * displayedWidth
                            val top = frame.top - window.top * displayedHeight
                            drawContext.canvas.nativeCanvas.drawBitmap(source, null, RectF(left, top, left + displayedWidth, top + displayedHeight), paint)
                            val shade = Color.Black.copy(alpha = .6f)
                            drawRect(shade, Offset.Zero, Size(size.width.toFloat(), frame.top))
                            drawRect(shade, Offset(0f, frame.bottom), Size(size.width.toFloat(), size.height - frame.bottom))
                            drawRect(shade, Offset(0f, frame.top), Size(frame.left, frame.height))
                            drawRect(shade, Offset(frame.right, frame.top), Size(size.width - frame.right, frame.height))
                            drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
                            for (line in 1..2) {
                                val x = frame.left + frame.width * line / 3
                                val y = frame.top + frame.height * line / 3
                                drawLine(Color.White.copy(alpha = .65f), Offset(x, frame.top), Offset(x, frame.bottom))
                                drawLine(Color.White.copy(alpha = .65f), Offset(frame.left, y), Offset(frame.right, y))
                            }
                        }
                    }
                }
            }
            if (landscape) Row(Modifier.fillMaxSize()) { preview(Modifier.weight(1f).fillMaxHeight()); controls(Modifier.width(280.dp).fillMaxHeight()) }
            else Column(Modifier.fillMaxSize()) { preview(Modifier.weight(1f).fillMaxWidth()); controls(Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight)) }
        }
    }
}
