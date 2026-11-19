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

    /** Top-level products on the "Product profile" screen, in order. */
    val products: List<String>
    /** The product whose selection reveals the "Tell us more" free-text box (stored in Visitor.otherDetail); null = none. */
    val otherProduct: String?
    fun productIcon(product: String): ImageVector
    /** Machines under [product]; an empty list makes the product a single tick (no catalogue screen). */
    fun machinesFor(product: String): List<Machine>
    /** One-line hint under the title of the catalogue screen. */
    fun catalogHint(product: String): String
}
