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

// Читает DEPLOY_DIR из локального файла .env (KEY=VALUE, не в гите). null, если нет.
fun deployDirFromEnv(): String? {
    val env = rootProject.file(".env")
    if (!env.exists()) return null
    for (raw in env.readLines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue
        if (line.substringBefore("=").trim() == "DEPLOY_DIR") {
            return line.substringAfter("=").trim().trim('"', '\'')
        }
    }
    return null
}

// Сборка + копирование jar в тестовый сервер. Каталог берётся по приоритету:
//   1) -PdeployDir=<путь>         — явно в командной строке
//   2) DEPLOY_DIR из .env         — локальный файл (см. .env.example)
//   3) build/deploy               — дефолт, чтобы задача не падала
// Пример: ./gradlew deploy   (возьмёт папку из .env)
tasks.register<Copy>("deploy") {
    dependsOn(tasks.jar)
    from(tasks.jar.map { it.archiveFile })
    val target = (project.findProperty("deployDir") as String?) ?: deployDirFromEnv() ?: "build/deploy"
    into(target)
    doNotTrackState("deploy target may contain files locked by a running server")
}
