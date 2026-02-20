import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()
    jvmToolchain(17)

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
            implementation(libs.voyager.navigator)
            implementation(libs.reorderable)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
        }
    }
}

tasks.register<JavaExec>("seedDatabase") {
    description = "Popola la tabella part_type con i dati da data/part-types.json"
    group = "application"
    mainClass.set("org.example.project.core.cli.SeedDatabaseKt")
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
        kotlin.jvm().compilations["main"].output.allOutputs
    workingDir = rootProject.projectDir
}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "efficaci-nel-ministero"
            packageVersion = numericVersion
            description = "Pianificatore per il ministero del Regno"
            vendor = "Efficaci Nel Ministero"
            copyright = "© 2025 Efficaci Nel Ministero"

            windows {
                iconFile.set(project.file("launcher-icon.ico"))
                shortcut = true
                menuGroup = "Efficaci Nel Ministero"
            }
        }
    }
}
