package net.exoego.keycloak.crossrealm;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

final class SharedRealmKeys {

    private static final String KEY_PROVIDER = "org.keycloak.keys.KeyProvider";

    private static final class Holder {
        static final String PRIVATE_KEY = generate();

        private static String generate() {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
                return Base64.getEncoder().encodeToString(encoded);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private SharedRealmKeys() {
    }

    static void addTo(RealmRepresentation rep) {
        MultivaluedHashMap<String, ComponentExportRepresentation> components = rep.getComponents();
        if (components == null) {
            components = new MultivaluedHashMap<>();
            rep.setComponents(components);
        }
        components.add(KEY_PROVIDER, provider("rsa", Map.of("keyUse", "SIG", "privateKey", Holder.PRIVATE_KEY)));
        components.add(KEY_PROVIDER, provider("rsa-enc", Map.of("keyUse", "ENC", "algorithm", "RSA-OAEP",
                "privateKey", Holder.PRIVATE_KEY)));
        components.add(KEY_PROVIDER, provider("hmac-generated", Map.of("algorithm", "HS512")));
        components.add(KEY_PROVIDER, provider("aes-generated", Map.of()));
    }

    private static ComponentExportRepresentation provider(String id, Map<String, String> config) {
        ComponentExportRepresentation component = new ComponentExportRepresentation();
        component.setName(id);
        component.setProviderId(id);
        MultivaluedHashMap<String, String> map = new MultivaluedHashMap<>();
        map.putSingle("priority", "100");
        config.forEach(map::putSingle);
        component.setConfig(map);
        return component;
    }
}
