package net.exoego.keycloak.crossrealm.testprovider;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Exists only for the integration tests and must never ship.
 * It tells which realm a username belongs to without a login, which this project exists to prevent.
 */
public class CandidateTestEndpointFactory implements RealmResourceProviderFactory {

    public CandidateTestEndpointFactory() {
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new RealmResourceProvider() {
            @Override
            public Object getResource() {
                return new CandidateTestEndpoint(session);
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "cross-realm-test";
    }
}
