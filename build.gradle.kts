plugins {
    kotlin("jvm") version "1.9.23"
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
    mavenCentral()
}

dependencies {
    // build.gradle
    implementation("org.mvel:mvel2:2.4.12.Final")

    // JUnit 测试框架
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}


tasks {
    patchPluginXml {
        sinceBuild.set("223")
        untilBuild.set("252.*")
    }

    runIde {

        jvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
    }
}

repositories {
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}
