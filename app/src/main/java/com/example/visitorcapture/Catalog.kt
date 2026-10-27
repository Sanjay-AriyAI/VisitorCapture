package com.example.visitorcapture

data class Section(val title: String?, val items: List<String>)
data class Machine(val name: String, val sections: List<Section>)

/** Product catalogue: Product -> Machine -> (Section) -> Make / Model / Retrofit kit */
object Catalog {
    private fun brands(vararg i: String) = Section(null, i.toList())
    private fun sec(t: String, vararg i: String) = Section(t, i.toList())
    private fun m(n: String, vararg s: Section) = Machine(n, s.toList())

    private val G = arrayOf("LMW", "Rieter", "Trutzschler", "Others")
    private val SIMPLEX = arrayOf("Marzoli", "Toyoda", "LMW", "Electro-Jet", "Rieter", "Zinser", "Others")
    private val SPIN = arrayOf("LMW", "Rieter", "Zinser", "Marzoli", "Toyoda", "KTTM", "Susan", "Others")
    private val OHTC = arrayOf("ELGI", "SIMTA", "LUWA", "Electrojet", "Jacobi", "Unirolls", "Mozhlers")
    private val HUMID = arrayOf("LUWA", "Batliboi", "Temsons", "Others")

    /** Same machine list for Mechanical and Electronics spares */
    private val spares = listOf(
        m("Blow Room", brands(*G)),
        m("Carding", brands(*G)),
        m("Draw Frame", brands(*G)),
        m("Comber", brands("Toyoda", "LMW", "Rieter", "Others")),
        m("Lap Former", brands("LMW", "Rieter", "Others")),
        m("Simplex / Roving Frame / Speed Frame", brands(*SIMPLEX)),
        m("Spinning", brands(*SPIN)),
        m("Winding",
            sec("Schlafhorst", "238", "338", "AC5/X5", "AC6/X6"),
            sec("Savio", "Espero", "Orion", "Polar", "Eco Pulsar"),
            sec("Murata", "21C", "QPRO", "QPRO+", "7II", "7V", "7VII"),
            sec("Others", "Others")),
        m("TFO", brands("Murata", "Savio", "Zinser", "Veejay", "Omega", "Others")),
        m("SSM Winding", brands("Xeno", "TW")),
        m("Open-End", brands("Rieter", "Saurer", "Others")),
        m("OHTC", brands(*OHTC)),
        m("YCP", brands("ELGI", "Mozhlers", "Sieger", "Xorella")),
        m("Humidification Plant", brands(*HUMID)),
        m("Murata Air-Jet / Vortex Spinning",
            sec("Murata Air-Jet", "860", "870"),
            sec("Rieter Air-Jet", "Rieter Air-Jet"))
    )

    private fun make(vararg i: String) = sec("Make", *i)
    private fun kit(vararg i: String) = sec("Retrofit", *i)

    private val retrofits = listOf(
        m("Blow Room", make(*G), kit("Control panel PLC conversion", "Chute Contifeed PLC system")),
        m("Carding", make(*G), kit("Control panel PLC conversion", "HMI conversion", "Analog sensor")),
        m("Draw Frame", make(*G), kit("B2 conversion kit", "Sliver monitoring HMI conversion")),
        m("Comber", make("Toyoda", "LMW", "Rieter", "Others"), kit("HMI conversion")),
        m("Lap Former", make("LMW", "Rieter", "Others"), kit("HMI conversion")),
        m("Simplex / Roving Frame / Speed Frame", make(*SIMPLEX),
            kit("PLC conversion", "Electrical panel rewiring", "Digital photo master conversion",
                "Feather touch push button", "AC drive conversion")),
        m("Spinning", make(*SPIN),
            kit("Stepspeed controller", "PLC conversion", "HMI conversion", "Naren beltless drive conversion")),
        m("Winding",
            sec("Schlafhorst model", "238", "338", "AC5/X5", "AC6/X6"),
            sec("Schlafhorst retrofit", "Feather touch PCB conversion"),
            sec("Savio model", "Espero", "Orion", "Polar", "Eco Pulsar"),
            sec("Savio retrofit", "Suction motor PLC conversion", "Fixed yarn feeler conversion", "Feather touch PCB conversion")),
        m("OHTC", make(*OHTC), kit("PLC conversion")),
        m("Humidification Plant", make(*HUMID),
            kit("Digital LED display, suction pressure (wired)", "Digital LED display, suction pressure (wireless)"))
    )

    fun machinesFor(product: String) = if (product == "Retrofits") retrofits else spares

    fun path(product: String, machine: Machine, section: Section, item: String) =
        listOfNotNull(product, machine.name, section.title?.takeIf { it != item }, item).joinToString(" > ")

    fun paths(product: String, machine: Machine) =
        machine.sections.flatMap { s -> s.items.map { path(product, machine, s, it) } }

    /** "Mechanical Spares: Carding (LMW, Rieter); Winding (Schlafhorst 238)\nRetrofits: ..." */
    fun summarize(paths: Collection<String>): String =
        paths.map { it.split(" > ") }.groupBy { it[0] }.entries.joinToString("\n") { (p, l) ->
            val ms = l.filter { it.size > 1 }.groupBy { it[1] }.entries.joinToString("; ") { (mach, x) ->
                mach + " (" + x.joinToString(", ") { it.drop(2).joinToString(" ") } + ")"
            }
            if (ms.isEmpty()) p else "$p: $ms"
        }
}
