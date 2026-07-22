plugins {
    java
}

group = "ru.kiviuly.skyblockwars"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("SkyBlockWars")
}

// Ð¡Ð±Ð¾Ñ€ÐºÐ° + ÐºÐ¾Ð¿Ð¸Ñ€Ð¾Ð²Ð°Ð½Ð¸Ðµ jar Ð² Ñ‚ÐµÑÑ‚Ð¾Ð²Ñ‹Ð¹ ÑÐµÑ€Ð²ÐµÑ€:
//   ./gradlew deploy -PdeployDir=C:/Servers/test/plugins
// Ð‘ÐµÐ· -PdeployDir ÐºÐ¾Ð¿Ð¸Ñ€ÑƒÐµÑ‚ Ð² build/deploy (Ð¿Ñ€Ð¾ÑÑ‚Ð¾ Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð·Ð°Ð´Ð°Ñ‡Ð° Ð½Ðµ Ð¿Ð°Ð´Ð°Ð»Ð°).
tasks.register<Copy>("deploy") {
    dependsOn(tasks.jar)
    from(tasks.jar.map { it.archiveFile })
    val target = (project.findProperty("deployDir") as String?) ?: "build/deploy"
    into(target)
    doNotTrackState("deploy target may contain files locked by a running server")
}
