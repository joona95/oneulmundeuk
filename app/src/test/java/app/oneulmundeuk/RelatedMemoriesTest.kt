package app.oneulmundeuk

import app.oneulmundeuk.data.db.RecordEntity
import app.oneulmundeuk.data.model.RecordWithCategory
import app.oneulmundeuk.related.RelatedRecords
import app.oneulmundeuk.related.resolveRelated
import app.oneulmundeuk.resurface.ResurfacedRecordSelector
import app.oneulmundeuk.ui.detail.DetailState
import app.oneulmundeuk.ui.detail.detailState
import app.oneulmundeuk.ui.home.HomeViewModel
import app.oneulmundeuk.ui.home.HomeUiState
import app.oneulmundeuk.ui.navigation.RelatedMemoriesRoute
import app.oneulmundeuk.ui.related.RelatedMemoriesState
import app.oneulmundeuk.ui.related.relatedMemoriesState
import app.oneulmundeuk.related.RelatedRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/** M6-4: ids → records for the UI, Related Memories / Detail state, Home staying time-based, and what the UI may see. */
class RelatedMemoriesTest {
    private fun rec(id: String, at: Long = 0L) = RecordWithCategory(RecordEntity(id, "t-$id", at, at, null, null, null), categoryName = null)

    @Test
    fun resolveKeepsStoredOrder() {
        val all = listOf(rec("now", 100), rec("old", 1), rec("mid", 50))
        val r = resolveRelated(all, "now", listOf("mid", "old"))!!
        assertEquals("now", r.target.record.id)
        assertEquals(listOf("mid", "old"), r.related.map { it.record.id })
    }

    @Test
    fun deletedCandidatesSelfAndDuplicatesAreSkippedRestKept() {
        val all = listOf(rec("now"), rec("a"), rec("b"))
        assertEquals(listOf("a", "b"), resolveRelated(all, "now", listOf("gone", "a", "now", "a", "b"))!!.related.map { it.record.id })
    }

    @Test
    fun nothingLeftOrTargetGoneIsNull() {
        assertNull(resolveRelated(listOf(rec("now")), "now", listOf("gone")))
        assertNull(resolveRelated(listOf(rec("now"), rec("a")), "now", emptyList()))
        assertNull(resolveRelated(listOf(rec("a")), "now", listOf("a")))
    }

    @Test
    fun screenStateClosesWhenNothingToShow() {
        val gone = relatedMemoriesState(null)
        assertTrue(gone.gone)
        assertFalse(gone.loading)
        val shown = relatedMemoriesState(RelatedRecords(rec("now"), listOf(rec("a"))))
        assertFalse(shown.gone)
        assertEquals("now", shown.current?.record?.id)
        assertEquals(listOf("a"), shown.past.map { it.record.id })
    }

    @Test
    fun routeCarriesOnlyTheTargetId() {
        assertEquals(listOf("recordId"), RelatedMemoriesRoute::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name })
    }

    /** The UI never sees the pipeline version, model ids, text hashes, labels or scores. */
    @Test
    fun pipelineDetailsAreNotExposedToTheUi() {
        val forbidden = listOf("version", "pipeline", "hash", "label", "model", "similarity", "score")
        listOf(RelatedRecords::class.java, RelatedMemoriesState::class.java, HomeUiState::class.java, DetailState.Loaded::class.java)
            .flatMap { c -> c.declaredFields.map { c.simpleName + "." + it.name } }
            .forEach { f -> assertTrue(f, forbidden.none { f.substringAfter('.').contains(it, ignoreCase = true) }) }
        // RelatedRepository API: a record id in, records out — no version parameter anywhere.
        RelatedRepository::class.java.declaredMethods.filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }.forEach { m ->
            assertTrue(m.name, m.parameterTypes.all { it == String::class.java } && m.parameterCount <= 1)
        }
    }

    @Test
    fun detailHidesTheSectionWithoutResults() {
        assertEquals(emptyList<RecordWithCategory>(), (detailState(rec("now"), null) as DetailState.Loaded).related)
        val loaded = detailState(rec("now"), RelatedRecords(rec("now"), listOf(rec("b"), rec("a")))) as DetailState.Loaded
        assertEquals(listOf("b", "a"), loaded.related.map { it.record.id }) // relevance order kept
        assertEquals(DetailState.Gone, detailState(null, null))
    }

    /** Home is time-based only: no semantic highlight in its state, no related source in its ViewModel. */
    @Test
    fun homeHasNoSemanticHighlight() {
        assertTrue(HomeUiState::class.java.declaredFields.none { it.type == RelatedRecords::class.java })
        HomeViewModel::class.java.constructors.forEach { c ->
            assertTrue(c.parameterTypes.none { it == RelatedRepository::class.java })
        }
    }

    /** "다시 만난 생각" depends only on records + date (M3 rule), never on semantic results. */
    @Test
    fun resurfacingIsIndependentOfSemanticResults() {
        val related = listOf(RelatedRecords::class.java, RelatedRepository::class.java)
        ResurfacedRecordSelector::class.java.methods.forEach { m ->
            assertTrue(m.name, m.parameterTypes.none { it in related })
        }
    }
}
