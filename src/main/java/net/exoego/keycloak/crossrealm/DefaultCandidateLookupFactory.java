package net.exoego.keycloak.crossrealm;

import java.util.List;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

/**
 * Setting:
 * <pre>
 * --spi-cross-realm-login--default--excluded-realm-ids=&lt;realm id&gt;,&lt;realm id&gt;
 * </pre>
 */
public class DefaultCandidateLookupFactory implements CandidateLookupFactory {

    public static final String ID = "default";

    static final String EXCLUDED_REALM_IDS = "excluded-realm-ids";

    private Settings settings = Settings.of(null);

    /** Keycloak creates the factory through {@link java.util.ServiceLoader}. */
    public DefaultCandidateLookupFactory() {
    }

    @Override
    public CandidateLookup create(KeycloakSession session) {
        return new DefaultCandidateLookup(session, settings);
    }

    @Override
    public void init(Config.Scope config) {
        settings = Settings.of(config.get(EXCLUDED_REALM_IDS));
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(EXCLUDED_REALM_IDS)
                .type(ProviderConfigProperty.STRING_TYPE)
                .label("Excluded realm IDs")
                .helpText("IDs of realms that are never searched, separated by commas.")
                .add()
                .build();
    }
}
