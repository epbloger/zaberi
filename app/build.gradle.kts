plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.kapt")
}
val configuredBannerId = providers.gradleProperty("zaberiBannerId").orElse("").get()
require(configuredBannerId.isEmpty() || Regex("R-M-[0-9]+-[0-9]+").matches(configuredBannerId)) { "Invalid advertising block identifier" }
val configuredInterstitialId = providers.gradleProperty("zaberiInterstitialId").orElse("").get()
require(configuredInterstitialId.isEmpty() || Regex("R-M-[0-9]+-[0-9]+").matches(configuredInterstitialId)) { "Invalid interstitial block identifier" }
val storeAge = providers.gradleProperty("zaberiStoreAge").orElse("0").get()
require(storeAge in listOf("0", "6", "12", "16", "18")) { "Invalid RuStore age category" }
val demoInterstitial = providers.gradleProperty("zaberiDemoInterstitial").orElse("false").get().toBoolean()
// Distribution APKs are intentionally unsigned. The permanent key never enters CI.
val distributionCandidate = providers.gradleProperty("zaberiDistributionCandidate").orElse("false").get().toBoolean()
if (distributionCandidate) {
    require(configuredBannerId.isNotBlank() && configuredInterstitialId.isNotBlank() && configuredBannerId != configuredInterstitialId) { "A distribution candidate requires two distinct production ad blocks" }
}
android {
 namespace = "ru.epbloger.zaberi"
 compileSdk = 35
 defaultConfig {
  applicationId = "ru.epbloger.zaberi"
  minSdk = 26
  targetSdk = 35
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  versionCode = 111
  versionName = "1.0.3"
  buildConfigField("int","STORE_AGE",storeAge)
  buildConfigField("String","BANNER_ID","\""+configuredBannerId+"\"")
  buildConfigField("String","INTERSTITIAL_ID","\""+configuredInterstitialId+"\"")
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true; buildConfig = true }
 buildTypes {
  debug {
   buildConfigField("String","BANNER_ID","\"demo-banner-yandex\"")
   buildConfigField("String","INTERSTITIAL_ID",if(demoInterstitial)"\"demo-interstitial-yandex\"" else "\"\"")
  }
  release {
   isMinifyEnabled = false
   // Sign locally with Android SDK apksigner only.
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