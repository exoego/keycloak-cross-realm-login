package net.exoego.keycloak.crossrealm;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.info.SpiInfoRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.ADMIN;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossRealmLoginSpiIT {

    @Test
    void keycloakListsTheSpiWithTheDefaultProvider() {
        SpiInfoRepresentation spi = ADMIN.serverInfo().getInfo().getProviders()
                .get("cross-realm-login");

        assertNotNull(spi);
        assertTrue(spi.getProviders().containsKey("default"), spi.getProviders().keySet().toString());
    }
}
