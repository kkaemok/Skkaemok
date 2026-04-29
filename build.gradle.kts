plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "org.kkaemok"
version = "1.6"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "dmulloy2-repo"
        url = uri("https://repo.dmulloy2.net/repository/public/")
    }
    maven {
        name = "skript-repo"
        url = uri("https://repo.skriptlang.org/releases")
    }
    maven {
        name = "luckperms-repo"
        url = uri("https://repo.lucko.me/")
    }
}

dependencies {
    constraints {
        compileOnly("org.apache.commons:commons-lang3:3.20.0") {
            because("Resolve CVE-2025-48924 from Paper API transitive dependencies")
        }
        compileOnly("org.codehaus.plexus:plexus-utils:4.0.3") {
            because("Resolve CVE-2025-67030 from Paper API transitive dependencies")
        }
    }

    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
    compileOnly("com.github.SkriptLang:Skript:2.9.1")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("com.google.guava:guava:32.1.3-jre")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }
}

val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.runtimeClasspath.get())

    dependencies {
        // Only merge bStats into the final jar, no other dependencies
        exclude { it.moduleGroup != "org.bstats" }
    }

    // Relocate bStats into the plugin's package to avoid conflicts with other
    // plugins using bStats
    relocate("org.bstats", "org.kkaemok.skkaemok.bstats")
    archiveClassifier.set("")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
