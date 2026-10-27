import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    dependencies {
        classpath(libs.recloudstream.gradle)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin) apply false
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // when running through github workflow, GITHUB_REPOSITORY should contain current repository name
        // you can modify it to use other git hosting services, like gitlab
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/karayel90/cloudplug")
        authors = listOf("karayel90")
    }

    android {
        // Not: `namespace` her modülün kendi build.gradle.kts dosyasında
        // tanımlanır (ör. tr.tabii) — tek modül varsayılanını
        // kullanmak ikinci bir provider eklenince çakışma yaratıyor.

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            // The Cloudstream library is compiled with JVM target 11.
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        sourceSets {
            getByName("main") {
                java.srcDir("${rootProject.projectDir}/common/src/main/kotlin")
            }
            getByName("test") {
                java.srcDir("${rootProject.projectDir}/common/src/test/kotlin")
            }
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11) // Required
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions",
                    "-opt-in=com.lagradost.cloudstream3.Prerelease"
                )
            }
        }
    }

    dependencies {
        val implementation by configurations
        val libs = rootProject.libs

        // If the task is specifically to compile the app then use the stubs, otherwise use the library.
        val cloudstream by configurations
        cloudstream(libs.cloudstream3)

        // these dependencies can include any of those which are added to the app,
        // but you dont need to include any of them if you dont need them
        // https://github.com/recloudstream/cloudstream/blob/master/app/build.gradle
        implementation(kotlin("stdlib")) // adds standard kotlin features
        implementation(libs.nicehttp) // http library
        implementation(libs.kotlinx.coroutines.core) // coroutines for fetchBypass
        implementation(libs.jsoup) // html parser
        implementation(libs.jackson) // json parser (NextData)

        val testImplementation by configurations
        testImplementation(libs.junit)
    }

    tasks.withType<Test>().configureEach {
        // Lokal calistirmada test hatasi build'i durdurur; CI'da (build.yml)
        // bu adim `continue-on-error: true` ile ENGELLENIR: Cloudstream kutuphane
        // anlik gorsu rolling release'den geldigi icin gecici bir test hatasi
        // .cs3 yayimlanmasini durdurmaz. Yani: lokalde bloklar, CI'da yalnizca
        // adim basarisiz gosterir ama job devam eder.
        ignoreFailures = false
    }
}

tasks.register<Delete>("clean") {
    delete(getLayout().buildDirectory)
}

tasks.register<TestReport>("testReport") {
    description = "Aggregate all test results as a HTML report"
    group = "Build"
    destinationDirectory = layout.buildDirectory.dir("reports/allTests")
    // findByName: testReleaseUnitTest olmayan bir alt projede configuration
    // asamasinda patlamasin (2. provider eklenince kirilmaz).
    testResults.from(subprojects.mapNotNull { project -> project.tasks.findByName("testReleaseUnitTest") })
}
