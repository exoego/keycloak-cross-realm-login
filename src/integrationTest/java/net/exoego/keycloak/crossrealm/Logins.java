package net.exoego.keycloak.crossrealm;

import java.util.List;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Logins {

    static final String CLIENT = "entry-client";
    static final String USER_STORAGE = "org.keycloak.storage.UserStorageProvider";
    static final String STORAGE_ID = "cross-realm-users";

    private static final String INVALID = "Invalid username or password.";
    private static final String HOME_CLIENT = "home-client";

    private Logins() {
    }

    static IdToken logIn(String realm, String identifier, String password) throws Exception {
        try (Browser browser = new Browser()) {
            return browser.exchange(realm, CLIENT, browser.login(realm, CLIENT, identifier, password).code());
        }
    }

    static String registerPasskey(TempRealm home, Browser browser, String username, String password) {
        home.addPublicClient(HOME_CLIENT);
        home.allowPasskeys();
        String userId = home.addUser(username, password, true);
        home.setName(userId, "Pass", "Key");
        home.requireAction(userId, "webauthn-register-passwordless");
        browser.loginAndRegisterPasskey(home.name(), HOME_CLIENT, username, password).code();
        browser.clearCookies();
        return userId;
    }

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
        List<UserRepresentation> users = ADMIN.realm(realm).users()
                .searchByUsername(username, true);
        assertEquals(1, users.size());
        return users.get(0).getId();
    }

    static String componentId(String entryRealm) {
        RealmResource entry = ADMIN.realm(entryRealm);
        return entry.components().query(realmId(entryRealm), USER_STORAGE).stream()
                .filter(component -> STORAGE_ID.equals(component.getProviderId()))
                .findFirst().orElseThrow().getId();
    }

    static String realmId(String realm) {
        return ADMIN.realm(realm).toRepresentation().getId();
    }
}
