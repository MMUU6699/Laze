import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.vela.core"
    testOptions {
        // The on-demand obf harnesses (ObfSpeedLimitProbeTest, ObfRoadNamesProbeTest) run the real
        // engine on the JVM, and its android.util.Log lines would otherwise throw "not mocked".
        unitTests.isReturnDefaultValues = true
    }
    compileSdk = 37
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        val localProperties = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) {
                file.inputStream().use { load(it) }
            }
        }

        val supabaseUrl = (project.findProperty("supabaseUrl") as? String)
            ?: localProperties.getProperty("supabase.url")
            ?: "https://dwubxdddfcfeanrppxvy.supabase.co"

        val supabaseAnonKey = (project.findProperty("supabaseAnonKey") as? String)
            ?: localProperties.getProperty("supabase.anon_key")
            ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR3dWJ4ZGRkZmNmZWFucnBweHZ5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE2Mjc1MDMsImV4cCI6MjEwNzIwMzUwM30.EtqWZXyh98FBTw5jdQkf5VC-1NlNAmPllLEw09721_c"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.rhino.runtime)

    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.1")
    // OsmAnd obf routing engine (ObfRouteEngine): the router + binary obf reader as plain Java
    // jars, vendored from OsmAndMapCreator's lib (GPLv3, same license as Vela). Gitignored like
    // the sherpa AAR - CI fetches them from the `obf-runtime` infra release; locally copy them in
    // (see CLAUDE.md). Everything else the router needs is already on Android (org.json,
    // kotlin-stdlib) or in this module (kotlinx-serialization). commons-logging + kxml2 are the
    // two desktop assumptions Android does NOT satisfy: OsmAnd logs through commons-logging and
    // instantiates org.kxml2.io.KXmlParser directly (present on a desktop classpath, not visible
    // to apps on modern Android) - both device-caught on the release canary, 2026-07-23.
    // kxml2-vela.jar is upstream kxml2 2.3.0 with its bundled org/xmlpull/** REMOVED - the stock
    // Maven jar duplicates the platform's XmlPullParser interfaces and R8 hard-fails on the
    // library/program split ("Library class android.content.res.XmlResourceParser implements
    // program class org.xmlpull.v1.XmlPullParser").
    // osmand-java.jar comes through :osmand-shaded (its protobuf relocated, so Cronet's can coexist).
    implementation(project(mapOf("path" to ":osmand-shaded", "configuration" to "shadow")))
    implementation(files("libs/osmand-shared-jvm.jar", "libs/gnu-trove-osmand.jar", "libs/kxml2-vela.jar"))
    implementation("commons-logging:commons-logging:1.4.0")

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    // The real org.json for the on-demand obf harness (ObfSpeedLimitProbeTest): the engine reads
    // its region index with JSONArray, and Android's unit-test jar only stubs it.
    testImplementation("org.json:json:20240303")
}

// Forward the trip-audit harness property into the TEST JVM (see NavReplayTest.auditSharedTripLog):
// `-DvelaTrip=…` on the command line sets a GRADLE-daemon property, which the forked test JVM does
// NOT inherit — without this the documented audit command silently skipped the test every time.
tasks.withType<Test>().configureEach {
    System.getProperty("velaTrip")?.let { systemProperty("velaTrip", it) }
    System.getProperty("velaSeg")?.let { systemProperty("velaSeg", it) }
    System.getProperty("velaProbe")?.let { systemProperty("velaProbe", it) }
    System.getProperty("velaObf")?.let { systemProperty("velaObf", it) }
    System.getProperty("velaCells")?.let { systemProperty("velaCells", it) }
    System.getProperty("velaLive")?.let { systemProperty("velaLive", it) }
    System.getProperty("velaStudy")?.let { systemProperty("velaStudy", it) }
    System.getProperty("velaOne")?.let { systemProperty("velaOne", it) }
    System.getProperty("velaLines")?.let { systemProperty("velaLines", it) }
    System.getProperty("velaNudge")?.let { systemProperty("velaNudge", it) }
    System.getProperty("velaFeedProbe")?.let { systemProperty("velaFeedProbe", it) }
    // Painted-roads bake (PaintedRoadsBakeTest): a whole state's streets need a big heap.
    System.getProperty("velaPaintIn")?.let { systemProperty("velaPaintIn", it); maxHeapSize = "20g" }
    System.getProperty("velaPaintOut")?.let { systemProperty("velaPaintOut", it) }
}
