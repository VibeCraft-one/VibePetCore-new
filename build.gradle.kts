plugins {
    id("java")
}

import java.util.zip.ZipFile

group = "dev.li2fox.vibepetcore"
version = "2.6.37"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.mysql:mysql-connector-j:8.4.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    constraints {
        implementation("com.google.protobuf:protobuf-java:3.25.8")
        implementation("org.codehaus.plexus:plexus-utils:4.0.2")
        implementation("org.apache.commons:commons-lang3:3.18.0")
    }

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

val resourcePackSourceDir = layout.projectDirectory.dir("resource-pack/VibePetCore")
val resourcePackZip = layout.buildDirectory.file("resource-pack/VibePetCore-resource-pack.zip")

tasks.register<Zip>("buildResourcePack") {
    group = "build"
    description = "Builds the VibePetCore client resource pack zip."
    from(resourcePackSourceDir)
    archiveFileName.set("VibePetCore-resource-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("resource-pack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register<Copy>("publishResourcePack") {
    group = "distribution"
    description = "Publishes the built resource pack zip to dist/."
    dependsOn("buildResourcePack")
    from(resourcePackZip)
    into(layout.projectDirectory.dir("dist"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    exclude("MIGRATION-*")
    exclude("**/MIGRATION-*")
    exclude("resource-pack/**")
    filesMatching("plugin.yml") {
        expand(props)
    }
    filesMatching("config.yml") {
        expand(props)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("MIGRATION-*")
    exclude("**/MIGRATION-*")
    exclude("resource-pack/**")
    exclude("org/sqlite/native/Linux-Android/**")
    exclude("org/sqlite/native/Windows/**")
    exclude("org/sqlite/native/FreeBSD/**")
    exclude("org/sqlite/native/Mac/**")
    exclude("org/sqlite/native/Linux/ppc64/**")
    exclude("org/sqlite/native/Linux/arm/**")
    exclude("org/sqlite/native/Linux/armv6/**")
    exclude("org/sqlite/native/Linux/armv7/**")
    exclude("org/sqlite/native/Linux/x86/**")
    exclude("org/sqlite/native/Linux-Musl/**")
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

val maxPluginJarSizeBytes = 12L * 1024 * 1024

tasks.register("checkJarSize") {
    group = "verification"
    description = "Verifies that the built plugin jar size does not exceed the configured limit."
    dependsOn(tasks.jar)

    doLast {
        val jarFile = tasks.jar.get().archiveFile.get().asFile
        val jarSizeBytes = jarFile.length()
        require(jarSizeBytes <= maxPluginJarSizeBytes) {
            "Plugin jar is ${jarSizeBytes / 1024} KiB, limit is ${maxPluginJarSizeBytes / 1024} KiB: ${jarFile.name}"
        }
        logger.lifecycle("Plugin jar size: ${jarSizeBytes / 1024} KiB / ${maxPluginJarSizeBytes / 1024} KiB")
    }
}

tasks.register("checkJarHasNoBundledResourcePack") {
    group = "verification"
    description = "Verifies that the plugin jar does not contain a bundled resource pack zip."
    dependsOn(tasks.jar)

    doLast {
        val jarFile = tasks.jar.get().archiveFile.get().asFile
        val bundledEntries = ZipFile(jarFile).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("resource-pack/") && it.endsWith(".zip") }
                .toList()
        }
        require(bundledEntries.isEmpty()) {
            "Plugin jar must not bundle resource pack archives: ${bundledEntries.joinToString()}"
        }
    }
}

tasks.check {
    dependsOn("checkJarSize", "checkJarHasNoBundledResourcePack")
}
