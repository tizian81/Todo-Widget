package de.tizian.todowidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLogicTest {

    private fun op(action: String, text: String, n: Int = 0, newText: String = "") =
        Prefs.Op(action, text, n, newText)

    @Test fun addAppendsAtEnd() {
        assertEquals(listOf("A", "B"), TaskLogic.apply(listOf("A"), op("add", "B")))
    }

    @Test fun addAllowsDuplicates() {
        assertEquals(listOf("A", "A"), TaskLogic.apply(listOf("A"), op("add", "A")))
    }

    @Test fun completeRemovesOnlyTappedOccurrence() {
        val list = listOf("Milch", "Brot", "Milch")
        assertEquals(listOf("Milch", "Brot"), TaskLogic.apply(list, op("complete", "Milch", 1)))
        assertEquals(listOf("Brot", "Milch"), TaskLogic.apply(list, op("complete", "Milch", 0)))
    }

    @Test fun completeWithTooHighIndexFallsBackToFirst() {
        assertEquals(listOf("B"), TaskLogic.apply(listOf("A", "B"), op("complete", "A", 5)))
    }

    @Test fun completeUnknownTaskChangesNothing() {
        assertEquals(listOf("A"), TaskLogic.apply(listOf("A"), op("complete", "X")))
    }

    @Test fun uncompleteAddsEvenIfSameTextIsStillOpen() {
        // Früher ging die Aufgabe hier verloren
        assertEquals(listOf("Milch", "Milch"), TaskLogic.apply(listOf("Milch"), op("uncomplete", "Milch")))
    }

    @Test fun completeThenUndoRestoresTask() {
        val start = listOf("A", "B")
        val done = TaskLogic.apply(start, op("complete", "A"))
        val undone = TaskLogic.apply(done, op("uncomplete", "A"))
        assertEquals(setOf("A", "B"), undone.toSet())
        assertEquals(2, undone.size)
    }

    @Test fun editReplacesTappedOccurrence() {
        val list = listOf("Milch", "Brot", "Milch")
        assertEquals(
            listOf("Milch", "Brot", "Hafermilch"),
            TaskLogic.apply(list, op("edit", "Milch", 1, "Hafermilch"))
        )
    }

    @Test fun unknownActionChangesNothing() {
        assertEquals(listOf("A"), TaskLogic.apply(listOf("A"), op("foo", "A")))
    }

    @Test fun occurrenceCountsEarlierDuplicates() {
        val list = listOf("A", "B", "A", "A")
        assertEquals(0, TaskLogic.occurrence(list, 0))
        assertEquals(0, TaskLogic.occurrence(list, 1))
        assertEquals(1, TaskLogic.occurrence(list, 2))
        assertEquals(2, TaskLogic.occurrence(list, 3))
    }

    @Test fun occurrenceAndNthIndexAreInverse() {
        val list = listOf("A", "B", "A", "C", "A")
        list.indices.forEach { pos ->
            assertEquals(pos, TaskLogic.nthIndex(list, list[pos], TaskLogic.occurrence(list, pos)))
        }
    }

    @Test fun normalizeCollapsesWhitespace() {
        assertEquals("Brot kaufen", TaskLogic.normalize("  Brot \n  kaufen\t"))
    }

    @Test fun duplicateIgnoresCase() {
        assertTrue(TaskLogic.isDuplicate(listOf("Milch"), "milch"))
        assertFalse(TaskLogic.isDuplicate(listOf("Milch"), "Brot"))
    }

    @Test fun duplicateIgnoresEditedEntryItself() {
        assertFalse(TaskLogic.isDuplicate(listOf("Milch"), "MILCH", original = "Milch"))
        assertTrue(TaskLogic.isDuplicate(listOf("Milch", "Milch"), "Milch", original = "Milch"))
    }

    @Test fun opsGetUniqueIds() {
        assertNotEquals(op("add", "A").id, op("add", "A").id)
    }
}
