package com.example.visitorcapture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.ui.graphics.Color

/** Naren Group build. */
object Brand : BrandSpec {
    override val name = "Naren Group"
    override val logoDescription = "Naren - Empowering quality and trust"
    override val logo = R.drawable.naren_logo
    override val logoOnDark = R.drawable.naren_logo_dark

    /** Brand teal #18A99F with white text and amber accents. */
    override val colors = AppColors(
        bg = Color(0xFF18A99F), bgDeep = Color(0xFF0E8A81), card = Color(0xFF12968D), cardActive = Color(0xFF0F8279),
        text = Color(0xFFFFFFFF), textDim = Color(0xFFE3F6F4), muted = Color(0xFFC2EAE6),
        accent = Color(0xFFE8A33D), accentText = Color(0xFFFFD27A), onAccent = Color(0xFF0B2033),
        danger = Color(0xFFC0563F), dangerText = Color(0xFFFFE1D9), outline = Color(0x40FFFFFF), isDark = true)

    override val signature = "TEAM NAREN"
    override val companyDefaults = CompanyInfo(
        phone = "+91 98429 91141 / +91 99655 16076",
        email = "export@narenonline.com / customerssupport@narenonline.com",
        website = "https://narengroup.in")
    override val intro = listOf(
        "A BIG THANK YOU FOR CONNECTING WITH NAREN AT INDIA ITME 2026!" to true,
        "It was a pleasure welcoming you to the NAREN Group Pavilion at Greater Noida. We sincerely appreciate your valuable " +
            "time, interest, and the opportunity to connect with you." to false,
        "We believe great partnerships begin with meaningful connections and look forward to building a strong and lasting " +
            "business relationship." to false,
        "DISCOVER THE WORLD OF NAREN!" to true,
        "Explore our product catalogue through the link below and discover our range of innovative textile machinery solutions." to false,
        "Catalogue Link: https://drive.google.com/drive/folders/1C3d6lmh5TtbQhDpH47kYtNHB-zKqzVu3" to false,
        "Thank you for being part of our journey!" to false,
        "LET'S CONNECT, COLLABORATE & GROW TOGETHER!" to true)

    override val products = listOf("Mechanical Spares", "Electronics Spares", "Retrofits", "Other Products")
    override val otherProduct = "Other Products"
    override val mailShowsProfile = false
    /** Naren teal band with the amber accent, like the app. */
    override val mailColors = MailColors(
        band = "#0E7C74", bandSubtle = "#D2F1EE", bandFaint = "#A9DCD7", text = "#0A3A36",
        accent = "#E8A33D", onAccent = "#0B2033", accentDark = "#A35F00",
        tint = "#FFF8EC", tintBorder = "#F3D19C", chipText = "#7A4600")
    override fun productIcon(product: String) = when (product) {
        "Mechanical Spares" -> Icons.Default.Build
        "Electronics Spares" -> Icons.Default.Memory
        "Retrofits" -> Icons.Default.Autorenew
        else -> Icons.Default.Inventory2
    }
    override fun machinesFor(product: String) = NarenCatalog.machinesFor(product)
    override fun catalogHint(product: String) = when (product) {
        "Retrofits" -> "Tap a machine, then tick the retrofits wanted under each make."
        "Other Products" -> "Tap every spare part the visitor is interested in."
        else -> "Tap a machine, then tick every make or model you deal with."
    }
}
