package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import net.exoego.keycloak.crossrealm.CandidateEndpoint.Checked;

import static net.exoego.keycloak.crossrealm.TestRealms.withRealm;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeRealmCredentialsIT {

    // The checks run at the entry realm. It has no user.
    private static final String ENTRY = "entry";

    @Test
    void checksThePasswordOfEachHomeRealmInOneRequest() {
        assertEquals(Map.of("home-a", true, "home-b", false), validity("alice", "alice-password-a"));
        assertEquals(Map.of("home-a", false, "home-b", true), validity("alice", "alice-password-b"));
    }

    @Test
    void acceptsTheSamePasswordInTwoHomeRealms() {
        String username = "twin-" + UUID.randomUUID();
        withRealm(first -> withRealm(second -> {
            createUser(first, username, "shared-password");
            createUser(second, username, "shared-password");

            Map<String, Boolean> result = validity(username, "shared-password");
            assertEquals(Map.of(name(first), true, name(second), true), result);
            assertEquals(Map.of(name(first), false, name(second), false), validity(username, "other-password"));
        }, false), false);
    }

    @Test
    void acceptsNoRealmForAWrongPassword() {
        assertEquals(Map.of("home-a", false, "home-b", false), validity("alice", "wrong"));
    }

    @Test
    void doesNotKeepAStalePassword() {
        withRealm(realm -> {
            String username = "user-" + UUID.randomUUID();
            String id = createUser(realm, username, "old-password");
            assertEquals(Map.of(name(realm), true), validity(username, "old-password"));

            realm.users().get(id).resetPassword(password("new-password"));

            assertEquals(Map.of(name(realm), false), validity(username, "old-password"));
            assertEquals(Map.of(name(realm), true), validity(username, "new-password"));
        }, false);
    }

    @Test
    void rejectsAUserWithoutAPassword() {
        assertEquals(Map.of("home-a", false), validity("dave", "anything"));
    }

    @Test
    void checksTheCredentialsOfADisabledUser() {
        withRealm(realm -> {
            String username = "user-" + UUID.randomUUID();
            String id = createUser(realm, username, "a-password");
            UserRepresentation user = realm.users().get(id).toRepresentation();
            user.setEnabled(false);
            realm.users().get(id).update(user);

            assertEquals(Map.of(name(realm), true), validity(username, "a-password"));
        }, false);
    }

    private static Map<String, Boolean> validity(String identifier, String password) {
        return toMap(CandidateEndpoint.checkPassword(ENTRY, identifier, password));
    }

    private static Map<String, Boolean> toMap(List<Checked> checked) {
        return checked.stream().collect(Collectors.toMap(Checked::realmName, Checked::valid));
    }

    private static String name(RealmResource realm) {
        return realm.toRepresentation().getRealm();
    }

    private static CredentialRepresentation password(String value) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(value);
        credential.setTemporary(false);
        return credential;
    }

    private static String createUser(RealmResource realm, String username, String password) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setCredentials(List.of(password(password)));
        try (Response response = realm.users().create(user)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }
}
