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
    // The ID that home-excluded-realm.json gives to its realm. Realm IDs are random otherwise.
    static final String EXCLUDED_REALM_ID = "excluded-realm-id";
    static final String KEYCLOAK_VERSION = System.getProperty("keycloak.version");

    private static final Network NETWORK = Network.newNetwork();

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version"))
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("keycloak")
            .withUsername("keycloak")
            .withPassword("keycloak");

    static final KeycloakContainer KEYCLOAK = Coverage.measure(Coverage.keycloak("quay.io/keycloak/keycloak:" + KEYCLOAK_VERSION))
            .withNetwork(NETWORK)
            .withProviderLibsFrom(List.of(PROVIDER_JAR))
            .withRealmImportFiles("/entry-realm.json", "/home-a-realm.json", "/home-b-realm.json",
                    "/home-no-email-login-realm.json",
                    "/home-disabled-realm.json", "/home-excluded-realm.json")
            .withEnv("KC_SPI_CROSS_REALM_LOGIN__DEFAULT__EXCLUDED_REALM_IDS", EXCLUDED_REALM_ID)
            .withEnv("KC_DB", "postgres")
            .withEnv("KC_DB_URL", "jdbc:postgresql://postgres:5432/keycloak")
            .withEnv("KC_DB_USERNAME", "keycloak")
            .withEnv("KC_DB_PASSWORD", "keycloak");

    static {
        POSTGRES.start();
        KEYCLOAK.start();
        // Testcontainers kills the container after the JVM exits, so the data is read before that.
        // The hook also runs when a test fails.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Coverage.save(KEYCLOAK)));
    }

    private KeycloakEnvironment() {
    }

    static String realmUrl(String realm) {
        return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
    }
}
