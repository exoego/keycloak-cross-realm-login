package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Calls the endpoint of the test provider jar. The realm in the URL is the context realm of the lookup. */
final class CandidateEndpoint {

    record Found(String realmName, String realmId, String userId, String username, boolean enabled) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private CandidateEndpoint() {
    }

    static List<Found> candidates(String entryRealm, String identifier) {
        return list(entryRealm, "candidates?identifier=" + encode(identifier));
    }

    /** The lookup gets an entry realm that is not the context realm. */
    static List<Found> candidatesInContext(String contextRealm, String entryRealm, String identifier) {
        return list(contextRealm, "candidates?identifier=" + encode(identifier) + "&entryRealm=" + encode(entryRealm));
    }

    private static List<Found> list(String contextRealm, String pathAndQuery) {
        HttpResponse<String> response = get(contextRealm, pathAndQuery);
        check(response);
        try {
            return JSON.readValue(response.body(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException(response.body(), e);
        }
    }

    static Optional<Found> candidate(String entryRealm, String realmId, String userId) {
        return find(entryRealm, "candidate?realmId=" + encode(realmId) + "&userId=" + encode(userId));
    }

    /** Looks the identifier up first, in the same session of Keycloak. */
    static Optional<Found> candidateAfterLookup(String entryRealm, String identifier, String realmId, String userId) {
        return find(entryRealm, "candidate?realmId=" + encode(realmId) + "&userId=" + encode(userId)
                + "&after=" + encode(identifier));
    }

    private static Optional<Found> find(String entryRealm, String pathAndQuery) {
        HttpResponse<String> response = get(entryRealm, pathAndQuery);
        if (response.statusCode() == 204) {
            return Optional.empty();
        }
        check(response);
        try {
            return Optional.of(JSON.readValue(response.body(), Found.class));
        } catch (IOException e) {
            throw new IllegalStateException(response.body(), e);
        }
    }

    /** The realm names of the candidates, in the order the lookup returns them. */
    static List<String> realmNames(String entryRealm, String identifier) {
        return candidates(entryRealm, identifier).stream().map(Found::realmName).toList();
    }

    private static HttpResponse<String> get(String entryRealm, String pathAndQuery) {
        URI uri = URI.create(KeycloakEnvironment.realmUrl(entryRealm) + "/cross-realm-test/" + pathAndQuery);
        try {
            return HTTP.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void check(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException(response.statusCode() + " " + response.body());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
