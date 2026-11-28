package net.exoego.keycloak.crossrealm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.keycloak.representations.idm.PartialImportRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import static net.exoego.keycloak.crossrealm.Logins.CLIENT;
import static net.exoego.keycloak.crossrealm.Logins.proxyId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasskeysInSeveralRealmsIT {

    private static final String PASSWORD = "the-password";

    private final String username = "person-" + UUID.randomUUID();

    @Test
    void passkeysOfOnePersonInTwoHomeRealmsLookTheSameAndEachLogsInToItsOwnRealm() throws Exception {
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage(); TempRealm first = new TempRealm();
                TempRealm second = new TempRealm(); Browser browser = new Browser()) {
            entry.allowPasskeys();
            VirtualAuthenticator authenticator = browser.addVirtualAuthenticator();
            String firstUserId = registerPasskey(first, browser);
            JsonObject firstCredential = authenticator.credentials().get(0);
            String secondUserId = registerPasskey(second, browser);
            JsonObject secondCredential = authenticator.credentials().stream()
                    .filter(credential -> !credential.get("credentialId").equals(firstCredential.get("credentialId")))
                    .findFirst().orElseThrow();

            for (JsonObject credential : List.of(firstCredential, secondCredential)) {
                assertEquals("localhost", credential.get("rpId").getAsString());
                assertEquals(username, credential.get("userName").getAsString());
                assertEquals(username, credential.get("userDisplayName").getAsString());
            }

            assertEquals(proxyId(entry.name(), first.id(), firstUserId),
                    loginWithout(browser, authenticator, entry, secondCredential));
            assertEquals(proxyId(entry.name(), second.id(), secondUserId),
                    loginWithout(browser, authenticator, entry, firstCredential));
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "browser.chooser", matches = "true")
    @ResourceLock("os-keyboard")
    void theChooserOfChromeLetsThePersonPickEachPasskey() throws Exception {
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage(); TempRealm first = new TempRealm();
                TempRealm second = new TempRealm(); Browser registering = new Browser();
                Browser browser = Browser.withChooser()) {
            entry.allowPasskeys();
            VirtualAuthenticator source = registering.addVirtualAuthenticator();
            String firstUserId = registerPasskey(first, registering);
            String secondUserId = registerPasskey(second, registering);
            VirtualAuthenticator authenticator = browser.addVirtualAuthenticator(true);
            for (JsonObject credential : source.credentials()) {
                authenticator.add(credential);
            }

            String picked = browser.exchange(entry.name(), CLIENT,
                    browser.loginWithPasskeyChooser(entry.name(), CLIENT, username, 0).code()).subject();
            browser.clearCookies();
            String pickedNext = browser.exchange(entry.name(), CLIENT,
                    browser.loginWithPasskeyChooser(entry.name(), CLIENT, username, 1).code()).subject();

            assertEquals(Set.of(proxyId(entry.name(), first.id(), firstUserId),
                    proxyId(entry.name(), second.id(), secondUserId)), new HashSet<>(List.of(picked, pickedNext)));
        }
    }

    @Test
    void aUserCannotGetTheIdOfAUserOfAnotherRealm() {
        try (TempRealm first = new TempRealm(); TempRealm second = new TempRealm()) {
            String userId = first.addUser(username, PASSWORD, true);
            UserRepresentation copy = new UserRepresentation();
            copy.setId(userId);
            copy.setUsername(username);
            PartialImportRepresentation imported = new PartialImportRepresentation();
            imported.setUsers(List.of(copy));
            imported.setIfResourceExists("FAIL");

            try (Response response = second.resource().partialImport(imported)) {
                assertEquals(409, response.getStatus(), response.readEntity(String.class));
            }

            assertTrue(second.resource().users().searchByUsername(username, true).isEmpty());
        }
    }

    private String loginWithout(Browser browser, VirtualAuthenticator authenticator, TempEntryRealm entry,
            JsonObject hidden) throws Exception {
        authenticator.remove(hidden);
        try {
            return browser.exchange(entry.name(), CLIENT, browser.loginWithPasskey(entry.name(), CLIENT).code())
                    .subject();
        } finally {
            authenticator.add(hidden);
            browser.clearCookies();
        }
    }

    private String registerPasskey(TempRealm home, Browser browser) {
        return Logins.registerPasskey(home, browser, username, PASSWORD);
    }
}
