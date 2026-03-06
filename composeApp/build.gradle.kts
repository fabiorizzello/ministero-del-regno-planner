import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
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
            implementation(libs.mockk)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
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
            implementation(libs.jna.platform)
            implementation(libs.kotlin.logging)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.java)
            implementation(libs.ktor.client.logging)
        }
    }
}

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
    mainClass.set("org.example.project.core.cli.SeedHistoricalDemoDataKt")
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
        kotlin.jvm().compilations["main"].output.allOutputs
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("generateWolEfficaciCatalog") {
    description = "Genera un catalogo schemi JSON dal selettore settimane WOL (sezione EFFICACI NEL MINISTERO)"
    group = "application"
    mainClass.set("org.example.project.core.cli.GenerateWolEfficaciCatalogKt")
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
        kotlin.jvm().compilations["main"].output.allOutputs
    workingDir = rootProject.projectDir
}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

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
