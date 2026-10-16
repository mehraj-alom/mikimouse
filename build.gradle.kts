plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

extra["androidCompileSdk"] = libs.versions.compileSdk.get().toInt()
extra["androidTargetSdk"] = libs.versions.targetSdk.get().toInt()
extra["androidMinSdk"] = libs.versions.minSdk.get().toInt()

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
