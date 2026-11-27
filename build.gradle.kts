plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.mavenPublish) apply false
}

// One shared producer for both modules, including parallel Gradle builds.
val outputDir = file("ffmpeg/output")

val ffmpegSetup = tasks.register<Exec>("ffmpegSetup") {
    group = "build"
    description = "Build native media libraries for all Android ABIs"
    workingDir = file("ffmpeg")
    val sdkDirectory = providers.fileContents(layout.projectDirectory.file("local.properties"))
        .asText.orElse("").map { contents ->
            java.util.Properties().apply { load(contents.reader()) }.getProperty("sdk.dir", "")
        }.get().ifBlank {
            providers.environmentVariable("ANDROID_HOME")
                .orElse(providers.environmentVariable("ANDROID_SDK_ROOT")).getOrElse("")
        }
    environment("ANDROID_HOME", sdkDirectory)
    environment("ANDROID_NDK_VERSION", libs.versions.ndk.get())
    environment("ANDROID_CMAKE_VERSION", libs.versions.cmake.get())
    inputs.file(file("ffmpeg/setup.sh"))
    inputs.property("ndkVersion", libs.versions.ndk.get())
    inputs.property("cmakeVersion", libs.versions.cmake.get())
    outputs.dir(outputDir)
    commandLine("bash", "setup.sh")

    onlyIf {
        val needsBuild = !outputDir.exists() || outputDir.listFiles().isNullOrEmpty()
        if (!needsBuild) {
            println("FFmpeg native output already exists at ${outputDir.absolutePath}. Skipping setup.")
        }
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (isWindows && needsBuild) {
            throw GradleException(
                "Building FFmpeg from source is not supported on Windows (requires bash, meson, nasm, etc.).\n" +
                "Please run './gradlew downloadFfmpegPrebuilts' to download prebuilt native binaries from GitHub Releases, " +
                "or use GitHub Actions CI for native building."
            )
        }
        needsBuild
    }
}

tasks.register("downloadFfmpegPrebuilts") {
    group = "build"
    description = "Download prebuilt FFmpeg native libraries from GitHub Releases"
    doLast {
        val zipFile = file("ffmpeg/ffmpeg-output.zip")
        zipFile.parentFile.mkdirs()
        if (zipFile.exists()) {
            zipFile.delete()
        }

        val urlsToTry = listOf(
            "https://github.com/Darkstar085/nextlib-extended/releases/download/test/ffmpeg-output.zip",
            "https://github.com/Darkstar085/nextlib-extended/releases/latest/download/ffmpeg-output.zip"
        )

        var downloaded = false
        for (downloadUrl in urlsToTry) {
            println("Trying to download prebuilt FFmpeg binaries from $downloadUrl...")
            var connection: java.net.HttpURLConnection? = null
            try {
                connection = java.net.URI(downloadUrl).toURL().openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connect()
                if (connection.responseCode == 200) {
                    connection.inputStream.use { input ->
                        java.nio.file.Files.copy(input, zipFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                    downloaded = true
                    break
                }
            } catch (_: Exception) {
                // Try next URL
            } finally {
                connection?.disconnect()
            }
        }

        if (downloaded) {
            val stagingDir = file("ffmpeg/.output-staging")
            try {
                if (stagingDir.exists()) {
                    stagingDir.deleteRecursively()
                }
                project.copy {
                    from(zipTree(zipFile))
                    into(stagingDir)
                }
                if (outputDir.exists()) {
                    println("Removing existing prebuilt FFmpeg binaries at ${outputDir.absolutePath}...")
                    if (!outputDir.deleteRecursively()) {
                        throw GradleException("Failed to remove existing FFmpeg output directory: ${outputDir.absolutePath}")
                    }
                }
                val topLevelEntries = stagingDir.listFiles().orEmpty()
                val extractedRoot = topLevelEntries.singleOrNull()
                    ?.takeIf { it.isDirectory && it.name == "output" }
                    ?: stagingDir
                project.copy {
                    from(extractedRoot)
                    into(outputDir)
                }
                println("Prebuilt FFmpeg binaries successfully downloaded and extracted to ${outputDir.absolutePath}")
            } catch (e: Exception) {
                println("Failed to extract downloaded zip: ${e.message}")
            } finally {
                stagingDir.deleteRecursively()
                zipFile.delete()
            }
        } else {
            println("Failed to download prebuilt FFmpeg binaries from all release URLs.")
            println("Please ensure a release or pre-release with 'ffmpeg-output.zip' exists on GitHub, or place ffmpeg/output manually.")
        }
    }
}

val libraryVersion = libs.versions.nextlibExtended.get()
version = libraryVersion

subprojects {
    version = libraryVersion
    plugins.withId(rootProject.libs.plugins.mavenPublish.get().pluginId) {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral()
            val hasSigningProps = System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null ||
                    project.hasProperty("signing.keyId") ||
                    project.hasProperty("signingInMemoryKey")
            if (hasSigningProps) {
                signAllPublications()
            }
            coordinates(
                groupId = "io.github.darkstar085",
                artifactId = property("POM_ARTIFACT_ID") as String,
                version = libraryVersion
            )

            pom {
                name = property("POM_NAME") as String
                description = property("POM_DESCRIPTION") as String
                url = "https://github.com/Darkstar085/nextlib-extended"

                licenses {
                    license {
                        name = "GNU General Public License v3.0"
                        url = "https://www.gnu.org/licenses/gpl-3.0.html"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "Darkstar085"
                        name = "S I P U N"
                    }
                }
                scm {
                    url = "https://github.com/Darkstar085/nextlib-extended"
                }
            }
        }
    }
}
