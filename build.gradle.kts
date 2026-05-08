plugins {
    id("net.fabricmc.fabric-loom")
    id("me.modmuss50.mod-publish-plugin") version "2.0.0-beta.1"
    `maven-publish`
}

version = "${property("mod.version")}+${sc.current.version}"
group = property("mod.group") as String
base.archivesName = property("mod.id") as String
val licenseArchiveName = "LICENSE_${rootProject.name}"

val requiredJava = when {
    sc.current.version == "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven") { name = "Modrinth" }
        }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
}

loom {
    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.named("server") {
        ideConfigGenerated(true)
        runDir = "../../run"
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

tasks {
    processResources {
        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "description" to project.property("mod.description"),
            "minecraft" to project.property("mod.mc_dep")
        )

        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    withType<JavaCompile>().configureEach {
        options.release = requiredJava.majorVersion.toInt()
    }

    jar {
        from(rootProject.file("LICENSE")) {
            rename { licenseArchiveName }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(jar.map { it.archiveFile }, named<Jar>("sourcesJar").map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
        dependsOn("build")
    }
}

publishMods {
    displayName = "${property("mod.name")} ${project.version}"
    version = property("mod.version") as String
    changelog = providers.environmentVariable("CHANGELOG")
    type = STABLE
    modLoaders.add("fabric")
    file.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })

    modrinth {
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
        projectId.set(property("modrinth.id") as String)
        minecraftVersions.add(stonecutter.current.version)
        requires("fabric-api")
    }
}
