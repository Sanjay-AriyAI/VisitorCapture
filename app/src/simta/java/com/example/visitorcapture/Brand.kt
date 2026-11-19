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
    override val logoDescription = "SIMTA Jacobi"
    // Always shown on its white plate: straight on the background the logo's blue frame melts into the page.
    override val logo: Int? = R.drawable.simta_logo
    override val logoOnDark: Int? = null

    /**
     * Deep navy page so the SIMTA logo blue (#01A0E2) stands out; that blue is the accent (buttons, progress rail),
     * with dark navy text on it for contrast.
     * Keep res/values/colors.xml brand_bg in step with [AppColors.bg] so the launch window matches.
     */
    override val colors = AppColors(
        bg = Color(0xFF0E2A47), bgDeep = Color(0xFF0A1F36), card = Color(0xFF15365A), cardActive = Color(0xFF1B4270),
        text = Color(0xFFFFFFFF), textDim = Color(0xFFDCE8F5), muted = Color(0xFFA9BED6),
        accent = Color(0xFF01A0E2), accentText = Color(0xFF5CC8F5), onAccent = Color(0xFF04172A),
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
