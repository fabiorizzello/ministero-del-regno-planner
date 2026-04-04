import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.arrow.core)
            implementation(libs.konform)
            implementation(libs.voyager.navigator)
            implementation(libs.reorderable)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.mockk)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jna.platform)
            implementation(libs.jewel.int.ui.standalone)
            implementation(libs.jewel.int.ui.decorated.window)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.slf4j.api)
            implementation(libs.logback.classic)
            implementation(libs.koin.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.pdfbox)
            implementation(libs.jsoup)
            implementation(libs.kotlin.logging)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.java)
            implementation(libs.ktor.client.logging)
        }
    }
}

val jetbrainsRuntimeLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.JETBRAINS)
}

val packagingJavaHome = providers.gradleProperty("packaging.java.home")
    .orElse(providers.environmentVariable("JAVA_HOME"))
    .map(::File)
    .filter(File::isDirectory)

kover {
    reports {
        filters {
            excludes {
                packages(
                    "org.example.project.ui",
                    "org.example.project.db",
                    "org.example.project.core.cli",
                )
            }
        }
        total {
            html {
                onCheck = false
            }
        }
    }
}

val appVersion: String = project.property("app.version") as String
val numericVersion: String = appVersion.substringBefore("-")

val generateVersionProps by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/versionProps")
    outputs.dir(outputDir)
    inputs.property("appVersion", appVersion)
    doLast {
        val version = inputs.properties["appVersion"] as String
        val propsFile = outputDir.get().asFile.resolve("version.properties")
        propsFile.parentFile.mkdirs()
        propsFile.writeText("app.version=$version\n")
    }
}

kotlin.sourceSets.named("jvmMain") {
    resources.srcDir(generateVersionProps.map { it.outputs.files.singleFile })
}

sqldelight {
    databases {
        create("MinisteroDatabase") {
            packageName.set("org.example.project.db")
            dialect(libs.sqldelight.dialect.sqlite338)
            verifyMigrations.set(true)
        }
    }
}

tasks.register<JavaExec>("seedHistoricalDemoData") {
    description = "Genera dataset storico realistico (proclamatori, idoneita', catalogo, programmi e assegnazioni)"
    group = "application"
    javaLauncher.set(jetbrainsRuntimeLauncher)
    mainClass.set("org.example.project.core.cli.SeedHistoricalDemoDataKt")
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
        kotlin.jvm().compilations["main"].output.allOutputs
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("generateWolEfficaciCatalog") {
    description = "Genera un catalogo schemi JSON dal selettore settimane WOL (sezione EFFICACI NEL MINISTERO)"
    group = "application"
    javaLauncher.set(jetbrainsRuntimeLauncher)
    mainClass.set("org.example.project.core.cli.GenerateWolEfficaciCatalogKt")
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
        kotlin.jvm().compilations["main"].output.allOutputs
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("runUpdateDev") {
    description = "Avvia l'app con update locale rallentato per testare progresso download e velocita"
    group = "application"
    javaLauncher.set(jetbrainsRuntimeLauncher)
    mainClass.set("org.example.project.MainKt")
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
        kotlin.jvm().compilations["main"].output.allOutputs
    workingDir = rootProject.projectDir

    val useLocalBuild = providers.gradleProperty("updateDev.useLocalBuild").orElse("true")
    val localMsiPath = providers.gradleProperty("updateDev.localMsiPath").orNull
    val localVersion = providers.gradleProperty("updateDev.localVersion").orNull
    val chunkDelayMs = providers.gradleProperty("updateDev.chunkDelayMs").orElse("35")
    val chunkSizeBytes = providers.gradleProperty("updateDev.chunkSizeBytes").orElse("65536")
    val disableInstallerCache = providers.gradleProperty("updateDev.disableInstallerCache").orElse("true")

    systemProperty("ministero.update.useLocalBuild", useLocalBuild.get())
    systemProperty("ministero.update.devChunkDelayMs", chunkDelayMs.get())
    systemProperty("ministero.update.devChunkSizeBytes", chunkSizeBytes.get())
    systemProperty("ministero.update.devDisableInstallerCache", disableInstallerCache.get())

    if (!localMsiPath.isNullOrBlank()) {
        systemProperty("ministero.update.localMsiPath", localMsiPath)
    }
    if (!localVersion.isNullOrBlank()) {
        systemProperty("ministero.update.localVersion", localVersion)
    }
}

tasks.withType<JavaExec>().matching {
    it.name in setOf("run", "runDistributable", "runRelease", "runReleaseDistributable", "jvmRun")
}.configureEach {
    javaLauncher.set(jetbrainsRuntimeLauncher)
}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"
        packagingJavaHome.orNull?.let { javaHome = it.absolutePath }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "scuola-di-ministero"
            packageVersion = numericVersion
            description = "Pianificatore per il ministero del Regno"
            vendor = "Scuola di ministero"
            copyright = "© 2026 Scuola di ministero"

            windows {
                iconFile.set(project.file("launcher-icon.ico"))
                shortcut = true
                menuGroup = "Scuola di ministero"
            }
        }
    }
}

val releasePackageInputDir = layout.buildDirectory.dir("release-jpackage/input")
val releasePackageNativeDir = layout.buildDirectory.dir("release-jpackage/native")
val jvmRuntimeClasspath = configurations.named("jvmRuntimeClasspath")
val mainJarFile = tasks.named("jvmJar")
val externalUpdaterScript = layout.projectDirectory.file("src/jvmMain/resources/updater/external-updater.ps1")
val skikoRuntimeJar = jvmRuntimeClasspath.map { configuration ->
    configuration.files.firstOrNull { file ->
        file.name.startsWith("skiko-awt-runtime-windows-") && file.extension == "jar"
    }
}

val extractReleaseNativeFiles by tasks.registering(Sync::class) {
    description = "Estrae i native runtime necessari al packaging release"
    group = "distribution"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(skikoRuntimeJar.map { runtimeJar ->
        runtimeJar?.let { zipTree(it) } ?: files()
    }) {
        include("**/skiko-windows-*.dll", "**/icudtl.dat")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(releasePackageNativeDir)
}

tasks.register<Sync>("prepareReleasePackageInput") {
    description = "Prepara l'input jpackage per il bundling release con JBR completa"
    group = "distribution"
    dependsOn(mainJarFile, extractReleaseNativeFiles)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(mainJarFile)
    from(jvmRuntimeClasspath)
    from(releasePackageNativeDir)
    from(externalUpdaterScript) {
        into("resources")
    }
    into(releasePackageInputDir)
}
