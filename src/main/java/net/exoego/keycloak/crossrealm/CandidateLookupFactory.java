package net.exoego.keycloak.crossrealm;

import org.keycloak.provider.ProviderFactory;

/** The factory interface of the SPI {@code cross-realm-login}. */
public interface CandidateLookupFactory extends ProviderFactory<CandidateLookup> {
}
