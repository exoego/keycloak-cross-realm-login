import java.time.Duration
import java.util.zip.ZipFile

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

// Test-only provider with a REST endpoint for the integration tests. It is never published.
val testProvider = sourceSets.create("testProvider")
val testProviderJar = tasks.register<Jar>("testProviderJar") {
    archiveClassifier.set("test-provider")
    // Away from build/libs, so nobody installs it with the main jar.
    destinationDirectory.set(layout.buildDirectory.dir("test-libs"))
    from(testProvider.output)
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
    "testProviderCompileOnly"(platform(libs.keycloak.parent))
    "testProviderCompileOnly"(libs.bundles.keycloak.provided)
    "testProviderCompileOnly"(sourceSets.main.get().output)
}

// An empty value, as from a CI matrix row without it, means the version of the catalog.
val testKeycloakVersion = providers.gradleProperty("testKeycloakVersion")
    .filter { it.isNotBlank() }
    .orElse(libs.versions.keycloak)

// The CLI of Playwright is in the same jar as its API.
val playwrightCli = configurations.create("playwrightCli")
dependencies {
    playwrightCli(libs.playwright)
}

// Quick when the browser is already there. The CI workflow sets playwrightDeps to install the system libraries too.
val installBrowser = tasks.register<JavaExec>("installBrowser") {
    classpath = playwrightCli
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "--only-shell")
    if (providers.gradleProperty("playwrightDeps").isPresent) {
        args("--with-deps")
    }
    args("chromium")
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
        }

        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(libs.testcontainers.keycloak)
                implementation(libs.testcontainers.postgresql)
                implementation(platform(libs.keycloak.parent))
                implementation(libs.jackson.databind)
                implementation(libs.playwright)
                runtimeOnly(libs.slf4j.simple)
            }
            targets.all {
                testTask.configure {
                    // The tests run the jar, so a new jar must run them again.
                    inputs.files(tasks.jar, testProviderJar)
                    systemProperty("keycloak.version", testKeycloakVersion.get())
                    systemProperty("postgres.version", libs.versions.postgres.image.get())
                    // The installBrowser task is the one place that downloads the browser.
                    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
                    dependsOn(installBrowser)
                    // Shows the browser window while debugging. Run with -Pheaded.
                    systemProperty("browser.headed", providers.gradleProperty("headed").isPresent)
                    systemProperty("provider.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
                    systemProperty("test.provider.jar", testProviderJar.get().archiveFile.get().asFile.absolutePath)
                    // A hung container call must fail the build, not block it.
                    // The CI jobs that run the tests have a longer limit, so this one fails first and the report shows why.
                    timeout.set(Duration.ofMinutes(30))
                    shouldRunAfter(tasks.test)
                }
            }
        }
    }
}

tasks.check {
    dependsOn(testing.suites.named("integrationTest"))
}

// The tests run on this JDK. The compiler stays on JDK 21.
val testJavaVersion = providers.gradleProperty("testJavaVersion").orElse("21")
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(testJavaVersion.map { JavaLanguageVersion.of(it) })
    })
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

// The test provider and the Keycloak libraries must stay out of the jar that users install.
val checkMainJar = tasks.register("checkMainJar") {
    val jar = tasks.jar.flatMap { it.archiveFile }
    val testProviderClasses = testProvider.output.classesDirs
    inputs.file(jar)
    inputs.files(testProviderClasses)
    doLast {
        val testProviderEntries = testProviderClasses.files.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "class" }.map { it.relativeTo(dir).invariantSeparatorsPath }
        }.toSet()
        val unwanted = ZipFile(jar.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }
                .filter { it in testProviderEntries || it.startsWith("org/keycloak/") }
                .toList()
        }
        check(unwanted.isEmpty()) { "The main jar holds entries that must not ship: $unwanted" }
    }
}

tasks.check {
    dependsOn(checkMainJar)
}
