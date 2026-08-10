pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		maven {
			name = "KikuGie Releases"
			url = uri("https://maven.kikugie.dev/releases")
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	// Auto-provisions the right JDK toolchain per version project (21 vs 25)
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("dev.kikugie.stonecutter") version "0.9.7"
	// Picks the remap vs no-remap loom variant per version project (loom version via loomx.loom_version)
	id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
	create(rootProject) {
		versions("1.21.11" to "1.21.11", "26.1.2" to "26.1.2", "26.2" to "26.2")
		vcsVersion = "26.2"
	}
}
