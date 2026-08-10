plugins {
	id("net.fabricmc.fabric-loom") version "1.15-SNAPSHOT"
	kotlin("jvm") version "2.4.10"
	`maven-publish`
}

val minecraft_version: String by project
val loader_version: String by project
val mod_version: String by project
val maven_group: String by project
val archives_base_name: String by project
val fabric_version: String by project

version = mod_version
group = maven_group

base {
	archivesName.set(archives_base_name)
}

loom {
	runs {
		named("client") {
			property("devauth.enabled", "true")
			property("devauth.account", "main")
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
		name = "Gegy"
		url = uri("https://maven.gegy.dev")
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:$minecraft_version")
	implementation("net.fabricmc:fabric-loader:$loader_version")

	// Fabric API
	implementation("net.fabricmc.fabric-api:fabric-api:$fabric_version")

	// Fabric Language Kotlin
	implementation("net.fabricmc:fabric-language-kotlin:1.13.13+kotlin.2.4.10")

	include(implementation("dev.lambdaurora:spruceui:11.0.0+26.2")!!)
	include("dev.yumi.mc.core:yumi-mc-foundation:1.1.1+26.2")

	// ModMenu integration
	compileOnly("com.terraformersmc:modmenu:20.0.0-beta.2")

	// DevAuth: authenticate with a real Microsoft account in dev environment
	runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")

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

	filesMatching("fabric.mod.json") {
		expand("version" to project.version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set(25)
}

java {
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
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
