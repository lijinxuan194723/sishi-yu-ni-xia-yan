plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="cn.sishiyuni.designsystem"; compileSdk=35
 defaultConfig { minSdk=26 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17"; freeCompilerArgs+=listOf("-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi","-opt-in=androidx.compose.material3.ExperimentalMaterial3Api") }
 buildFeatures { compose=true }
}
dependencies {
 api(project(":core")); api(platform("androidx.compose:compose-bom:2025.05.01"))
 api("androidx.compose.ui:ui"); api("androidx.compose.foundation:foundation"); api("androidx.compose.animation:animation"); api("androidx.compose.material3:material3"); api("androidx.compose.material:material-icons-extended")
 api("androidx.compose.ui:ui-tooling-preview"); api("androidx.activity:activity-compose:1.10.1"); api("io.coil-kt:coil-compose:2.7.0")
}
