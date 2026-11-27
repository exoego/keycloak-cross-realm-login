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

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// The JaCoCo agent for the JVM of Keycloak.
val jacocoContainerAgent = configurations.create("jacocoContainerAgent")

dependencies {
    jacocoContainerAgent("org.jacoco:org.jacoco.agent:${libs.versions.jacoco.get()}:runtime")
    errorprone(libs.errorprone.core)
    compileOnly(platform(libs.keycloak.parent))
    compileOnly(libs.bundles.keycloak.provided)
    testImplementation(platform(libs.keycloak.parent))
    testImplementation(libs.bundles.keycloak.provided)
    testImplementation(libs.archunit)
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
                implementation(libs.jacoco.core)
                runtimeOnly(libs.slf4j.simple)
            }
            targets.all {
                testTask.configure {
                    // The tests run the jar, so a new jar must run them again.
                    inputs.files(tasks.jar)
                    systemProperty("keycloak.version", testKeycloakVersion.get())
                    systemProperty("postgres.version", libs.versions.postgres.image.get())
                    // The installBrowser task is the one place that downloads the browser.
                    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
                    dependsOn(installBrowser)
                    // Shows the browser window while debugging. Run with -Pheaded.
                    systemProperty("browser.headed", providers.gradleProperty("headed").isPresent)
                    systemProperty("provider.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
                    // A hung container call must fail the build, not block it.
                    // The CI jobs that run the tests have a longer limit, so this one fails first and the report shows why.
                    timeout.set(Duration.ofMinutes(30))
                    // The provider runs in the JVM of Keycloak, so the JaCoCo agent goes into that container.
                    val agent = jacocoContainerAgent
                    val coverageDir = layout.buildDirectory.dir("jacoco/keycloak")
                    val loadedClasses = layout.buildDirectory.dir("jacoco-classes/keycloak")
                    inputs.files(agent)
                    outputs.dir(coverageDir)
                    outputs.dir(loadedClasses)
                    doFirst {
                        // A rerun must not append to old data.
                        for (dir in listOf(coverageDir, loadedClasses)) {
                            dir.get().asFile.deleteRecursively()
                            dir.get().asFile.mkdirs()
                        }
                        systemProperty("jacoco.agent", agent.singleFile.absolutePath)
                        systemProperty("jacoco.dir", coverageDir.get().asFile.absolutePath)
                        systemProperty("jacoco.classes", loadedClasses.get().asFile.absolutePath)
                    }
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
// The second part comes out of the Keycloak container, where the provider really runs.
val coverageData = fileTree(layout.buildDirectory.dir("jacoco")) { include("**/*.exec") }

// Keycloak can rewrite a class when it builds the server. Coverage data fits only the class it was
// measured on, so the report takes each class as Keycloak loaded it, and the compiled class otherwise.
val coverageClasses = tasks.register("coverageClasses") {
    mustRunAfter(tasks.named("integrationTest"))
    val compiled = sourceSets.main.get().output.classesDirs
    val loaded = layout.buildDirectory.dir("jacoco-classes/keycloak")
    val target = layout.buildDirectory.dir("jacoco-classes/report")
    inputs.files(compiled)
    inputs.files(loaded)
    outputs.dir(target)
    doLast {
        val out = target.get().asFile
        out.deleteRecursively()
        compiled.forEach { dir -> if (dir.exists()) dir.copyRecursively(out, overwrite = true) }
        // The agent names a file "Name.<id>.class". The same class can be written twice, as compiled
        // and as rewritten. The rewritten one is the one that ran.
        val dumped = Regex("""(.+)\.[0-9a-f]{16}\.class""")
        val root = loaded.get().asFile
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val name = dumped.matchEntire(file.name)?.groupValues?.get(1) ?: return@forEach
            val place = File(out, file.relativeTo(root).parentFile.path + "/" + name + ".class")
            val original = compiled.map { File(it, place.relativeTo(out).path) }.firstOrNull { it.exists() }
            if (original == null || !original.readBytes().contentEquals(file.readBytes())) {
                place.parentFile.mkdirs()
                file.copyTo(place, overwrite = true)
            }
        }
    }
}

tasks.jacocoTestReport {
    mustRunAfter(tasks.test, tasks.named("integrationTest"))
    dependsOn(coverageClasses)
    executionData.setFrom(coverageData)
    classDirectories.setFrom(layout.buildDirectory.dir("jacoco-classes/report"))
    sourceDirectories.setFrom(sourceSets.main.get().allJava.srcDirs)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

// The Keycloak libraries must stay out of the jar that users install.
val checkMainJar = tasks.register("checkMainJar") {
    val jar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val unwanted = ZipFile(jar.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }
                .filter { it.startsWith("org/keycloak/") }
                .toList()
        }
        check(unwanted.isEmpty()) { "The main jar holds entries that must not ship: $unwanted" }
    }
}

tasks.check {
    dependsOn(checkMainJar)
}
