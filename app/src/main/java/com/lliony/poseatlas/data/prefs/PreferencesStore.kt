package com.lliony.poseatlas.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.lliony.poseatlas.data.print.PrintOptions

private val Context.atlasPreferences by preferencesDataStore("atlas-preferences")
data class Preferences(val theme: String = "system", val ratio: String = "3:4", val frame: Boolean = true, val dense: Boolean = false)
@Singleton class PreferencesStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.atlasPreferences
    val printOptions = store.data.map {
        PrintOptions(
            paper = it[stringPreferencesKey("print_paper")] ?: "a4",
            landscape = it[booleanPreferencesKey("print_landscape")] ?: true,
            shortEdge = it[booleanPreferencesKey("print_short_edge")] ?: true,
            frame = it[booleanPreferencesKey("print_frame")] ?: true
        )
    }
    suspend fun updatePrintOptions(value: PrintOptions) = store.edit {
        it[stringPreferencesKey("print_paper")] = value.paper
        it[booleanPreferencesKey("print_landscape")] = value.landscape
        it[booleanPreferencesKey("print_short_edge")] = value.shortEdge
        it[booleanPreferencesKey("print_frame")] = value.frame
    }
    val flow = store.data.map { Preferences(it[stringPreferencesKey("theme")] ?: "system", it[stringPreferencesKey("ratio")] ?: "3:4", it[booleanPreferencesKey("frame")] ?: true, it[booleanPreferencesKey("dense")] ?: false) }
    suspend fun update(value: Preferences) = store.edit {
        it[stringPreferencesKey("theme")] = value.theme; it[stringPreferencesKey("ratio")] = value.ratio
        it[booleanPreferencesKey("frame")] = value.frame; it[booleanPreferencesKey("dense")] = value.dense
    }
    suspend fun markSeeded() = store.edit { it[booleanPreferencesKey("seed_completed")] = true }
}
