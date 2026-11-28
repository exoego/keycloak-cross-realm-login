package net.exoego.keycloak.crossrealm;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static net.exoego.keycloak.crossrealm.Logins.CLIENT;
import static net.exoego.keycloak.crossrealm.Logins.proxyId;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeRealmRehashIT {

    private static final String PASSWORD = "the-password";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String username = "rehash-" + UUID.randomUUID();

    @Test
    void aLoginAtTheEntryRealmRehashesThePasswordByThePolicyOfTheHomeRealm() throws Exception {
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage(); TempRealm home = new TempRealm();
                Browser browser = new Browser()) {
            entry.setPasswordPolicy("hashAlgorithm(pbkdf2-sha512) and hashIterations(2000)");
            String userId = home.addUser(username, PASSWORD, true);
            home.setPasswordPolicy("hashAlgorithm(pbkdf2-sha256) and hashIterations(1000)");

            IdToken token = browser.exchange(entry.name(), CLIENT, browser.login(entry.name(), CLIENT, username, PASSWORD).code());

            assertEquals(proxyId(entry.name(), home.id(), userId), token.subject());
            Await.until("a rehash by the policy of the home realm", () -> hashedWith(home, userId, "pbkdf2-sha256", 1000));
        }
    }

    private static boolean hashedWith(TempRealm home, String userId, String algorithm, int iterations) {
        return home.resource().users().get(userId).credentials().stream()
                .filter(credential -> "password".equals(credential.getType()))
                .anyMatch(credential -> {
                    try {
                        JsonNode data = JSON.readTree(credential.getCredentialData());
                        return algorithm.equals(data.get("algorithm").asText())
                                && iterations == data.get("hashIterations").asInt();
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }
}
