pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Not: google.com/mavenCentral'a bazi yapilar (lint araclari)
        // ara sira 404 veriyor; yansilar (Aliyun + repo1) bu yuzden
        // once sira tutar.
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://repo1.maven.org/maven2")
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "CloudstreamPlugins"

// This file sets what projects are included. All new projects should get automatically included unless specified in "disabled" variable.

val disabled = listOf<String>()

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
