package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.Spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCandidateLookupFactoryTest {

    @Test
    void isTheProviderDefault() {
        assertEquals("default", new DefaultCandidateLookupFactory().getId());
    }

    @Test
    void declaresTheSetting() {
        List<ProviderConfigProperty> properties = new DefaultCandidateLookupFactory().getConfigMetadata();

        assertEquals(1, properties.size());
        assertEquals("excluded-realm-ids", properties.get(0).getName());
    }

    @Test
    void isRegisteredForServiceLoader() {
        assertTrue(ServiceLoader.load(Spi.class).stream()
                .anyMatch(spi -> spi.get().getName().equals("cross-realm-login")));
        assertTrue(ServiceLoader.load(CandidateLookupFactory.class).stream()
                .anyMatch(factory -> factory.get() instanceof DefaultCandidateLookupFactory));
    }
}
