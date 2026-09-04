@file:OptIn(StonecutterExperimentalAPI::class)

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("dev.kikugie.loom-back-compat")
	kotlin("jvm")
	`maven-publish`
}

val mcVersion: String = sc.properties["deps.minecraft"]
val loaderVersion: String = sc.properties["deps.fabric_loader"]
val fabricVersion: String = sc.properties["deps.fabric_api"]
val modmenuVersion: String = sc.properties["deps.modmenu"]
val yaclVersion: String = sc.properties["deps.yacl"]
val modVersion: String = sc.properties["mod.version"]
val mcDep: String = sc.properties["mod.mc_dep"]
val loaderDep: String = sc.properties["mod.loader_dep"]

val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21
val kotlinTarget = if (sc.current.parsed >= "26.1") JvmTarget.JVM_25 else JvmTarget.JVM_21

version = modVersion
group = sc.properties["mod.group"] as String

base {
	archivesName.set(sc.properties["mod.id"] as String)
}

// Lint gate (rule ledger: gradle/detekt/detekt.yml), registered on the active version project
// only so `./gradlew detekt` analyzes src/main/kotlin once. Runs the detekt CLI on the JDK 21
// toolchain via JavaExec because detekt 1.23.8's embedded parser crashes on the JDK 25 runtime
// the 26.x builds use; analysis only parses sources, so the toolchain version is irrelevant.
if (sc.current.isActive) {
	val detektCli = configurations.create("detektCli")
	dependencies {
		"detektCli"("io.gitlab.arturbosch.detekt:detekt-cli:1.23.8")
	}
	tasks.register<JavaExec>("detekt") {
		group = "verification"
		description = "Runs detekt over src/main/kotlin (rule ledger: gradle/detekt/detekt.yml)."
		classpath = detektCli
		mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
		javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
		setArgs(listOf(
			"--input", rootDir.resolve("src/main/kotlin").path,
			"--config", rootProject.file("gradle/detekt/detekt.yml").path,
			"--report", "html:${layout.buildDirectory.file("reports/detekt/report.html").get().asFile.path}",
		))
	}
}

loom {
	runs {
		named("client") {
			property("devauth.enabled", "true")
			property("devauth.account", "main")
			// 26.3's new SDL windowing fails EGL init on native Wayland (EGL_BAD_DISPLAY); default
			// to X11 (XWayland) on Linux unless the developer chose a video driver explicitly.
			if (sc.current.parsed >= "26.3" && System.getProperty("os.name").startsWith("Linux")) {
				environmentVariable("SDL_VIDEODRIVER", System.getenv("SDL_VIDEODRIVER") ?: "x11")
			}
		}
	}
}

repositories {
	mavenCentral()
	maven {
		name = "Fabric"
		url = uri("https://maven.fabricmc.net/")
	}
	maven {
		url = uri("https://api.modrinth.com/maven")
	}
	maven {
		name = "DevAuth"
		url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
	}
	maven {
		name = "TerraformersMC"
		url = uri("https://maven.terraformersmc.com/releases/")
	}
	maven {
		name = "Xander Maven"
		url = uri("https://maven.isxander.dev/releases")
	}
}

dependencies {
	// To change the versions see the stonecutter.properties.toml file
	minecraft("com.mojang:minecraft:$mcVersion")
	// Applies Mojang mappings only on obfuscated versions (loom-back-compat no-ops on 26.x)
	loomx.applyMojangMappings()
	modImplementation("net.fabricmc:fabric-loader:$loaderVersion")

	// Fabric API
	modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

	// Fabric Language Kotlin
	modImplementation("net.fabricmc:fabric-language-kotlin:1.13.13+kotlin.2.4.10")

	// ModMenu integration — compile-only for the published jar, but present in dev runtime
	// so the ModMenu config entrypoint can be tested with runClient.
	// modLocalRuntime is skipped on 26.3: ModMenu 20.0.1 declares <26.3-alpha.3, so the dev
	// client refuses to start until a compatible build is published.
	modCompileOnly("com.terraformersmc:modmenu:$modmenuVersion")
	if (sc.current.parsed < "26.3") modLocalRuntime("com.terraformersmc:modmenu:$modmenuVersion")

	// YACL config screen — external dependency (not bundled: YACL officially discourages
	// jar-in-jar as it's heavy and usually already present in modpacks); declared in
	// fabric.mod.json "depends"
	modImplementation("dev.isxander:yet-another-config-lib:$yaclVersion")

	// DevAuth: authenticate with a real Microsoft account in dev environment
	modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")

	// Apache HttpClient for Mojang API skin upload
	implementation("org.apache.httpcomponents:httpclient:4.5.13")
	implementation("org.apache.httpcomponents:httpmime:4.5.13")
	implementation("org.apache.httpcomponents:httpcore:4.4.15")
	include("org.apache.httpcomponents:httpclient:4.5.13")
	include("org.apache.httpcomponents:httpmime:4.5.13")
	include("org.apache.httpcomponents:httpcore:4.4.15")
	include(implementation("commons-logging:commons-logging:1.2")!!)
}

tasks.processResources {
	inputs.property("version", project.version)
	inputs.property("mc_dep", mcDep)
	inputs.property("loader_dep", loaderDep)

	filesMatching("fabric.mod.json") {
		expand("version" to project.version, "mc_dep" to mcDep, "loader_dep" to loaderDep)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set(requiredJava.majorVersion.toInt())
}

java {
	withSourcesJar()

	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
}

kotlin {
	compilerOptions {
		jvmTarget.set(kotlinTarget)
	}
}

tasks.jar {
	from("LICENSE") {
		rename { "${it}_${base.archivesName.get()}" }
	}
}

// configure the maven publication
publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	repositories {
		// Add repositories to publish to here.
	}
}
