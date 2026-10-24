package com.lliony.poseatlas.data.local

import android.content.Context
import com.lliony.poseatlas.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import javax.inject.Inject

class SeedLoader @Inject constructor(@ApplicationContext private val context: Context) {
    fun load(): State {
        val seed = context.assets.open("seed/seed.json").bufferedReader().use { atlasJson.decodeFromString<State>(it.readText()) }
        val rows = context.assets.open("seed/reference-rows.json").bufferedReader().use { atlasJson.decodeFromString<List<List<String>>>(it.readText()) }
        val reference = rows.mapIndexed { panel, row ->
            Card("style-reference-${panel + 1}", "reference-styles-2026", "REF-00${panel + 1}", row[2],
                tags = listOf(row[3], row[1]), gender = row[0], styles = listOf(row[1]),
                notes = Notes(pose = row[4], composition = row[5], camera = "保持自然视角，观察人物与背景的关系。", light = "优先选择柔和自然光，留意面部与背景的亮度差。", cautions = "参考姿势按现场调整，注意拍摄环境安全。"),
                images = listOf(Photo("style-reference-image-${panel + 1}", "/images/pose-reference-styles.png", panel, true)))
        }
        return seed.copy(categories = seed.categories + Category("reference-styles-2026", "人物气质参考", "REF"), cards = seed.cards + reference)
    }
}
