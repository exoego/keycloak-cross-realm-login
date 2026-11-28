package net.exoego.keycloak.crossrealm;

import java.util.UUID;

import jakarta.ws.rs.NotFoundException;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.ADMIN;
import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.EXCLUDED_REALM_ID;
import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;
import static net.exoego.keycloak.crossrealm.Logins.CLIENT;
import static net.exoego.keycloak.crossrealm.Logins.assertRejected;
import static net.exoego.keycloak.crossrealm.Logins.logIn;
import static net.exoego.keycloak.crossrealm.Logins.proxyId;
import static net.exoego.keycloak.crossrealm.Logins.proxyIdOf;
import static net.exoego.keycloak.crossrealm.Logins.realmId;
import static net.exoego.keycloak.crossrealm.Logins.userId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests which users the entry realm finds in the home realms.
 * A login finds users by an identifier. The admin API finds a user by a proxy user ID.
 * A negative test gives the user a password, so the login would work if the rule did not hold.
 */
class CandidatesIT {

    private static final String ENTRY = "entry";

    @Test
    void ignoresTheCaseOfTheIdentifier() throws Exception {
        assertEquals("alice", logIn(ENTRY, "ALICE", "alice-password-a").preferredUsername());
        assertEquals("bob", logIn(ENTRY, "Bob@Home-A.Example.COM", "bob-password").preferredUsername());
    }

    @Test
    void ignoresSpacesAroundTheIdentifier() throws Exception {
        assertEquals("bob", logIn(ENTRY, "  bob ", "bob-password").preferredUsername());
    }

    @Test
    void aBlankIdentifierIsRejected() {
        assertRejected(ENTRY, "", "bob-password");
        assertRejected(ENTRY, "   ", "bob-password");
    }

    @Test
    void aMissingUsernameIsRejected() {
        try (Browser browser = new Browser()) {
            assertRejected(browser.loginWithoutUsername(ENTRY, CLIENT, "bob-password"));
        }
    }

    @Test
    void aUsernameOf255CharactersLogsInAndOneMoreIsRejected() throws Exception {
        String username = "u".repeat(255);
        try (TempRealm home = new TempRealm()) {
            home.addUser(username, null, true, "the-password");

            assertEquals(username, logIn(ENTRY, username, "the-password").preferredUsername());
            assertRejected(ENTRY, username + "u", "the-password");
        }
    }

    @Test
    void anUnverifiedEmailAddressDoesNotLogIn() throws Exception {
        assertRejected(ENTRY, "dave@home-a.example.com", "dave-password");
        assertEquals("dave", logIn(ENTRY, "dave", "dave-password").preferredUsername());
    }

    @Test
    void aRealmWithoutLoginByEmailDoesNotMatchByEmail() throws Exception {
        assertRejected(ENTRY, "erin@home-no-email-login.example.com", "erin-password");
        assertEquals("erin", logIn(ENTRY, "erin", "erin-password").preferredUsername());
    }

    @Test
    void twoUsersWithOneEmailAddressDoNotMatchByEmailButMatchByUsername() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "twin-" + suffix + "@twins.example";
        try (TempRealm home = new TempRealm()) {
            home.allowDuplicateEmails();
            home.addUser("twin-1-" + suffix, email, true, "the-password");
            home.addUser("twin-2-" + suffix, email, true, "the-password");
            home.allowLoginWithEmail();

            assertRejected(ENTRY, email, "the-password");
            assertEquals("twin-1-" + suffix, logIn(ENTRY, "twin-1-" + suffix, "the-password").preferredUsername());
            assertEquals("twin-2-" + suffix, logIn(ENTRY, "twin-2-" + suffix, "the-password").preferredUsername());
        }
    }

    @Test
    void anUnverifiedEmailHidesAUsernameThatLooksLikeIt() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String lookAlike = "shared-" + suffix + "@example.com";
        try (TempRealm home = new TempRealm()) {
            // Keycloak refuses this pair while the realm allows login with email, so that comes last.
            home.forbidLoginWithEmail();
            home.addUser(lookAlike, "x-" + suffix + "@example.com", false, "password-of-the-name");
            String ownerId = home.addUser("owner-" + suffix, lookAlike, false, "password-of-the-email");
            home.allowLoginWithEmail();

            assertRejected(ENTRY, lookAlike, "password-of-the-name");
            assertRejected(ENTRY, lookAlike, "password-of-the-email");

            UserRepresentation owner = home.resource().users().get(ownerId).toRepresentation();
            owner.setEmailVerified(true);
            home.resource().users().get(ownerId).update(owner);

            assertEquals("owner-" + suffix, logIn(ENTRY, lookAlike, "password-of-the-email").preferredUsername());
            assertRejected(ENTRY, lookAlike, "password-of-the-name");
        }
    }

    @Test
    void aUserWhoseUsernameIsItsUnverifiedEmailLogsIn() throws Exception {
        String name = "carol-" + UUID.randomUUID() + "@tenant.example";
        try (TempRealm home = new TempRealm()) {
            home.addUser(name, name, false, "the-password");

            assertEquals(name, logIn(ENTRY, name, "the-password").preferredUsername());
        }
    }

    @Test
    void theEmailOfAServiceAccountHidesAUsernameThatLooksLikeIt() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String lookAlike = "ops-" + suffix + "@example.com";
        try (TempRealm home = new TempRealm()) {
            // Keycloak refuses this pair while the realm allows login with email, so that comes last.
            home.forbidLoginWithEmail();
            home.addUser(lookAlike, "other-" + suffix + "@example.com", true, "password-of-the-name");
            home.addServiceAccount("machine-" + suffix, lookAlike);
            home.allowLoginWithEmail();

            assertRejected(ENTRY, lookAlike, "password-of-the-name");
            assertEquals(lookAlike, logIn(ENTRY, "other-" + suffix + "@example.com", "password-of-the-name")
                    .preferredUsername());
        }
    }

    @Test
    void aUserOfTheEntryRealmItselfCannotLogIn() {
        String username = "local-" + UUID.randomUUID();
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage()) {
            entry.addUser(username, "the-password", true);

            assertRejected(entry.name(), username, "the-password");
        }
    }

    @Test
    void showsAHomeUserByItsProxyId() {
        UserRepresentation user = show(ENTRY, proxyIdOf(ENTRY, "home-a", "bob"));

        assertEquals("bob", user.getUsername());
    }

    @Test
    void doesNotShowAUserWithTheIdOfAnotherRealm() {
        String bob = userId("home-a", "bob");

        assertEquals("bob", show(ENTRY, proxyId(ENTRY, realmId("home-a"), bob)).getUsername());
        assertNotFound(ENTRY, proxyId(ENTRY, realmId("home-b"), bob));
        assertNotFound(ENTRY, proxyId(ENTRY, "no-such-realm", bob));
    }

    @Test
    void doesNotShowTheUserOfTheRequestWithTheIdOfAnotherRealm() {
        // The admin API of Keycloak loads the administrator when it checks the token.
        // Keycloak 26.8.0 then returned that user for any realm.
        String admin = userId("master", KEYCLOAK.getAdminUsername());

        assertNotFound(ENTRY, proxyId(ENTRY, realmId("home-a"), admin));
        assertEquals("bob", show(ENTRY, proxyIdOf(ENTRY, "home-a", "bob")).getUsername());
    }

    @Test
    void doesNotShowAnIdThatLooksLikeAUserFederationId() {
        String realmId = realmId("home-a");

        assertNotFound(ENTRY, proxyId(ENTRY, realmId, "f:no-such-component:bob"));
        // Keycloak cannot parse this one.
        assertNotFound(ENTRY, proxyId(ENTRY, realmId, "f:bob"));
        assertEquals("bob", show(ENTRY, proxyIdOf(ENTRY, "home-a", "bob")).getUsername());
    }

    @Test
    void doesNotShowAUserOfTheMasterRealm() {
        assertNotFound(ENTRY, proxyId(ENTRY, realmId("master"), userId("master", KEYCLOAK.getAdminUsername())));
        assertEquals("bob", show(ENTRY, proxyIdOf(ENTRY, "home-a", "bob")).getUsername());
    }

    @Test
    void doesNotShowAUserOfTheEntryRealm() {
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage()) {
            String id = entry.addUser("local-" + UUID.randomUUID(), "the-password", true);

            assertNotFound(entry.name(), proxyId(entry.name(), entry.id(), id));
            assertEquals("bob", show(entry.name(), proxyId(entry.name(), realmId("home-a"), userId("home-a", "bob")))
                    .getUsername());
        }
    }

    @Test
    void doesNotShowAUserOfAnExcludedRealm() {
        assertEquals(EXCLUDED_REALM_ID, realmId("home-excluded"));

        assertNotFound(ENTRY, proxyIdOf(ENTRY, "home-excluded", "alice"));
    }

    @Test
    void doesNotShowAUserOfADisabledRealm() {
        assertNotFound(ENTRY, proxyIdOf(ENTRY, "home-disabled", "alice"));
    }

    @Test
    void doesNotShowAServiceAccount() {
        RealmResource homeA = realm("home-a");
        String clientId = homeA.clients().findByClientId("machine").get(0).getId();
        String accountId = homeA.clients().get(clientId).getServiceAccountUser().getId();

        assertNotFound(ENTRY, proxyId(ENTRY, realmId("home-a"), accountId));
    }

    @Test
    void doesNotShowAMissingUser() {
        assertNotFound(ENTRY, proxyId(ENTRY, realmId("home-a"), "no-such-user"));
    }

    private static UserRepresentation show(String entryRealm, String proxyId) {
        return realm(entryRealm).users().get(proxyId).toRepresentation();
    }

    private static void assertNotFound(String entryRealm, String proxyId) {
        assertThrows(NotFoundException.class, () -> show(entryRealm, proxyId));
    }

    private static RealmResource realm(String name) {
        return ADMIN.realm(name);
    }
}
