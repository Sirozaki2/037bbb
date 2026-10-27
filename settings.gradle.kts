pluginManagement {
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            // Kotlin 2.4 metadata requires D8/R8 9.1.29 or newer.
            // Load it before AGP's bundled D8/R8, following R8's override guidance.
            classpath("com.android.tools:r8:9.1.31")
        }
    }
}

rootProject.name = "CloudstreamPlugins"

include(":ExampleProvider")

// This file sets what projects are included.
// All new projects should get automatically included unless specified in the "disabled" variable.

// Retired providers: keep source/fixtures for reference, omit from published plugins.
val disabled = listOf("Movie2Free", "FWIPTV")

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}

// To only include a single project, comment out the previous lines (except the first one), and include your plugin like so:
// include("PluginName")
