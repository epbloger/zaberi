plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.kapt")
}
android {
 namespace = "ru.epbloger.zaberi"
 compileSdk = 35
 defaultConfig {
  applicationId = "ru.epbloger.zaberi"
  minSdk = 26
  targetSdk = 35
  versionCode = 4
  versionName = "0.4.0"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
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
}
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }