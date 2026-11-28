package net.exoego.keycloak.crossrealm;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossRealmUserTest {

    @Test
    void namesTheHomeRealmAndItsDisplayName() {
        assertEquals(Map.of("home_realm", "acme", "home_realm_display_name", "Acme Inc."),
                CrossRealmUser.homeRealmValues("acme", "Acme Inc."));
    }

    @Test
    void leavesOutANullDisplayName() {
        assertEquals(Map.of("home_realm", "acme"), CrossRealmUser.homeRealmValues("acme", null));
    }

    @Test
    void leavesOutABlankDisplayName() {
        assertEquals(Map.of("home_realm", "acme"), CrossRealmUser.homeRealmValues("acme", "  "));
        assertEquals(Map.of("home_realm", "acme"), CrossRealmUser.homeRealmValues("acme", ""));
    }
}
