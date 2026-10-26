package com.lliony.poseatlas.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.lliony.poseatlas.data.image.ImageStore
import com.lliony.poseatlas.domain.model.MediaRef
import com.lliony.poseatlas.domain.model.Photo
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val panels = object : LruCache<String, Bitmap>(24 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount }
@Composable fun PhotoView(photo: Photo, store: ImageStore, description: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    if (photo.panel == null) {
        val model: Any? = if (MediaRef.isSeed(photo.src)) "file:///android_asset/seed${photo.src}" else store.file(photo.src)
        AsyncImage(model = model, contentDescription = description, modifier = modifier, contentScale = contentScale)
    } else {
        val key = "${photo.src}:${photo.panel}"
        val bitmap by produceState<Bitmap?>(panels.get(key), key) {
            value = withContext(Dispatchers.IO) { panels.get(key) ?: store.decode(photo, 768)?.also { panels.put(key, it) } }
        }
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), description, modifier, contentScale = contentScale)
        else Box(modifier, contentAlignment = Alignment.Center) { Text("图片暂不可用", style = MaterialTheme.typography.labelMedium) }
    }
}
