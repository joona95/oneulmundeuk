package app.oneulmundeuk.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.oneulmundeuk.ui.components.MarkerShape
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** User settings (Settings screen). Every value has a safe default, so a missing / unreadable file = defaults. */
data class AppSettings(
    /** 내 감정 조각: one shape for every emotion marker. */
    val markerShape: MarkerShape = MarkerShape.Default,
    /**
     * 다시 만나기 알림 — time-based resurfacing (the same family as Home "다시 만난 생각"), NOT semantic AI.
     * TODO(reminder): only the preference exists; no notification is scheduled yet (WorkManager / notification
     *  permission / channel come in their own step).
     */
    val reminderEnabled: Boolean = false,
    /**
     * 관련된 생각 — the user's wish to use the on-device semantic feature. See [RelatedThoughtsStatus].
     * TODO(related-model): not yet wired to the pipeline; the runtime (release: none, debug: fake behind a flag file)
     *  is unchanged. Gate analysis on this when the real model download lands. Turning it off never deletes anything.
     */
    val relatedEnabled: Boolean = false,
)

/**
 * 관련된 생각 as shown in Settings. Only [AppSettings.relatedEnabled] is persisted; the rest is derived.
 * [DOWNLOADING] / [READY] need the model download step — nothing produces them yet (no fake progress).
 */
enum class RelatedThoughtsStatus { OFF, MODEL_NOT_DOWNLOADED, DOWNLOADING, READY }

fun relatedThoughtsStatus(enabled: Boolean, modelInstalled: Boolean, downloading: Boolean = false): RelatedThoughtsStatus = when {
    !enabled -> RelatedThoughtsStatus.OFF
    modelInstalled -> RelatedThoughtsStatus.READY
    downloading -> RelatedThoughtsStatus.DOWNLOADING
    else -> RelatedThoughtsStatus.MODEL_NOT_DOWNLOADED
}

/**
 * Settings in the shared local Preferences DataStore (`local_prefs`, same file as the editor draft but its own
 * `settings.*` keys — the draft store never touches them and vice versa).
 */
class SettingsStore(private val prefs: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = prefs.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                markerShape = MarkerShape.fromKey(p[MARKER_SHAPE]),
                reminderEnabled = p[REMINDER_ENABLED] ?: false,
                relatedEnabled = p[RELATED_ENABLED] ?: false,
            )
        }
        .distinctUntilChanged()

    suspend fun setMarkerShape(shape: MarkerShape) { prefs.edit { it[MARKER_SHAPE] = shape.key } }
    suspend fun setReminderEnabled(enabled: Boolean) { prefs.edit { it[REMINDER_ENABLED] = enabled } }
    suspend fun setRelatedEnabled(enabled: Boolean) { prefs.edit { it[RELATED_ENABLED] = enabled } }

    private companion object {
        val MARKER_SHAPE = stringPreferencesKey("settings.marker_shape")
        val REMINDER_ENABLED = booleanPreferencesKey("settings.reminder_enabled")
        val RELATED_ENABLED = booleanPreferencesKey("settings.related_enabled")
    }
}
