package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordLoginIT {

    private static final String ENTRY = "entry";
    private static final String CLIENT = "entry-client";
    private static final String USER_STORAGE = "org.keycloak.storage.UserStorageProvider";
    private static final String STORAGE_ID = "cross-realm-users";
    private static final String AUTHENTICATOR_ID = "cross-realm-password";
    private static final String INVALID = "Invalid username or password.";

    @Test
    void aliceLogsInWithThePasswordOfHomeA() throws Exception {
        IdToken token = loginAtEntry("alice", "alice-password-a");

        assertEquals("alice", token.preferredUsername());
        assertEquals("alice@home-a.example.com", token.email());
        assertEquals(KeycloakEnvironment.realmUrl(ENTRY), token.issuer());
        assertEquals(proxyIdOf("home-a", "alice"), token.subject());
    }

    @Test
    void aliceLogsInWithThePasswordOfHomeB() throws Exception {
        IdToken token = loginAtEntry("alice", "alice-password-b");

        assertEquals("alice", token.preferredUsername());
        assertEquals(proxyIdOf("home-b", "alice"), token.subject());
    }

    @Test
    void aWrongPasswordLooksLikeAnUnknownIdentifier() throws Exception {
        try (Browser browser = new Browser()) {
            LoginResult wrongPassword = browser.login(ENTRY, CLIENT, "alice", "not-the-password");
            LoginResult unknown = browser.login(ENTRY, CLIENT, "nobody-" + UUID.randomUUID(), "not-the-password");

            assertTrue(wrongPassword.isLoginForm());
            assertEquals(INVALID, wrongPassword.error());
            assertTrue(unknown.isLoginForm());
            assertEquals(wrongPassword.error(), unknown.error());
            assertEquals(wrongPassword.formShape(), unknown.formShape());
        }
    }

    // It does not assert on time, because that would be flaky. It prints the numbers for a person to read.
    // Bob has one candidate and alice has two, so the numbers also show the cost of each hash.
    @Test
    void anEmptyPasswordFailsLikeAWrongOne() {
        try (Browser browser = new Browser()) {
            LoginResult result = browser.login(ENTRY, CLIENT, "alice", "");

            assertTrue(result.isLoginForm());
            assertEquals(INVALID, result.error());
        }
    }

    @Test
    void theSamePasswordInTwoHomeRealmsFailsLikeAWrongPassword() {
        String username = "twin-" + UUID.randomUUID();
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm(); Browser browser = new Browser()) {
            one.addUser(username, "same-password", true);
            two.addUser(username, "same-password", true);

            LoginResult result = browser.login(ENTRY, CLIENT, username, "same-password");

            assertTrue(result.isLoginForm());
            assertEquals(INVALID, result.error());
        }
    }

    @Test
    void aDisabledUserNeverMatches() {
        String username = "disabled-" + UUID.randomUUID();
        try (TempRealm home = new TempRealm(); Browser browser = new Browser()) {
            home.addUser(username, "the-password", false);

            LoginResult result = browser.login(ENTRY, CLIENT, username, "the-password");

            assertTrue(result.isLoginForm());
            assertEquals(INVALID, result.error());
        }
    }

    @Test
    void theEnabledUserLogsInWhenTheSameIdentifierIsDisabledInAnotherRealm() throws Exception {
        String username = "pair-" + UUID.randomUUID();
        try (TempRealm disabledHome = new TempRealm(); TempRealm enabledHome = new TempRealm();
                Browser browser = new Browser(); Browser other = new Browser()) {
            disabledHome.addUser(username, "password-of-the-disabled", false);
            String enabledId = enabledHome.addUser(username, "password-of-the-enabled", true);

            IdToken token = browser.exchange(ENTRY, CLIENT,
                    browser.login(ENTRY, CLIENT, username, "password-of-the-enabled").code());
            assertEquals(username, token.preferredUsername());
            assertEquals(proxyId(enabledHome.id(), enabledId), token.subject());

            LoginResult disabledPassword = other.login(ENTRY, CLIENT, username, "password-of-the-disabled");
            assertTrue(disabledPassword.isLoginForm());
            assertEquals(INVALID, disabledPassword.error());
        }
    }

    @Test
    void aVerifiedEmailAddressLogsIn() throws Exception {
        IdToken token = loginAtEntry("alice@home-a.example.com", "alice-password-a");

        assertEquals("alice", token.preferredUsername());
        assertEquals(proxyIdOf("home-a", "alice"), token.subject());
    }

    @Test
    void theEntryRealmListsNoUser() throws Exception {
        loginAtEntry("alice", "alice-password-a");
        RealmResource entry = KEYCLOAK.getKeycloakAdminClient().realm(ENTRY);

        assertEquals(List.of(), entry.users().list());
        assertEquals(0, entry.users().count());
        assertEquals(List.of(), entry.users().search("alice"));
        assertEquals(List.of(), entry.users().searchByUsername("alice", true));
        assertEquals(List.of(), entry.users().searchByEmail("alice@home-a.example.com", true));
    }

    @Test
    void theEntryRealmStoresNothingAboutTheUser() throws Exception {
        IdToken token = loginAtEntry("alice", "alice-password-a");
        String proxyId = token.subject();
        String entryId = KEYCLOAK.getKeycloakAdminClient().realm(ENTRY).toRepresentation().getId();

        assertEquals(0, Database.count("user_entity", "realm_id = '" + entryId + "' or id = '" + proxyId + "'"));
        assertEquals(0, Database.count("credential", "user_id = '" + proxyId + "'"));
        List<String> tables = Database.tables("fed_user_");
        assertFalse(tables.isEmpty());
        for (String table : tables) {
            assertEquals(0, Database.count(table, "true"), table);
        }
        // The check can see a user that is stored.
        assertEquals(1, Database.count("credential", "user_id = '" + homeUserId("home-a", "alice") + "'"));
    }

    @Test
    void theAdminApiShowsTheProfileOfTheHomeUser() throws Exception {
        String proxyId = loginAtEntry("alice", "alice-password-a").subject();

        UserRepresentation user = KEYCLOAK.getKeycloakAdminClient().realm(ENTRY).users().get(proxyId).toRepresentation();

        assertEquals("alice", user.getUsername());
        assertEquals("alice@home-a.example.com", user.getEmail());
        assertEquals("Alice", user.getFirstName());
        assertEquals("Anderson", user.getLastName());
    }

    @Test
    void theEntryRealmListsNoCredentialOfTheUser() throws Exception {
        String proxyId = loginAtEntry("alice", "alice-password-a").subject();

        assertEquals(List.of(), KEYCLOAK.getKeycloakAdminClient().realm(ENTRY).users().get(proxyId).credentials());
    }

    @Test
    void aPasswordResetOfTheUserFailsAndStoresNothing() throws Exception {
        String proxyId = loginAtEntry("alice", "alice-password-a").subject();
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue("new-password");
        credential.setTemporary(false);
        UserResource user = KEYCLOAK.getKeycloakAdminClient().realm(ENTRY).users().get(proxyId);

        assertThrows(WebApplicationException.class, () -> user.resetPassword(credential));

        assertEquals(0, Database.count("fed_user_credential", "user_id = '" + proxyId + "'"));
        assertEquals(0, Database.count("fed_user_credential", "true"));
        assertEquals(List.of(), user.credentials());
    }

    @Test
    void aMemberOfAnOrganizationLogsInAtAnEntryRealmWithAnOrganization() throws Exception {
        String username = "member-" + UUID.randomUUID();
        try (TempEntryRealm entry = new TempEntryRealm(true); TempRealm home = new TempRealm()) {
            entry.addOrganization("entry-org", "entry-org.example");
            RealmRepresentation rep = home.resource().toRepresentation();
            rep.setOrganizationsEnabled(true);
            home.resource().update(rep);
            OrganizationRepresentation organization = new OrganizationRepresentation();
            organization.setName("home-org");
            organization.setAlias("home-org");
            organization.addDomain(new OrganizationDomainRepresentation("home-org.example"));
            String organizationId;
            try (Response response = home.resource().organizations().create(organization)) {
                organizationId = CreatedResponseUtil.getCreatedId(response);
            }
            String userId = home.addUser(username, "the-password", true);
            home.resource().organizations().get(organizationId).members().addMember(userId).close();

            IdToken token = loginAt(entry.name(), username, "the-password");

            assertEquals(username, token.preferredUsername());
            assertEquals(proxyId(entry.name(), home.id(), userId), token.subject());
        }
    }

    @Test
    void aUserOfAnExcludedRealmCannotLogIn() {
        assertCannotLogIn("alice", "alice-password-excluded");
    }

    @Test
    void aUserOfADisabledRealmCannotLogIn() {
        assertCannotLogIn("alice", "alice-password-disabled");
    }

    @Test
    void anAdministratorOfTheMasterRealmCannotLogIn() {
        assertCannotLogIn(KEYCLOAK.getAdminUsername(), KEYCLOAK.getAdminPassword());
    }

    @Test
    void failsWithTheGenericErrorPageWhenTheRealmHasNoUserStorage() {
        try (TempEntryRealm entry = new TempEntryRealm(false); Browser browser = new Browser()) {
            LoginResult result = browser.login(entry.name(), CLIENT, "alice", "alice-password-a");

            assertFalse(result.isLoginForm());
            assertEquals("Unexpected error when handling authentication request to identity provider.",
                    result.error());
            assertTrue(KEYCLOAK.getLogs().lines().anyMatch(line -> line.contains(" ERROR ")
                    && line.contains("The realm " + entry.name() + " has no user storage of the provider cross-realm-users")));
        }
    }

    @Test
    void keycloakListsTheProviders() {
        var info = KEYCLOAK.getKeycloakAdminClient().serverInfo().getInfo();

        assertTrue(info.getProviders().get("authenticator").getProviders()
                .containsKey(AUTHENTICATOR_ID));
        assertTrue(info.getProviders().get("storage").getProviders()
                .containsKey(STORAGE_ID));
        assertTrue(info.getComponentTypes().get(USER_STORAGE).stream()
                .anyMatch(type -> STORAGE_ID.equals(type.getId())));
        assertTrue(info.getProviders().get("cross-realm-login").getProviders().containsKey("default"));
    }

    private static void assertCannotLogIn(String username, String password) {
        try (Browser browser = new Browser()) {
            LoginResult result = browser.login(ENTRY, CLIENT, username, password);

            assertTrue(result.isLoginForm());
            assertEquals(INVALID, result.error());
        }
    }

    private static IdToken loginAtEntry(String identifier, String password) throws Exception {
        return loginAt(ENTRY, identifier, password);
    }

    private static IdToken loginAt(String realm, String identifier, String password) throws Exception {
        try (Browser browser = new Browser()) {
            return browser.exchange(realm, CLIENT, browser.login(realm, CLIENT, identifier, password).code());
        }
    }

    private static String proxyIdOf(String homeRealm, String username) {
        return proxyId(ENTRY, realmId(homeRealm), homeUserId(homeRealm, username));
    }

    private static String proxyId(String homeRealmId, String homeUserId) {
        return proxyId(ENTRY, homeRealmId, homeUserId);
    }

    private static String proxyId(String entryRealm, String homeRealmId, String homeUserId) {
        return "f:" + componentId(entryRealm) + ":" + homeRealmId + ":" + homeUserId;
    }

    private static String componentId(String entryRealm) {
        RealmResource entry = KEYCLOAK.getKeycloakAdminClient().realm(entryRealm);
        return entry.components().query(entry.toRepresentation().getId(), USER_STORAGE).stream()
                .filter(component -> STORAGE_ID.equals(component.getProviderId()))
                .findFirst().orElseThrow().getId();
    }

    private static String realmId(String realm) {
        return KEYCLOAK.getKeycloakAdminClient().realm(realm).toRepresentation().getId();
    }

    private static String homeUserId(String realm, String username) {
        List<UserRepresentation> users = KEYCLOAK.getKeycloakAdminClient().realm(realm).users()
                .searchByUsername(username, true);
        assertEquals(1, users.size());
        return users.get(0).getId();
    }
}
