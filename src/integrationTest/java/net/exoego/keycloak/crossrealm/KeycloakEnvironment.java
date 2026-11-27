package net.exoego.keycloak.crossrealm;

import java.io.File;
import java.util.List;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One Keycloak with the provider jar and one PostgreSQL, shared by all integration tests of a run.
 * Testcontainers removes the containers when the JVM exits.
 */
final class KeycloakEnvironment {

    static final String PROVIDERS_DIR = "/opt/keycloak/providers";
    static final File PROVIDER_JAR = new File(System.getProperty("provider.jar"));
    static final String KEYCLOAK_VERSION = System.getProperty("keycloak.version");

    private static final Network NETWORK = Network.newNetwork();

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("keycloak")
            .withUsername("keycloak")
            .withPassword("keycloak");

    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:" + KEYCLOAK_VERSION)
            .withNetwork(NETWORK)
            .withProviderLibsFrom(List.of(PROVIDER_JAR))
            .withRealmImportFiles("/entry-realm.json", "/home-a-realm.json", "/home-b-realm.json")
            .withEnv("KC_DB", "postgres")
            .withEnv("KC_DB_URL", "jdbc:postgresql://postgres:5432/keycloak")
            .withEnv("KC_DB_USERNAME", "keycloak")
            .withEnv("KC_DB_PASSWORD", "keycloak");

    static {
        POSTGRES.start();
        KEYCLOAK.start();
    }

    private KeycloakEnvironment() {
    }

    static String realmUrl(String realm) {
        return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
    }
}
