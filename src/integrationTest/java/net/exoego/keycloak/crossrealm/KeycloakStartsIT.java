package net.exoego.keycloak.crossrealm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.ADMIN;
import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakStartsIT {

    @Test
    void isHealthy() throws Exception {
        URI ready = URI.create("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(9000) + "/health/ready");
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(ready).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
        }
    }

    @Test
    void hasTheJarOfThisBuildInTheProvidersDirectory() throws Exception {
        String listing = KEYCLOAK.execInContainer("ls", KeycloakEnvironment.PROVIDERS_DIR).getStdout();
        assertTrue(listing.lines().anyMatch(KeycloakEnvironment.PROVIDER_JAR.getName()::equals), listing);
    }

    @Test
    void storesTheRealmsInPostgres() throws Exception {
        String count = KeycloakEnvironment.POSTGRES.execInContainer("psql", "-U", "keycloak", "-d", "keycloak", "-tAc",
                "select count(*) from realm where name in ('entry', 'home-a', 'home-b')").getStdout();
        assertEquals("3", count.strip());
    }

    @Test
    void runsTheRequestedVersion() {
        String running = ADMIN.serverInfo().getInfo().getSystemInfo().getVersion();
        assertEquals(KeycloakEnvironment.KEYCLOAK_VERSION, running);
    }
}
