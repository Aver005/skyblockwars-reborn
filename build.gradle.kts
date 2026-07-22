plugins {
    java
}

group = "ru.kiviuly"
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
    archiveBaseName.set("MCMGP")
}

// Сборка + копирование jar в тестовый сервер:
//   ./gradlew deploy -PdeployDir=C:/Servers/test/plugins
// Без -PdeployDir копирует в build/deploy (просто чтобы задача не падала).
tasks.register<Copy>("deploy") {
    dependsOn(tasks.jar)
    from(tasks.jar.map { it.archiveFile })
    val target = (project.findProperty("deployDir") as String?) ?: "build/deploy"
    into(target)
    doNotTrackState("deploy target may contain files locked by a running server")
}
