package net.exoego.keycloak.crossrealm;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/** The SPI {@code cross-realm-login}. Keycloak finds it through {@link java.util.ServiceLoader}. */
public class CrossRealmLoginSpi implements Spi {

    public static final String NAME = "cross-realm-login";

    public CrossRealmLoginSpi() {
    }

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return CandidateLookup.class;
    }

    @Override
    public Class<? extends ProviderFactory<?>> getProviderFactoryClass() {
        return CandidateLookupFactory.class;
    }
}
