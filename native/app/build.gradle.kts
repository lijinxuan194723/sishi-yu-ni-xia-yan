plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace="cn.sishiyuni.app"; compileSdk=35
 defaultConfig { applicationId="cn.sishiyuni.nativeapp"; minSdk=26; targetSdk=35; versionCode=902060; versionName="2.0.10-native.1"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17"; freeCompilerArgs+=listOf("-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi","-opt-in=androidx.compose.material3.ExperimentalMaterial3Api") }
 buildFeatures { compose=true; buildConfig=true }
 buildTypes { release { isMinifyEnabled=true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
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
