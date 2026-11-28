package net.exoego.keycloak.crossrealm;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.jacoco.core.tools.ExecDumpClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.utility.MountableFile;

final class Coverage {

    private static final int PORT = 6300;
    private static final String AGENT = System.getProperty("jacoco.agent");
    private static final String DIR = System.getProperty("jacoco.dir");
    private static final String CLASSES = System.getProperty("jacoco.classes");

    private Coverage() {
    }

    static KeycloakContainer measure(KeycloakContainer keycloak) {
        if (AGENT == null) {
            return keycloak;
        }
        File classes = new File(CLASSES);
        classes.mkdirs();
        classes.setWritable(true, false);
        return keycloak
                .withCopyFileToContainer(MountableFile.forHostPath(AGENT), "/opt/jacoco/agent.jar")
                .withFileSystemBind(classes.getAbsolutePath(), "/opt/jacoco/classes", BindMode.READ_WRITE)
                .withEnv("JAVA_OPTS_APPEND", "-javaagent:/opt/jacoco/agent.jar=output=tcpserver,address=*,port="
                        + PORT + ",includes=net.exoego.keycloak.crossrealm.*"
                        + ",classdumpdir=/opt/jacoco/classes");
    }

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
