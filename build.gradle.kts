plugins {
    id("java-library")
    id("eclipse")
    id("idea")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.141"
}

version = project.property("mod_version") as String
group = project.property("mod_group_id") as String

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://maven.createmod.net/")
    }
    maven {
        name = "GeckoLib"
        url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        content {
            includeGroupByRegex("software\\.bernie.*")
            includeGroup("com.eliotlash.mclib")
        }
    }
    maven {
        url = uri("https://maven.tterrag.com/")
    }
    // Pour JEI
    maven {
        name = "ModMaven"
        url = uri("https://modmaven.dev/")
    }
    // Pour les mods Modrinth (Farmer's Delight, etc.)
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    // Pour FTB Teams / Chunks / Library
    maven {
        name = "Saps"
        url = uri("https://maven.saps.dev/releases")
        content {
            includeGroup("dev.ftb.mods")
            includeGroup("dev.latvian.mods")
        }
    }
    // Pour Curios
    maven {
        name = "IllusiveC4"
        url = uri("https://maven.theillusivec4.top/")
    }
    // Pour CC: Tweaked (ComputerCraft)
    maven {
        name = "SquidDev"
        url = uri("https://maven.squiddev.cc/")
    }
    // Pour Architectury
    maven {
        name = "Architectury"
        url = uri("https://maven.architectury.dev/")
    }
    maven {
        name = "IThundxr (Registrate 1.21.1)"
        url = uri("https://maven.ithundxr.dev/snapshots")
        content {
            includeGroup("com.tterrag.registrate")
        }
    }
}

base {
    archivesName.set(project.property("archives_base_name") as String)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

neoForge {
    version = project.property("neo_version") as String

    parchment {
        mappingsVersion = project.property("parchment_mapping_version") as String
        minecraftVersion = project.property("parchment_minecraft_version") as String
    }

    runs {
        configureEach {
            systemProperty("neoforge.enabledGameTestNamespaces", project.property("mod_id") as String)
        }
        create("client") {
            client()
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
        create("gameTestServer") {
            type.set("gameTestServer")
        }
        create("data") {
            data()
            programArguments.addAll("--mod", project.property("mod_id") as String, "--all", "--output", file("src/generated/resources/").absolutePath, "--existing", file("src/main/resources/").absolutePath)
        }
    }

    mods {
        create(project.property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

dependencies {
    implementation("com.simibubi.create:create-1.21.1:6.0.10-281") {
        exclude(group = "info.journeymap")
        exclude(group = "dev.ftb.mods")
        exclude(group = "dev.architectury")
    }
    implementation("software.bernie.geckolib:geckolib-neoforge-1.21.1:4.8.4")
}

var generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to project.property("minecraft_version") as String,
        "minecraft_version_range" to project.property("minecraft_version_range") as String,
        "neo_version" to project.property("neo_version") as String,
        "neo_version_range" to project.property("neo_version_range") as String,
        "loader_version_range" to project.property("loader_version_range") as String,
        "mod_id" to project.property("mod_id") as String,
        "mod_name" to project.property("mod_name") as String,
        "mod_license" to project.property("mod_license") as String,
        "mod_version" to project.property("mod_version") as String,
        "mod_authors" to project.property("mod_authors") as String,
        "mod_description" to project.property("mod_description") as String
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates/neoforge.mods.toml") {
        into("META-INF")
    }
    into("build/generated/sources/modMetadata")
}

sourceSets.main.get().resources.srcDir(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}