package net.exoego.keycloak.crossrealm;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;

/** Shows the users of the home realms in the entry realm. It finds a user by ID only. */
final class CrossRealmUserStorageProvider implements UserStorageProvider, UserLookupProvider {

    private final KeycloakSession session;
    private final ComponentModel component;

    CrossRealmUserStorageProvider(KeycloakSession session, ComponentModel component) {
        this.session = session;
        this.component = component;
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        return HomeUserId.parse(StorageId.externalId(id))
                .flatMap(home -> session.getProvider(CandidateLookup.class).find(realm, home.realmId(), home.userId())
                        .map(candidate -> HomeRealmContext.call(session, candidate.realm(),
                                () -> new CrossRealmUser(session, realm, component, home, candidate.user()))))
                .orElse(null);
    }

    // Several users can have one name. Only the authenticator picks one, after the password matched.
    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return null;
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return null;
    }

    @Override
    public void close() {
    }
}
