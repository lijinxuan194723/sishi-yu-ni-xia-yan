plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace="cn.sishiyuni.app"; compileSdk=35
 defaultConfig { applicationId="cn.sishiyuni.nativeapp"; minSdk=26; targetSdk=35; versionCode=902064; versionName="2.0.10-native.5"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 sourceSets["main"].assets.srcDir("../assets")
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17"; freeCompilerArgs+=listOf("-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi","-opt-in=androidx.compose.material3.ExperimentalMaterial3Api") }
 buildFeatures { compose=true; buildConfig=true }
 val externalKey = providers.environmentVariable("LUKE_KEYSTORE").orNull
 signingConfigs {
  if (externalKey != null) create("localInstall") {
   storeFile=file(externalKey)
   storePassword=providers.environmentVariable("LUKE_STORE_PASSWORD").get()
   keyAlias=providers.environmentVariable("LUKE_KEY_ALIAS").get()
   keyPassword=providers.environmentVariable("LUKE_KEY_PASSWORD").get()
  }
 }
 buildTypes {
  release { isMinifyEnabled=true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") }
  create("sideload") {
   initWith(getByName("release"))
   applicationIdSuffix=".preview"
   signingConfig=signingConfigs.getByName(if (externalKey != null) "localInstall" else "debug")
   isDebuggable=false
   matchingFallbacks+=listOf("release")
  }
 }
 packaging { resources.excludes+=setOf("META-INF/AL2.0","META-INF/LGPL2.1") }
}
dependencies {
 implementation(project(":core")); implementation(project(":designsystem"))
 listOf("home","chat","moments","plans","journal","timer","settings").forEach { implementation(project(":feature:$it")) }
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.metrics:metrics-performance:1.0.0-beta02")
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
