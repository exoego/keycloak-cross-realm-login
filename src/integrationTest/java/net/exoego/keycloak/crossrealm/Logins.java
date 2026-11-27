package net.exoego.keycloak.crossrealm;

import java.util.List;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Logs in at an entry realm in a new browser, and names the proxy users of an entry realm. */
final class Logins {

    static final String CLIENT = "entry-client";
    static final String USER_STORAGE = "org.keycloak.storage.UserStorageProvider";
    static final String STORAGE_ID = "cross-realm-users";

    private static final String INVALID = "Invalid username or password.";

    private Logins() {
    }

    static IdToken logIn(String realm, String identifier, String password) throws Exception {
        try (Browser browser = new Browser()) {
            return browser.exchange(realm, CLIENT, browser.login(realm, CLIENT, identifier, password).code());
        }
    }

    /** The login ends on the login form with the error for a wrong user or password. */
    static void assertRejected(String realm, String identifier, String password) {
        try (Browser browser = new Browser()) {
            assertRejected(browser.login(realm, CLIENT, identifier, password));
        }
    }

    static void assertRejected(LoginResult result) {
        assertTrue(result.isLoginForm());
        assertEquals(INVALID, result.error());
    }

    static String proxyId(String entryRealm, String homeRealmId, String homeUserId) {
        return "f:" + componentId(entryRealm) + ":" + homeRealmId + ":" + homeUserId;
    }

    static String proxyIdOf(String entryRealm, String homeRealm, String username) {
        return proxyId(entryRealm, realmId(homeRealm), userId(homeRealm, username));
    }

    static String userId(String realm, String username) {
        List<UserRepresentation> users = KEYCLOAK.getKeycloakAdminClient().realm(realm).users()
                .searchByUsername(username, true);
        assertEquals(1, users.size());
        return users.get(0).getId();
    }

    static String componentId(String entryRealm) {
        RealmResource entry = KEYCLOAK.getKeycloakAdminClient().realm(entryRealm);
        return entry.components().query(realmId(entryRealm), USER_STORAGE).stream()
                .filter(component -> STORAGE_ID.equals(component.getProviderId()))
                .findFirst().orElseThrow().getId();
    }

    static String realmId(String realm) {
        return KEYCLOAK.getKeycloakAdminClient().realm(realm).toRepresentation().getId();
    }
}
