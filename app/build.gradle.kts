plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val generatedFlipDoorRes = layout.buildDirectory.dir("generated/flipdoorRes")
val prepareFlipDoorLauncherIcon by tasks.registering(Copy::class) {
 from(rootProject.file("assets/FlipDoor.png"))
 into(generatedFlipDoorRes.map { it.dir("mipmap-nodpi") })
 rename { "ic_launcher.png" }
}
tasks.named("preBuild").configure { dependsOn(prepareFlipDoorLauncherIcon) }

android {
 sourceSets.getByName("main").res.srcDir(generatedFlipDoorRes)
 namespace="com.anuragrajpandey.smarttravel"; compileSdk=35
 defaultConfig { applicationId="com.anuragrajpandey.smarttravel"; minSdk=24; targetSdk=35; versionCode=3; versionName="1.1.0"
  // Optional: official Google Maps Embed API key (free). Leave empty to use the keyless embed.
  val mapsKey = (project.findProperty("MAPS_EMBED_KEY") as String?) ?: ""
  buildConfigField("String", "MAPS_EMBED_KEY", "\"" + mapsKey + "\"")
 }
 buildTypes { release { isMinifyEnabled=false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
 buildFeatures { buildConfig=true }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("com.google.android.material:material:1.12.0")
 implementation("com.google.mlkit:translate:17.0.3")
}
