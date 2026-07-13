plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

intellij {
    version.set("2023.2.2")
    type.set("IC")
    plugins.set(listOf("java"))
}

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    compileTestJava {
        options.encoding = "UTF-8"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("")
    }

    runIde {
        jvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
    }
}

tasks.test {
    useJUnitPlatform()
}

// The plugin exposes no Settings/SearchableOptions pages. Starting a full headless IDE
// for this task only slows packaging and can leave Maven indexer files locked on Windows.
tasks.named("buildSearchableOptions") {
    enabled = false
}
