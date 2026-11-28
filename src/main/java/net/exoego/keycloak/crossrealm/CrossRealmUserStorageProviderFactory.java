package net.exoego.keycloak.crossrealm;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.storage.UserStorageProviderFactory;

public class CrossRealmUserStorageProviderFactory implements UserStorageProviderFactory<CrossRealmUserStorageProvider> {

    public static final String ID = "cross-realm-users";

    public CrossRealmUserStorageProviderFactory() {
    }

    @Override
    public CrossRealmUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        return new CrossRealmUserStorageProvider(session, model);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getHelpText() {
        return "Shows the users of the home realms. It stores nothing in this realm.";
    }
}
