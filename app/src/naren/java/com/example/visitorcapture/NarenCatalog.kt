package com.example.visitorcapture

/** Naren product catalogue: Product -> Machine -> (Section) -> Make / Model / Retrofit kit */
internal object NarenCatalog {
    private fun brands(vararg i: String) = Section(null, i.toList())
    private fun sec(t: String, vararg i: String) = Section(t, i.toList())
    private fun m(n: String, vararg s: Section) = Machine(n, s.toList())

    private val G = arrayOf("LMW", "Rieter", "Trutzschler", "Others")
    private val SIMPLEX = arrayOf("Marzoli", "Toyota", "LMW", "Electro-Jet", "Rieter", "Zinser", "Others")
    private val SPIN = arrayOf("LMW", "Rieter", "Zinser", "Marzoli", "KTTM / Toyota", "Sussen", "Others")
    private val OHTC = arrayOf("ELGI", "SIMTA", "LUWA", "Electrojet", "Unirolls", "Mohler")
    private val HUMID = arrayOf("LUWA", "Batliboi", "Temsons", "Others")

    /** Same machine list for Mechanical and Electronics spares */
    private val spares = listOf(
        m("Blow Room", brands(*G)),
        m("Carding", brands(*G)),
        m("Draw Frame", brands(*G)),
        m("Comber", brands("Toyota", "LMW", "Rieter", "Others")),
        m("Lap Former", brands("LMW", "Rieter", "Others")),
        m("Simplex / Roving Frame / Speed Frame", brands(*SIMPLEX)),
        m("Spinning", brands(*SPIN)),
        m("Winding",
            sec("Schlafhorst", "238", "338", "AC5/X5", "AC6/X6"),
            sec("Savio", "Espero", "Orion", "Polar", "Eco Pulsar"),
            sec("Murata", "21C", "Qpro/Qpro+", "QproEX", "AI Cone"),
            sec("Others", "Others")),
        m("TFO", brands("Murata", "Savio", "Veejay", "Others")),
        m("SSM Winding", brands("Xeno", "XENO-YW", "XENO1", "XENO2")),
        m("Open-End", brands("Rieter", "Saurer", "Titan", "Others")),
        m("OHTC", brands(*OHTC)),
        m("YCP", brands("ELGI", "Mohler", "Sieger", "Xorella")),
        m("Humidification Plant", brands(*HUMID)),
        m("Murata Air-Jet / Vortex Spinning",
            sec("Murata Air-Jet", "860", "861", "870", "870 Ex"),
            sec("Rieter Air-Jet", "Rieter Air-Jet"))
    )

    /** "Others" make in Retrofits: the visitor types what they need instead of picking from a list. */
    private val OTHERS = Section("Others", emptyList(), freeText = true)

    private const val THERMISTOR = "Thermistor protection unit"
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
            OTHERS),
        m("Carding",
            sec("LMW", "Control panel PLC conversion", "HMI conversion", "Analog sensor", THERMISTOR),
            sec("Rieter", "Control panel PLC conversion", "Inverter conversion", THERMISTOR),
            sec("Trutzschler", "Control panel PLC conversion", "Inverter conversion", "Pressure transducer", THERMISTOR),
            OTHERS),
        m("Draw Frame",
            sec("LMW", "Sliver monitoring HMI conversion", EARTH, THERMISTOR),
            sec("Rieter", "B2 conversion kit", "Sliver monitoring HMI conversion", "DC to AC drive conversion", EARTH, THERMISTOR),
            OTHERS),
        m("Comber", *arrayOf("LMW", "Rieter").map { sec(it, "HMI conversion", EARTH, THERMISTOR) }.toTypedArray(), OTHERS),
        m("Lap Former", sec("LMW", "HMI conversion"), sec("Rieter", "HMI conversion"), OTHERS),
        m("Simplex / Roving Frame / Speed Frame",
            sec("LMW", "Servo drive PLC conversion", "PLC conversion", "Electrical panel rewiring", PHOTO, FEATHER_SW,
                "AC drive conversion"),
            sec("Zinser", PHOTO, FEATHER_SW),
            OTHERS),
        m("Spinning",
            sec("LMW", "StepSpeed controller", "PLC conversion", "HMI conversion", "Naren beltless drive conversion"),
            OTHERS),
        m("Winding - Rieter", sec("AC 338 / 338 Gold", FEATHER_PCB, "Pressure transducer"), OTHERS),
        m("Winding - Savio",
            sec("Espero", SUCTION),
            sec("Orion", SUCTION, FEELER, FEATHER_PCB),
            sec("Polar", SUCTION, FEELER, FEATHER_PCB),
            sec("Eco Pulsar", FEELER, FEATHER_PCB),
            OTHERS),
        m("OHTC", sec("ELGI", "PLC conversion"), sec("SIMTA", "PLC Conversion"), OTHERS),
        m("Humidification Plant", *HUMID.map { if (it == "Others") OTHERS else sec(it, *HUMID_KITS) }.toTypedArray()),
        m("Compressor", brands("Digital pressure monitoring display unit (4 inch / 8 inch)"), OTHERS)
    )

    /** Other Products spare parts: plain entries with nothing under them, ticked by tapping the card itself. */
    private val otherProducts = listOf("Carding Belts", "Ceramic Scissors", "Direct Coupling", "HMI Conversion")
        .map { Machine(it, emptyList()) }

    /**
     * Mechanical spares: same as [spares], plus Fluff Cleaning Gun before Winding,
     * Volkman under TFO, and Splicers + Mesdan Knotter after TFO.
     */
    private val mechanicalSpares = spares.flatMap {
        if (it.name == "Winding") listOf(
            m("Fluff Cleaning Gun",
                sec("Mechanical", "150mm", "400mm", "550mm"),
                sec("Electric", "150mm", "300mm")),
            it
        ) else if (it.name == "TFO") listOf(
            m("TFO", brands("Murata", "Savio", "Veejay", "Volkman", "Others")),
            m("Splicers", brands("110", "114", "115", "116", "490L", "498", "590L", "690", "698", "845", "895",
                "4923A", "4923B", "4923E", "4941A", "Chinese")),
            m("Mesdan Knotter", brands("L22/30I", "L-24/302", "L-26", "L-26/304", "L-30/213", "A061", "B063", "C064"))
        ) else listOf(it)
    }

    fun machinesFor(product: String) = when (product) {
        "Mechanical Spares" -> mechanicalSpares
        "Retrofits" -> retrofits
        "Other Products" -> otherProducts
        else -> spares
    }
}
