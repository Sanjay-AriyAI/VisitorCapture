package com.example.visitorcapture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.Color

/** Lakshmi Ring Travellers (LRT) build. */
object Brand : BrandSpec {
    override val name = "LRT"
    override val logoDescription = "Lakshmi LRT"
    // Blue lettering: always shown on its white plate.
    override val logo: Int? = R.drawable.lrt_logo
    override val logoOnDark: Int? = null

    /**
     * Deep LRT blue page with the logo's red as the accent (buttons, progress rail), white text on it.
     * Keep res/values/colors.xml brand_bg in step with [AppColors.bg] so the launch window matches.
     */
    override val colors = AppColors(
        bg = Color(0xFF0B3A75), bgDeep = Color(0xFF072A57), card = Color(0xFF114A8F), cardActive = Color(0xFF1A5BA8),
        text = Color(0xFFFFFFFF), textDim = Color(0xFFDCE7F6), muted = Color(0xFFA9C0DE),
        accent = Color(0xFFE53935), accentText = Color(0xFFFF9C94), onAccent = Color(0xFFFFFFFF),
        danger = Color(0xFFE07B39), dangerText = Color(0xFFFFE1CC), outline = Color(0x40FFFFFF), isDark = true)

    override val signature = "TEAM LRT"
    // TODO(lrt): replace with LRT's own thank-you wording.
    override val intro = listOf(
        "THANK YOU FOR VISITING LRT!" to true,
        "It was a pleasure meeting you at our stall. We sincerely appreciate your valuable time, interest, and the " +
            "opportunity to connect with you." to false,
        "We look forward to building a strong and lasting business relationship." to false,
        "LET'S CONNECT, COLLABORATE & GROW TOGETHER!" to true)

    override val products = listOf("Ring Traveller", "Application Tools", "Bobbin Holders", "LEED", "LCI")
    override val otherProduct: String? = null
    override fun productIcon(product: String) = when (product) {
        "Ring Traveller" -> Icons.Default.TripOrigin
        "Application Tools" -> Icons.Default.Handyman
        "Bobbin Holders" -> Icons.Default.Layers
        "LEED" -> Icons.Default.Widgets
        "LCI" -> Icons.Default.Category
        else -> Icons.Default.Settings
    }
    override fun machinesFor(product: String): List<Machine> = emptyList()
    override fun catalogHint(product: String) = "Tick every option the visitor is interested in."

    // ---- LRT visit flow ----
    override val customerTypes = listOf("Customer", "Agent", "Supplier", "Others")
    override val autoMarketExposure = true
    override val indianPhones = true
    override val productsHint = "Tap every product the visitor is interested in, then describe the requirement."
    override val describeAnyProduct = true
    override val leadOutcomes = listOf("Sample Request", "Complaint", "Request for Visit", "General Enquiry")
    override val outcomeDescriptionMin = 10
    override val followUpOnDone = true
    override val mailShowsNextStep = false
    override val mailShowsProfile = false
    override val mailShowsVisitDescription = true
    /** LRT blue band and headings, with the logo red as the accent line, borders and badges. */
    override val mailColors = MailColors(
        band = "#0B3A75", bandSubtle = "#C9D8EE", bandFaint = "#9FB6D6", text = "#0B2A55",
        accent = "#E53935", onAccent = "#FFFFFF", accentDark = "#0D5DB8",
        tint = "#EEF4FC", tintBorder = "#B9D0EE", chipText = "#0B3A75")
    /** LEED alone gets the LEED logo; any other choice (Ring Traveller, Application Tools, Bobbin Holders, LCI) keeps the LRT logo. */
    override fun mailLogo(products: Set<String>): Int? =
        if (products == setOf("LEED")) R.drawable.leed_logo else logo
}
