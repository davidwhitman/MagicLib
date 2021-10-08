import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

//////////////////////
// VARIABLES TO CHANGE

object Variables {
    // Note: On Linux, if you installed Starsector into ~/something, you have to write /home/<user>/ instead of ~/
    val starsectorDirectory = "C:/Program Files (x86)/Fractal Softworks/Starsector"
    val modVersion = "0.35.0"
    val jarFileName = "MagicLib.jar"

// Scroll down and change the "dependencies" part of mod_info.json, if needed
// LazyLib is needed to use Kotlin, as it provides the Kotlin Runtime
}
//////////////////////

// Note: On Linux, use "${Variables.starsectorDirectory}" as core directory
val starsectorCoreDirectory = "${Variables.starsectorDirectory}/starsector-core"
val starsectorModDirectory = "${Variables.starsectorDirectory}/mods"

plugins {
    kotlin("jvm") version "1.3.60"
    java
}

version = Variables.modVersion

repositories {
    maven(url = uri("$projectDir/libs"))
    jcenter()
}

dependencies {
    val kotlinVersionInLazyLib = "1.4.21"

    implementation(fileTree("libs") { include("*.jar") })

    // Get kotlin sdk from LazyLib during runtime, only use it here during compile time
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersionInLazyLib")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersionInLazyLib")

    compileOnly(fileTree("$starsectorModDirectory/LazyLib/jars") { include("*.jar") })
    compileOnly(fileTree("$starsectorModDirectory/Console Commands/jars") { include("*.jar") })
    compileOnly(fileTree("$starsectorModDirectory/Vayra's Sector/jars") { include("*.jar") })
    compileOnly(fileTree("$starsectorModDirectory/Ship and Weapon Pack/jars") { include("*.jar") })

    // Starsector jars and dependencies
    implementation("starfarer:starfarer-api:1.0.0") // This grabs local files from the /libs folder, see `repositories` block.
    implementation(fileTree(starsectorCoreDirectory) {
        include(
            "*.jar"
        )
        exclude("starfarer.api.jar")
    })
}

tasks {
    named<Jar>("jar")
    {
        destinationDirectory.set(file("$rootDir/jars"))
        archiveFileName.set(Variables.jarFileName)
    }
}

sourceSets.main {
    java.setSrcDirs(listOf("src"))//emptyList<String>())
}

kotlin.sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
    resources.setSrcDirs(listOf("data"))
}

repositories {
    maven(url = uri("$projectDir/libs"))
    mavenCentral()
}
// Compile to Java 6 bytecode so that Starsector can use it
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.6"
}

// Compile to Java 6 bytecode so that Starsector can use it
java {
    sourceCompatibility = JavaVersion.VERSION_1_7
    targetCompatibility = JavaVersion.VERSION_1_7
}