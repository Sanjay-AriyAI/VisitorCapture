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

    /** A make with no retrofit listed yet: a single chip with the make's name, so the visitor can still record it. */
    private fun bare(make: String) = sec(make, make)

    private const val THERMISTOR = "Thermistor production unit"
    private const val EARTH = "Earth stop motion unit"
    private const val PHOTO = "Digital photo master conversion"
    private const val FEATHER_SW = "Feather-touch switch conversion"
    private const val FEATHER_PCB = "Feather-touch PCB conversion"
    private const val SUCTION = "Suction motor PLC conversion"
    private const val FEELER = "Fixed yarn feeler conversion"
    private val HUMID_KITS = arrayOf("Digital LED display for suction pressure monitoring",
        "RH and temperature display monitoring (wired/wireless)")

    /** Retrofit kits per make, as in the "Retrofits Spare parts (per-machine cascading)" sheet */
    private val retrofits = listOf(
        m("Blow Room",
            sec("LMW", "Control panel PLC conversion", "Chute Contifeed PLC system"),
            sec("Rieter", "Control panel PLC conversion"),
            sec("Trutzschler", "Control panel PLC conversion", "Chute Contifeed PLC system"),
            sec("Others", "Control panel PLC conversion", "Chute Contifeed PLC system")),
        m("Carding",
            sec("LMW", "Control panel PLC conversion", "HMI conversion", "Analog sensor", THERMISTOR),
            sec("Rieter", "Control panel PLC conversion", "Inverter conversion", THERMISTOR),
            sec("Trutzschler", "Control panel PLC conversion", "Inverter conversion", "Pressure transducer", THERMISTOR),
            sec("Others", "Control panel PLC conversion", "HMI conversion", "Analog sensor", "Inverter conversion",
                "Pressure transducer", THERMISTOR)),
        m("Draw Frame",
            sec("LMW", "Sliver monitoring HMI conversion", EARTH, THERMISTOR),
            sec("Rieter", "B2 conversion kit", "Sliver monitoring HMI conversion", "DC to AC drive conversion", EARTH, THERMISTOR),
            bare("Trutzschler"),
            sec("Others", "Sliver monitoring HMI conversion", "B2 conversion kit", "DC to AC drive conversion", EARTH, THERMISTOR)),
        m("Comber", *arrayOf("LMW", "Rieter", "Trutzschler", "Others")
            .map { sec(it, "HMI conversion", EARTH, THERMISTOR) }.toTypedArray()),
        m("Lap Former", sec("LMW", "HMI conversion"), bare("Rieter"), bare("Others")),
        m("Simplex / Roving Frame / Speed Frame",
            sec("LMW", "Servo drive PLC conversion", "PLC conversion", "Electrical panel rewiring", PHOTO, FEATHER_SW,
                "AC drive conversion"),
            sec("Zinser", PHOTO, FEATHER_SW),
            bare("Rieter"),
            sec("Others", "Servo drive PLC conversion", "PLC conversion", "Electrical panel rewiring", PHOTO, FEATHER_SW,
                "AC drive conversion")),
        m("Spinning",
            sec("LMW", "StepSpeed controller", "PLC conversion", "HMI conversion", "Naren beltless drive conversion"),
            sec("Rieter", "Naren beltless drive conversion"),
            bare("Zinser"),
            sec("Others", "StepSpeed controller", "PLC conversion", "HMI conversion", "Naren beltless drive conversion")),
        m("Winding - Rieter", sec("AC 338 / 338 Gold", FEATHER_PCB, "Pressure transducer")),
        m("Winding - Savio",
            sec("Espero", SUCTION),
            sec("Orion", SUCTION, FEELER, FEATHER_PCB),
            sec("Polar", SUCTION, FEELER, FEATHER_PCB),
            sec("Eco Pulsar", FEELER, FEATHER_PCB)),
        m("OHTC", sec("ELGI", "PLC conversion"), bare("SIMTA"), bare("LUWA"), bare("Electro-Jet")),
        m("Humidification Plant", *HUMID.map { sec(it, *HUMID_KITS) }.toTypedArray()),
        m("Compressor", brands("Digital pressure monitoring display unit (4 inch / 8 inch)"))
    )

    /** Other Products spare parts: plain entries with nothing under them, ticked by tapping the card itself. */
    private val otherProducts = listOf("Carding Belts", "Ceramic Scissors", "Direct Coupling", "HMI Conversion")
        .map { Machine(it, emptyList()) }

    fun machinesFor(product: String) = when (product) {
        "Retrofits" -> retrofits
        "Other Products" -> otherProducts
        else -> spares
    }

    fun path(product: String, machine: Machine, section: Section, item: String) =
        listOfNotNull(product, machine.name, section.title?.takeIf { it != item }, item).joinToString(" > ")

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
