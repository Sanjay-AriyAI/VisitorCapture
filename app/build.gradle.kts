import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
// SMTP settings for the automatic thank-you mail live in local.properties (git-ignored), never in source:
//   smtp.host=smtp.gmail.com
//   smtp.port=587            (587 = STARTTLS, 465 = SSL)
//   smtp.user=visitors@yourcompany.com
//   smtp.password=<app password>
//   smtp.from=visitors@yourcompany.com
//   smtp.fromName=Naren Group
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun smtp(key: String, default: String = "") = "\"" + (localProps.getProperty("smtp.$key") ?: default).replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.visitorcapture"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.visitorcapture"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SMTP_HOST", smtp("host"))
        buildConfigField("int", "SMTP_PORT", (localProps.getProperty("smtp.port") ?: "587").trim())
        buildConfigField("String", "SMTP_USER", smtp("user"))
        buildConfigField("String", "SMTP_PASSWORD", smtp("password"))
        buildConfigField("String", "SMTP_FROM", smtp("from"))
        buildConfigField("String", "SMTP_FROM_NAME", smtp("fromName", "Naren Group"))
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/LICENSE", "META-INF/NOTICE",
            "META-INF/DEPENDENCIES", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // business-card capture: live edge detection, auto-capture, auto-crop + perspective fix (model via Play services)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // staff QR login: live camera preview + offline QR decoding
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // automatic thank-you e-mail: background job + SMTP
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    testImplementation("junit:junit:4.13.2")
}
