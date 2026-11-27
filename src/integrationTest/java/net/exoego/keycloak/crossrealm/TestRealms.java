package net.exoego.keycloak.crossrealm;

import java.util.UUID;
import java.util.function.Consumer;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;

final class TestRealms {

    private TestRealms() {
    }

    static RealmResource realm(String name) {
        return KEYCLOAK.getKeycloakAdminClient().realm(name);
    }

    // The realm has a random name, so tests do not see each other's users.
    static void withRealm(Consumer<RealmResource> test, boolean allowDuplicateEmails) {
        String name = "tmp-" + UUID.randomUUID();
        RealmRepresentation rep = new RealmRepresentation();
        rep.setRealm(name);
        rep.setEnabled(true);
        rep.setDuplicateEmailsAllowed(allowDuplicateEmails);
        rep.setLoginWithEmailAllowed(false);
        KEYCLOAK.getKeycloakAdminClient().realms().create(rep);
        try {
            test.accept(realm(name));
        } finally {
            realm(name).remove();
        }
    }
}
