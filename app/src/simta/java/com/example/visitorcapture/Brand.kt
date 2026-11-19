package com.example.visitorcapture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color

/** SIMTA build. */
object Brand : BrandSpec {
    override val name = "SIMTA"
    override val logoDescription = "SIMTA"
    // TODO(simta): add src/simta/res/drawable-nodpi/simta_logo.png (dark letters, transparent background) and
    // simta_logo_dark.png (light letters), then set these to R.drawable.simta_logo / R.drawable.simta_logo_dark.
    // Until then the app and the mail header show the name as text.
    override val logo: Int? = null
    override val logoOnDark: Int? = null

    // TODO(simta): placeholder palette (deep blue + amber). Replace with SIMTA's brand colours; keep
    // res/values/colors.xml brand_bg in step with [AppColors.bg] so the launch window matches.
    override val colors = AppColors(
        bg = Color(0xFF1F4E8C), bgDeep = Color(0xFF173D6E), card = Color(0xFF1A4579), cardActive = Color(0xFF153A66),
        text = Color(0xFFFFFFFF), textDim = Color(0xFFE2EAF5), muted = Color(0xFFBCCBE0),
        accent = Color(0xFFE8A33D), accentText = Color(0xFFFFD27A), onAccent = Color(0xFF0B2033),
        danger = Color(0xFFC0563F), dangerText = Color(0xFFFFE1D9), outline = Color(0x40FFFFFF), isDark = true)

    override val signature = "TEAM SIMTA"
    // TODO(simta): replace with SIMTA's own thank-you wording.
    override val intro = listOf(
        "THANK YOU FOR VISITING SIMTA!" to true,
        "It was a pleasure meeting you at our stall. We sincerely appreciate your valuable time, interest, and the " +
            "opportunity to connect with you." to false,
        "We look forward to building a strong and lasting business relationship." to false,
        "LET'S CONNECT, COLLABORATE & GROW TOGETHER!" to true)

    override val products = listOf("Spindle Tape", "Clearer Roller", "OHTC", "Autopacker", "BTS", "CTS", "Others")
    override val otherProduct = "Others"
    override fun productIcon(product: String) = when (product) {
        "Spindle Tape" -> Icons.Default.LinearScale
        "Clearer Roller" -> Icons.Default.Cached
        "OHTC" -> Icons.Default.Air
        "Autopacker" -> Icons.Default.Inventory
        "BTS", "CTS" -> Icons.Default.PrecisionManufacturing
        "Others" -> Icons.Default.MoreHoriz
        else -> Icons.Default.Settings
    }
    /** No sub-lists yet: every product is a single tick. Return machines here to give a product its own catalogue screen. */
    override fun machinesFor(product: String): List<Machine> = emptyList()
    override fun catalogHint(product: String) = "Tick every option the visitor is interested in."
}
