package net.exoego.keycloak.crossrealm;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.jacoco.core.tools.ExecDumpClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.utility.MountableFile;

/**
 * Measures which code of the provider the integration tests run.
 * The provider runs in the JVM of Keycloak, so the JaCoCo agent runs there and the tests read its data out.
 */
final class Coverage {

    private static final int PORT = 6300;
    private static final String AGENT = System.getProperty("jacoco.agent");
    private static final String DIR = System.getProperty("jacoco.dir");
    private static final String CLASSES = System.getProperty("jacoco.classes");

    private Coverage() {
    }

    /** Adds the agent to a Keycloak container, when the build asks for it. */
    static KeycloakContainer measure(KeycloakContainer keycloak) {
        if (AGENT == null) {
            return keycloak;
        }
        // Keycloak can rewrite a class when it builds the server.
        // The agent writes the classes as it loads them, and the report needs exactly those.
        File classes = new File(CLASSES);
        classes.mkdirs();
        // The user in the container is not the user of the build.
        classes.setWritable(true, false);
        return keycloak
                .withCopyFileToContainer(MountableFile.forHostPath(AGENT), "/opt/jacoco/agent.jar")
                .withFileSystemBind(classes.getAbsolutePath(), "/opt/jacoco/classes", BindMode.READ_WRITE)
                .withEnv("JAVA_OPTS_APPEND", "-javaagent:/opt/jacoco/agent.jar=output=tcpserver,address=*,port="
                        + PORT + ",includes=net.exoego.keycloak.crossrealm.*"
                        + ",classdumpdir=/opt/jacoco/classes");
    }

    /** The parent sets its own list of ports, so the port of the agent is added after it. */
    static KeycloakContainer keycloak(String image) {
        return new KeycloakContainer(image) {
            @Override
            protected void configure() {
                super.configure();
                if (AGENT != null) {
                    addExposedPort(PORT);
                }
            }
        };
    }

    /** Saves what the container measured so far. Call it before the container stops. */
    static void save(KeycloakContainer keycloak) {
        if (AGENT == null || !keycloak.isRunning()) {
            return;
        }
        var client = new ExecDumpClient();
        client.setDump(true);
        try {
            client.dump(keycloak.getHost(), keycloak.getMappedPort(PORT))
                    .save(new File(DIR, UUID.randomUUID() + ".exec"), false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
