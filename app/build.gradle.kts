plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.kapt")
}
val configuredBannerId = providers.gradleProperty("zaberiBannerId").orElse("").get()
require(configuredBannerId.isEmpty() || Regex("R-M-[0-9]+-[0-9]+").matches(configuredBannerId)) { "Invalid advertising block identifier" }
// Release credentials are supplied only by the local environment.
val signingEnv = listOf("ZABERI_KEYSTORE_PATH","ZABERI_STORE_PASSWORD","ZABERI_KEY_ALIAS","ZABERI_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
val signingConfigured = signingEnv.values.all { !it.isNullOrBlank() }
require(signingEnv.values.none { !it.isNullOrBlank() } || signingConfigured) {
    "Supply all four release signing environment variables, or none for unsigned checks"
}
val publishableRelease = providers.gradleProperty("zaberiPublishableRelease").orElse("false").get().toBoolean()
if (publishableRelease) {
    require(signingConfigured) { "A publication build requires a private release signing key" }
    require(configuredBannerId.isNotBlank()) { "A publication build requires the configured production ad block" }
}
android {
 namespace = "ru.epbloger.zaberi"
 compileSdk = 35
 defaultConfig {
  applicationId = "ru.epbloger.zaberi"
  minSdk = 26
  targetSdk = 35
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  versionCode = 106
  versionName = "1.0.0-rc7"
  buildConfigField("String","BANNER_ID","\""+configuredBannerId+"\"")
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true; buildConfig = true }
 if (signingConfigured) {
  signingConfigs {
   create("zaberiRelease") {
    storeFile = file(requireNotNull(signingEnv["ZABERI_KEYSTORE_PATH"]))
    storePassword = signingEnv["ZABERI_STORE_PASSWORD"]
    keyAlias = signingEnv["ZABERI_KEY_ALIAS"]
    keyPassword = signingEnv["ZABERI_KEY_PASSWORD"]
   }
  }
 }
 buildTypes {
  debug { buildConfigField("String","BANNER_ID","\"demo-banner-yandex\"") }
  release {
   isMinifyEnabled = false
   if (signingConfigured) signingConfig = signingConfigs.getByName("zaberiRelease")
  }
 }
}
dependencies {
 implementation("com.yandex.android:mobileads:8.7.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 kapt("androidx.room:room-compiler:2.6.1")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test:rules:1.6.1")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:core-ktx:1.6.1")
}
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }