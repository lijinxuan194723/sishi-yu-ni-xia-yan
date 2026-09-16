plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.kapt") }
android { namespace="cn.sishiyuni.core"; compileSdk=35
 defaultConfig { minSdk=26; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17"; freeCompilerArgs+=listOf("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi") }
 testOptions { unitTests.isReturnDefaultValues=true }
}
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
dependencies {
 api("androidx.core:core-ktx:1.16.0")
 api("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
 api("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
 api("androidx.room:room-runtime:2.7.2"); api("androidx.room:room-ktx:2.7.2"); kapt("androidx.room:room-compiler:2.7.2")
 api("androidx.datastore:datastore-preferences:1.1.7")
 api("androidx.work:work-runtime-ktx:2.10.1")
 api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
 api("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2"); testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2"); testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
 androidTestImplementation("androidx.room:room-testing:2.7.2"); androidTestImplementation("androidx.test.ext:junit:1.2.1"); androidTestImplementation("androidx.test:runner:1.6.2")
}
