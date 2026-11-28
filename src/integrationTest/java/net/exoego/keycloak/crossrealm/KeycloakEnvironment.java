package net.exoego.keycloak.crossrealm;

import java.io.File;
import java.util.List;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.spi.ResteasyClientClassicProvider;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class KeycloakEnvironment {

    static final String PROVIDERS_DIR = "/opt/keycloak/providers";
    static final File PROVIDER_JAR = new File(System.getProperty("provider.jar"));
    static final String EXCLUDED_REALM_ID = "excluded-realm-id";
    static final String KEYCLOAK_VERSION = System.getProperty("keycloak.version");

    private static final Network NETWORK = Network.newNetwork();

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(System.getProperty("postgres.image"))
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("keycloak")
            .withUsername("keycloak")
            .withPassword("keycloak");

    static final KeycloakContainer KEYCLOAK = Coverage.measure(Coverage.keycloak(System.getProperty("keycloak.image")))
            .withNetwork(NETWORK)
            .withProviderLibsFrom(List.of(PROVIDER_JAR))
            .withRealmImportFiles("/entry-realm.json", "/home-a-realm.json", "/home-b-realm.json",
                    "/home-no-email-login-realm.json",
                    "/home-disabled-realm.json", "/home-excluded-realm.json")
            .withEnv("KC_SPI_CROSS_REALM_LOGIN__DEFAULT__EXCLUDED_REALM_IDS", EXCLUDED_REALM_ID)
            .withEnv("KC_SPI_PASSWORD_HASHING__ARGON2__ITERATIONS", "1")
            .withEnv("KC_SPI_PASSWORD_HASHING__ARGON2__MEMORY", "1024")
            .withEnv("KC_DB", "postgres")
            .withEnv("KC_DB_URL", "jdbc:postgresql://postgres:5432/keycloak")
            .withEnv("KC_DB_USERNAME", "keycloak")
            .withEnv("KC_DB_PASSWORD", "keycloak");

    static final Keycloak ADMIN;

    static {
        POSTGRES.start();
        KEYCLOAK.start();
        ADMIN = KeycloakBuilder.builder()
                .serverUrl(KEYCLOAK.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(KEYCLOAK.getAdminUsername())
                .password(KEYCLOAK.getAdminPassword())
                .resteasyClient(ResteasyClientClassicProvider.createClientBuilder()
                        .connectionPoolSize(32)
                        .maxPooledPerRoute(32)
                        .build())
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(ADMIN::close));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Coverage.save(KEYCLOAK)));
    }

    private KeycloakEnvironment() {
    }

    static String realmUrl(String realm) {
        return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
    }
}
