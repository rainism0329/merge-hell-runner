plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.bigphil.mergehell"
version = providers.gradleProperty("pluginVersion").get()

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
        sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
        untilBuild.set(providers.gradleProperty("pluginUntilBuild"))
    }

    runIde {
        jvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
    }

    signPlugin {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
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
