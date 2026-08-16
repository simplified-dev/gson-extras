plugins {
    id("java-library")
}

group = "dev.simplified"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    // Simplified Libraries
    api("com.github.simplified-dev:collections") { version { strictly("8ca6cb8") } }
    api("com.github.simplified-dev:utils") { version { strictly("821499b") } }
    api("com.github.simplified-dev:reflection") { version { strictly("6c3b7c5") } }

    // JetBrains Annotations
    api(libs.annotations)

    // Gson
    api(libs.gson)

    // Simplified Annotations
    compileOnly(libs.simplified.annotations)
    annotationProcessor(libs.simplified.annotations)
    testCompileOnly(libs.simplified.annotations)
    testAnnotationProcessor(libs.simplified.annotations)

    // Tests
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
