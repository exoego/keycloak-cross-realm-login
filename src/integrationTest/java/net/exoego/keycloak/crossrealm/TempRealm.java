package net.exoego.keycloak.crossrealm;

import java.util.List;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/** A home realm with a random name that exists for one test. */
final class TempRealm extends TempRealmBase {

    TempRealm() {
        super("tmp-", enabledRealm());
    }

    /** Creates a user with a verified email address and returns the ID. */
    String addUser(String username, String password, boolean enabled) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(username + "@" + name() + ".example");
        user.setEmailVerified(true);
        user.setEnabled(enabled);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));
        try (Response response = resource().users().create(user)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    private static RealmRepresentation enabledRealm() {
        RealmRepresentation rep = new RealmRepresentation();
        rep.setEnabled(true);
        return rep;
    }
}
