package com.example.visitorcapture

/** [freeText]: no chips, just a text box; whatever is typed is stored as the item under [title]. */
data class Section(val title: String?, val items: List<String>, val freeText: Boolean = false)
data class Machine(val name: String, val sections: List<Section>)

/** Catalogue helpers shared by every company. The company's own product data lives in [Brand] (one per build flavor). */
object Catalog {
    /** Machines shown for [product]; empty means the product is a single tick with nothing under it. */
    fun machinesFor(product: String): List<Machine> = Brand.machinesFor(product)

    fun path(product: String, machine: Machine, section: Section, item: String) =
        listOfNotNull(product, machine.name, section.title?.takeIf { it != item }, item).joinToString(" > ")

    /** Path prefix under which whatever is typed into a free-text section is stored. */
    fun freeTextPrefix(product: String, machine: Machine, section: Section) =
        listOfNotNull(product, machine.name, section.title).joinToString(" > ") + " > "

    /** Typed text cleaned so it can't break the " > " path or the "; " list separators. */
    fun cleanFreeText(text: String) = text.replace(">", "-").replace(";", ",").replace("\n", " ")

    fun paths(product: String, machine: Machine) =
        if (machine.sections.isEmpty()) listOf("$product > ${machine.name}")
        else machine.sections.flatMap { s -> s.items.map { path(product, machine, s, it) } }

    /** "Mechanical Spares: Carding (LMW, Rieter); Winding (Schlafhorst 238)\nRetrofits: ..." */
    fun summarize(paths: Collection<String>): String =
        paths.map { it.split(" > ") }.groupBy { it[0] }.entries.joinToString("\n") { (p, l) ->
            val ms = l.filter { it.size > 1 }.groupBy { it[1] }.entries.joinToString("; ") { (mach, x) ->
                val details = x.map { it.drop(2).joinToString(" ") }.filter { it.isNotEmpty() }
                if (details.isEmpty()) mach else mach + " (" + details.joinToString(", ") + ")"
            }
            if (ms.isEmpty()) p else "$p: $ms"
        }
}
