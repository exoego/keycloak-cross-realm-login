plugins {
    `java-library`
    jacoco
    alias(libs.plugins.errorprone)
}

group = "net.exoego"
version = System.getenv("RELEASE_VERSION") ?: "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    // Every warning of the compiler and of Error Prone fails the build.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Jar>().configureEach {
    from(files("LICENSE", "NOTICE")) {
        into("META-INF")
    }
}

repositories {
    mavenCentral()
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

dependencies {
    errorprone(libs.errorprone.core)
    compileOnly(platform(libs.keycloak.parent))
    compileOnly(libs.bundles.keycloak.provided)
    testImplementation(platform(libs.keycloak.parent))
    testImplementation(libs.bundles.keycloak.provided)
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
        }

        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(platform(libs.keycloak.parent))
            }
            targets.all {
                testTask.configure {
                    shouldRunAfter(tasks.test)
                }
            }
        }
    }
}

tasks.check {
    dependsOn(testing.suites.named("integrationTest"))
}

// One report for the unit tests and for the integration tests.
tasks.jacocoTestReport {
    mustRunAfter(tasks.test, tasks.named("integrationTest"))
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")) { include("**/*.exec") })
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}
