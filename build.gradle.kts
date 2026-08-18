// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}

import java.io.File

// This project lives on an ExFAT volume (/Volumes/Parsonal). ExFAT has no
// extended-attribute support, so macOS creates AppleDouble (._) sidecar files on
// every file write. Those stray files make Gradle's directory deletion fail
// during `clean`, incremental resource merges, and kapt stub regeneration,
// surfacing as "Unable to delete directory '.../classpath-snapshot'". Keep all
// build outputs on the local (APFS) disk instead.
// Disable with `-PuseLocalBuildDir=false` if you move the project to a native disk.
val useLocalBuildDir = (findProperty("useLocalBuildDir") ?: "true").toString().toBoolean()

if (useLocalBuildDir) {
    val localBuildRoot = File(System.getProperty("user.home"), ".diu-transport-build")
    allprojects {
        layout.buildDirectory.set(File(localBuildRoot, name.replace(' ', '_')))
    }
}