@file:OptIn(StonecutterExperimentalAPI::class)

import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("dev.kikugie.loom-back-compat")
	kotlin("jvm")
	kotlin("plugin.serialization") version "2.4.10"
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

	// Per-version lint gate: stonecutter resolves every version's source tree (branch
	// selection, API rewrites) under versions/<v>/build/generated/stonecutter — code the
	// single-tree `detekt` task above cannot see. Same CLI, same ledger, one run per
	// version; keep the list in sync with versions(...) in settings.gradle.kts.
	val detektVersionTrees = listOf("1.21.11", "26.1.2", "26.2", "26.3")
	val detektTreeTasks = detektVersionTrees.map { version ->
		tasks.register<JavaExec>("detektTree$version") {
			group = "verification"
			description = "Runs detekt over the $version generated tree (rule ledger: gradle/detekt/detekt.yml)."
			classpath = detektCli
			mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
			javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
			dependsOn(":$version:stonecutterGenerate")
			doFirst {
				val tree = rootDir.resolve("versions/$version/build/generated/stonecutter/main/kotlin")
				require(tree.isDirectory) { "Generated tree missing for $version — run ./gradlew build first (${tree.path})" }
			}
			setArgs(listOf(
				"--input", rootDir.resolve("versions/$version/build/generated/stonecutter/main/kotlin").path,
				"--config", rootProject.file("gradle/detekt/detekt.yml").path,
				"--report", "html:${layout.buildDirectory.file("reports/detekt/report-$version.html").get().asFile.path}",
			))
		}
	}
	tasks.register("detektAll") {
		group = "verification"
		description = "Runs detekt over every stonecutter-generated version tree (rule ledger: gradle/detekt/detekt.yml)."
		dependsOn(detektTreeTasks)
	}
}

// Architecture tests (Konsist + JUnit 5) run on EVERY version tree: the generated sources
// resolve the `//?` branch comments per version, so each tree's test run sees that
// version's live code — same rationale as detektAll above. The shared suite in
// src/test/kotlin receives this project's generated tree through a system property.
// (Stonecutter version projects have Gradle paths like :26.3 but live in versions/<v>.)
val isVersionProject = project.path != ":"
if (isVersionProject) {
	dependencies {
		testImplementation(platform("org.junit:junit-bom:5.11.4"))
		testImplementation("org.junit.jupiter:junit-jupiter")
		testRuntimeOnly("org.junit.platform:junit-platform-launcher")
		testImplementation("com.lemonappdev:konsist:0.17.3")
	}
	sourceSets.test {
		kotlin.setSrcDirs(listOf(rootDir.resolve("src/test/kotlin")))
	}
	tasks.withType<Test>().configureEach {
		useJUnitPlatform()
		dependsOn("stonecutterGenerate")
		// Konsist resolves scope paths against the JVM working directory: pin it to the
		// repo root and hand it the tree path relative to that root.
		workingDir = rootDir
		systemProperty(
			"konsist.tree",
			rootDir.toPath()
				.relativize(layout.buildDirectory.dir("generated/stonecutter/main/kotlin").get().asFile.toPath())
				.toString(),
		)
	}
} else {
	// Only the stonecutter version projects (:versions:<v>) have a generated tree to test;
	// this project carries no sources, so its test tasks stay disabled.
	tasks.named("compileTestKotlin") { enabled = false }
	tasks.named("test") { enabled = false }
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
	// Content filters keep each origin on the critical path only for the groups it
	// actually serves — a flaky origin (Modrinth 522'd once, failing CI on an unrelated
	// resolve) can no longer break the build. The Modrinth maven is gone entirely:
	// nothing in the dependency set resolves exclusively from it (verified by
	// `--refresh-dependencies` after removal); ModMenu's official home is TerraformersMC.
	maven {
		name = "Fabric"
		url = uri("https://maven.fabricmc.net/")
		content {
			includeGroupByRegex("net\\.fabricmc(\\..+)?")
			// transitive deps of Fabric API hosted on the Fabric maven
			includeGroup("org.quiltmc.parsers")
		}
	}
	maven {
		name = "DevAuth"
		url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
		content { includeGroup("me.djtheredstoner") }
	}
	maven {
		name = "TerraformersMC"
		url = uri("https://maven.terraformersmc.com/releases/")
		content { includeGroup("com.terraformersmc") }
	}
	maven {
		name = "QuiltMC"
		url = uri("https://maven.quiltmc.org/repository/release/")
		// org.quiltmc.parsers: transitive deps of Fabric API — previously resolved through
		// an implicit repo, made explicit (and filtered) here.
		content { includeGroupByRegex("org\\.quiltmc.*") }
	}
	maven {
		name = "Xander Maven"
		url = uri("https://maven.isxander.dev/releases")
		content { includeGroup("dev.isxander") }
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

	// Kotlinx serialization: compile only — the runtime jar ships inside Fabric Language
	// Kotlin (kotlinx-serialization-json 1.11.0), so nothing extra is embedded.
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

	// ModMenu integration — compile-only for the published jar, but present in dev runtime
	// so the ModMenu config entrypoint can be tested with runClient.
	// modLocalRuntime is skipped on 26.3: ModMenu 20.0.1 declares <26.3-alpha.3, so the dev
	// client refuses to start until a compatible build is published.
	modCompileOnly("com.terraformersmc:modmenu:$modmenuVersion")
	modLocalRuntime("com.terraformersmc:modmenu:$modmenuVersion")

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
	inputs.property("version", modVersion)
	inputs.property("mc_dep", mcDep)
	inputs.property("loader_dep", loaderDep)

	filesMatching("fabric.mod.json") {
		// modVersion (script-level val, configuration time) instead of project.version:
		// Task.project inside this execution-time closure is a Gradle 10 error.
		expand("version" to modVersion, "mc_dep" to mcDep, "loader_dep" to loaderDep)
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
		// Warnings are errors on every tree (change: warnings-as-errors). With a
		// zero-warning baseline, a new MC version deprecating an API we use fails CI
		// loudly instead of hiding in a `w:` line — a deprecation wave after a version
		// bump is the intended drift alarm. Triage per warning: fix it, or
		// @Suppress at the site with a justifying comment (never blanket).
		allWarningsAsErrors.set(true)
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
