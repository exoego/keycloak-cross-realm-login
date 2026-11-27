package net.exoego.keycloak.crossrealm;

import org.junit.jupiter.api.Test;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RealmsIT {

    @Test
    void haveTheDisplayNamesOfTheRealmFiles() {
        assertEquals("Entry", displayName("entry"));
        assertEquals("Home A", displayName("home-a"));
        assertEquals("Home B", displayName("home-b"));
    }

    private static String displayName(String realm) {
        return KEYCLOAK.getKeycloakAdminClient().realm(realm).toRepresentation().getDisplayName();
    }
}
