import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("org.jetbrains.compose") version "1.7.0"
}

// Versión: 1.0.<número de compilación de GitHub>
val appVersion = "2.0." + (System.getenv("RUN_NUMBER") ?: "0")
version = appVersion

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("uk.co.caprica:vlcj:4.8.3")
}

kotlin { jvmToolchain(17) }

// Número de versión disponible en el código (BuildInfo.VERSION)
val genDir = layout.buildDirectory.dir("generated/buildinfo")
val genBuildInfo by tasks.registering {
    outputs.dir(genDir)
    inputs.property("v", appVersion)
    doLast {
        val f = genDir.get().file("com/noirplus/desktop/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package com.noirplus.desktop\n\nobject BuildInfo { const val VERSION = \"$appVersion\" }\n")
    }
}
sourceSets["main"].kotlin.srcDir(genDir)
tasks.named("compileKotlin") { dependsOn(genBuildInfo) }

compose.desktop {
    application {
        mainClass = "com.noirplus.desktop.MainKt"
        jvmArgs += listOf("-Xmx1024m", "-Dfile.encoding=UTF-8", "-Dsun.java2d.d3d=true")
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "NoirPlus"
            packageVersion = appVersion
            description = "Noir+ — tu IPTV en el PC"
            vendor = "Noir+"
            copyright = "Noir+"
            includeAllModules = true
            windows {
                iconFile.set(project.file("packaging/icon.ico"))
                menu = true
                menuGroup = "Noir+"
                shortcut = true
                perUserInstall = true
                dirChooser = false
                // Fijo: permite que cada versión nueva se instale encima de la anterior
                upgradeUuid = "6f1d1c2e-8a43-4f6b-9a77-2c5e0b7d4e11"
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
