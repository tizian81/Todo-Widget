package de.tizian.todowidget

/**
 * Reine Listenlogik ohne Android-Abhängigkeiten (dadurch per Unit-Test prüfbar).
 * Muss sich genauso verhalten wie das Apps Script.
 */
object TaskLogic {

    /** Wendet eine Aktion lokal auf die Liste der offenen Aufgaben an. */
    fun apply(list: List<String>, op: Prefs.Op): List<String> = when (op.action) {
        "add", "uncomplete" -> list + op.text
        "complete" -> {
            val i = nthIndex(list, op.text, op.n)
            if (i < 0) list else list.filterIndexed { idx, _ -> idx != i }
        }
        "edit" -> {
            val i = nthIndex(list, op.text, op.n)
            if (i < 0) list else list.mapIndexed { idx, t -> if (idx == i) op.newText else t }
        }
        else -> list
    }

    /** Position des n-ten Vorkommens von [text] (sonst des ersten, sonst -1). */
    fun nthIndex(list: List<String>, text: String, n: Int): Int {
        val hits = list.indices.filter { list[it] == text }
        return hits.getOrNull(n) ?: hits.firstOrNull() ?: -1
    }

    /** Welches Vorkommen ist die Aufgabe an [position] (0 = erstes gleichlautendes)? */
    fun occurrence(list: List<String>, position: Int): Int =
        list.take(position).count { it == list[position] }

    /** Leerzeichen/Zeilenumbrüche zusammenfassen, Ränder abschneiden. */
    fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    /**
     * Gibt es [text] schon (Groß-/Kleinschreibung egal)?
     * Beim Bearbeiten zählt der bearbeitete Eintrag [original] selbst nicht mit.
     */
    fun isDuplicate(tasks: List<String>, text: String, original: String? = null): Boolean {
        val others = tasks.toMutableList()
        if (original != null) others.remove(original)
        return others.any { it.equals(text, ignoreCase = true) }
    }
}
