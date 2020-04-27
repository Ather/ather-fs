import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.72" apply false
}

subprojects {
    apply {
        plugin("org.jetbrains.kotlin.jvm")
    }

    group = "dev.ather.fs"

    repositories {
        jcenter()
    }

    val implementation by configurations
    val testImplementation by configurations

    dependencies {
        implementation(kotlin("stdlib-jdk8"))

        testImplementation("io.mockk:mockk:1.10.0")
        testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
