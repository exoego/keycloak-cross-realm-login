package net.exoego.keycloak.crossrealm;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static net.exoego.keycloak.crossrealm.Logins.CLIENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HomeRealmInTokenIT {

    @Test
    void theIdTokenNamesTheHomeRealmAndTheTokensCarryNoRoleAndNoGroupOfIt() throws Exception {
        String username = "named-" + UUID.randomUUID();
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage(); TempRealm home = new TempRealm();
                Browser browser = new Browser()) {
            home.setDisplayName("Home Realm Inc.");
            String userId = home.addUser(username, "the-password", true);
            home.giveRealmRole(userId, "home-only-role");
            home.addToGroup(userId, "home-only-group");
            entry.addUserAttributeMapper(CLIENT, "home_realm", "home_realm");
            entry.addUserAttributeMapper(CLIENT, "home_realm_display_name", "home_realm_display_name");
            entry.addGroupMembershipMapper(CLIENT, "groups");

            Tokens tokens = browser.exchangeTokens(entry.name(), CLIENT,
                    browser.login(entry.name(), CLIENT, username, "the-password").code());

            assertEquals(home.name(), tokens.idToken().claims().get("home_realm"));
            assertEquals("Home Realm Inc.", tokens.idToken().claims().get("home_realm_display_name"));
            for (IdToken token : new IdToken[] {tokens.idToken(), tokens.accessToken()}) {
                assertFalse(token.claims().toString().contains("home-only-role"));
                assertFalse(token.claims().toString().contains("home-only-group"));
            }
        }
    }

    @Test
    void aHomeRealmWithoutDisplayNameGivesNoDisplayNameClaim() throws Exception {
        String username = "unnamed-" + UUID.randomUUID();
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage(); TempRealm home = new TempRealm();
                Browser browser = new Browser()) {
            home.addUser(username, "the-password", true);
            entry.addUserAttributeMapper(CLIENT, "home_realm", "home_realm");
            entry.addUserAttributeMapper(CLIENT, "home_realm_display_name", "home_realm_display_name");

            IdToken token = browser.exchange(entry.name(), CLIENT,
                    browser.login(entry.name(), CLIENT, username, "the-password").code());

            assertEquals(home.name(), token.claims().get("home_realm"));
            assertFalse(token.claims().containsKey("home_realm_display_name"));
        }
    }
}
