package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;

/** A home realm with a random name that exists for one test. */
final class TempRealm implements AutoCloseable {

    private final String name = "tmp-" + UUID.randomUUID();
    private final RealmResource realm;

    TempRealm() {
        RealmRepresentation rep = new RealmRepresentation();
        rep.setRealm(name);
        rep.setEnabled(true);
        KEYCLOAK.getKeycloakAdminClient().realms().create(rep);
        realm = KEYCLOAK.getKeycloakAdminClient().realm(name);
    }

    RealmResource resource() {
        return realm;
    }

    String id() {
        return realm.toRepresentation().getId();
    }

    /** Creates a user with a verified email address and returns the ID. */
    String addUser(String username, String password, boolean enabled) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(username + "@" + name + ".example");
        user.setEmailVerified(true);
        user.setEnabled(enabled);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));
        try (Response response = realm.users().create(user)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    @Override
    public void close() {
        realm.remove();
    }
}
