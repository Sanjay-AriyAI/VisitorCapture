package com.example.visitorcapture

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Everything that differs between the companies this app is built for. Each build flavor (src/naren, src/simta, ...)
 * provides one `object Brand : BrandSpec`; the rest of the app only ever talks to [Brand], so shared code stays shared.
 * Adding a company = new flavor in app/build.gradle.kts + src/<flavor>/java/.../Brand.kt + src/<flavor>/res assets.
 */
interface BrandSpec {
    /** Company name used in the UI and as the default "From" name of the mails. */
    val name: String
    /** Accessibility text for the logo. */
    val logoDescription: String
    /** Logo with dark lettering, shown on a white plate in the app and in the e-mail header (must be a PNG). Null = show [name] as text. */
    @get:DrawableRes val logo: Int?
    /** Logo with light lettering, shown straight on the dark app background. Null = use [logo] on its white plate. */
    @get:DrawableRes val logoOnDark: Int?
    /** App colour tokens. */
    val colors: AppColors

    /** Sign-off under "Warm regards" in the mails. */
    val signature: String
    /** Opening paragraphs of the thank-you mail; true marks a headline (bold in the HTML mail). */
    val intro: List<Pair<String, Boolean>>
    /** "Contact us" details pre-filled on the Mail settings screen until staff enter their own. */
    val companyDefaults: CompanyInfo get() = CompanyInfo()

    /** Top-level products on the "Product profile" screen, in order. */
    val products: List<String>
    /** The product whose selection reveals the "Tell us more" free-text box (stored in Visitor.otherDetail); null = none. */
    val otherProduct: String?
    fun productIcon(product: String): ImageVector
    /** Machines under [product]; an empty list makes the product a single tick (no catalogue screen). */
    fun machinesFor(product: String): List<Machine>
    /** One-line hint under the title of the catalogue screen. */
    fun catalogHint(product: String): String

    // ---- Visit flow options. The defaults are the original flow (Naren, SIMTA); a company overrides only what differs. ----

    /** "Customer type" tiles on the Customer profile screen, in order. */
    val customerTypes: List<String> get() = listOf("OEM", "End User", "Agent", "Distributor")
    /** Pre-select Market exposure from the country on the Details screen (home country = Domestic, else International). */
    val autoMarketExposure: Boolean get() = false
    /** Hint under the "Product profile" title. */
    val productsHint: String get() = "Tap a product, then choose machines, makes and models. You can pick several."
    /** True: selecting any product reveals the description box (Visitor.otherDetail), not only [otherProduct]. */
    val describeAnyProduct: Boolean get() = false
    /** "Purpose of visit" tiles, in order. "Factory Visit" / "Follow-up" open the date-and-time popup (see Reminder). */
    val leadOutcomes: List<String> get() = listOf("Product Enquiry", "Sample Request", "General Enquiry", "Order", "Factory Visit", "Follow-up")
    /** > 0: once a purpose is picked, a description of at least this many characters is required (saved as discussion notes). */
    val outcomeDescriptionMin: Int get() = 0
    /** True: the follow-up date and time is set on the Done screen instead of as a "Purpose of visit" tile. */
    val followUpOnDone: Boolean get() = false
}
