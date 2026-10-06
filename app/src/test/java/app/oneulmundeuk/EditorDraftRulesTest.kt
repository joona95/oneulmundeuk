package app.oneulmundeuk

import app.oneulmundeuk.data.draft.RecordDraft
import app.oneulmundeuk.data.model.Emotion
import app.oneulmundeuk.ui.editor.EditorExit
import app.oneulmundeuk.ui.editor.EditorState
import app.oneulmundeuk.ui.editor.editorExit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When is there a draft, and what does back / X do (pure rules; the dialog itself is driven by these). */
class EditorDraftRulesTest {
    private fun state(text: String = "", emotion: Emotion? = null, category: String? = null) =
        EditorState(text = text, emotion = emotion, categoryId = category, loaded = true)

    @Test
    fun draftFollowsTheSaveRule() {
        assertNull(RecordDraft.of("", null, null)) // freshly opened editor: defaults are not a draft
        assertNull(RecordDraft.of("  \n ", null, null))
        assertNull(RecordDraft.of("", Emotion.CALM, "c1")) // emotion / category alone cannot be saved → not a draft
        assertEquals(RecordDraft("한 줄", Emotion.CALM, "c1"), RecordDraft.of("한 줄", Emotion.CALM, "c1"))
    }

    @Test
    fun backWithTextInCreateModeAsks() {
        assertEquals(EditorExit.Confirm, editorExit(state("쓰는 중"), draftEnabled = true))
        assertEquals(EditorExit.Confirm, editorExit(state("쓰는 중", Emotion.TIRED, "c1"), draftEnabled = true))
    }

    @Test
    fun backWithNothingWrittenClosesWithoutDialog() {
        assertEquals(EditorExit.Close, editorExit(state(), draftEnabled = true))
        assertEquals(EditorExit.Close, editorExit(state(emotion = Emotion.CALM, category = "c1"), draftEnabled = true))
    }

    @Test
    fun editModeNeverAsks() {
        assertEquals(EditorExit.Close, editorExit(state("기존 기록"), draftEnabled = false))
    }

    @Test
    fun backDuringSaveIsIgnored() {
        assertEquals(EditorExit.Ignore, editorExit(state("x").copy(saving = true), draftEnabled = true))
        assertEquals(EditorExit.Ignore, editorExit(state("x").copy(saved = true), draftEnabled = true))
    }
}
