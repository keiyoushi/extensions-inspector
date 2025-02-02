import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask
import java.io.BufferedReader

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.shadowjar)
    alias(libs.plugins.buildconfig)
}

dependencies {
    implementation(libs.bundles.okhttp)
    implementation(libs.bundles.tachiyomi)

    // AndroidCompat
    implementation(project(":AndroidCompat"))
    implementation(project(":AndroidCompat:Config"))
}

@Suppress("PropertyName")
val MainClass = "inspector.MainKt"

application {
    mainClass.set(MainClass)
}

sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
        }
    }
}

// should be bumped with each stable release
val inspectorVersion = "v1.4.8"

// counts commit count on master
val inspectorRevision = runCatching {
    System.getenv("ProductRevision") ?: Runtime
        .getRuntime()
        .exec(arrayOf("git", "rev-list", "HEAD", "--count"))
        .let { process ->
            process.waitFor()
            val output = process.inputStream.use {
                it.bufferedReader().use(BufferedReader::readText)
            }
            process.destroy()
            "r" + output.trim()
        }
}.getOrDefault("r0")

val String.wrapped get() = """"$this""""

buildConfig {
    className("BuildConfig")
    packageName("inspector")

    useKotlinOutput()

    buildConfigField("String", "NAME", rootProject.name.wrapped)
    buildConfigField("String", "VERSION", inspectorVersion.wrapped)
    buildConfigField("String", "REVISION", inspectorRevision.wrapped)
}

tasks {
    shadowJar {
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to MainClass,
                    "Implementation-Title" to rootProject.name,
                    "Implementation-Vendor" to "The Keiyoushi Project",
                    "Specification-Version" to inspectorVersion,
                    "Implementation-Version" to inspectorRevision
                )
            )
        }
        archiveBaseName.set(rootProject.name)
        archiveVersion.set(inspectorVersion)
        archiveClassifier.set(inspectorRevision)
    }

    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
                "-opt-in=kotlin.io.path.ExperimentalPathApi",
            )
        }
    }

    test {
        useJUnit()
    }

    withType<ShadowJar> {
        destinationDirectory.set(File("$rootDir/server/build"))
        dependsOn("formatKotlin", "lintKotlin")
    }

    named("run") {
        dependsOn("formatKotlin", "lintKotlin")
    }

    withType<LintTask> {
        source(files("src/kotlin"))
    }

    withType<FormatTask> {
        source(files("src/kotlin"))
    }

    withType<ProcessResources> {
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
