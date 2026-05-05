import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.example.barandgrillownerpanel"
version = "1.0-SNAPSHOT"


dependencies {
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    
    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.core)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.gotrue)
    implementation("io.github.jan-tennert.supabase:storage-kt")
    
    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // Excel Export
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // PDF Reports
    implementation("org.apache.pdfbox:pdfbox:3.0.1")
}

compose.desktop {
    application {
        mainClass = "com.example.barandgrillownerpanel.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "BarAndGrillOwnerPanel"
            packageVersion = "1.0.0"
        }
        // Default run uses Bar & Grill flavor
        jvmArgs += listOf("-DappFlavor=barandgrill")
    }
}

// ─── Flavor-specific run tasks ──────────────────────────────────────────────

tasks.register<JavaExec>("runBarAndGrill") {
    group = "application"
    description = "Run the Bar & Grill POS"
    classpath = tasks.named<JavaExec>("run").get().classpath
    mainClass.set("com.example.barandgrillownerpanel.MainKt")
    jvmArgs("-DappFlavor=barandgrill")
    dependsOn("compileKotlin")
}

tasks.register<JavaExec>("runLicoG") {
    group = "application"
    description = "Run the Lico G POS"
    classpath = tasks.named<JavaExec>("run").get().classpath
    mainClass.set("com.example.barandgrillownerpanel.MainKt")
    jvmArgs("-DappFlavor=licog")
    dependsOn("compileKotlin")
}

tasks.register<JavaExec>("runStopAndShop") {
    group = "application"
    description = "Run the Stop and Shop POS"
    classpath = tasks.named<JavaExec>("run").get().classpath
    mainClass.set("com.example.barandgrillownerpanel.MainKt")
    jvmArgs("-DappFlavor=stopandshop")
    dependsOn("compileKotlin")
}
