package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;

/**
 * Stands for the browser of one person. Each instance has its own cookies, so it has its own sessions.
 */
final class Browser {

    /** Every client of the realm files has this redirect URI. Nothing listens there. */
    static final String REDIRECT_URI = "http://localhost:3000/callback";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_REDIRECTS = 20;

    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookiesOverHttp())
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Opens the login of a client at a realm and types the username and the password.
     * A login that Keycloak does not accept ends on a page, so look at the result.
     */
    LoginResult login(String realm, String clientId, String username, String password)
            throws IOException, InterruptedException {
        String authUrl = KeycloakEnvironment.realmUrl(realm) + "/protocol/openid-connect/auth"
                + "?response_type=code&scope=openid&state=test"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(REDIRECT_URI);
        LoginResult opened = follow(HttpRequest.newBuilder(URI.create(authUrl)).GET().build());
        if (!opened.isLoginForm()) {
            return opened;
        }
        return follow(post(opened.formAction(), Map.of("username", username, "password", password)));
    }

    /** Exchanges the code at the token endpoint of the realm. The client must be a public one. */
    IdToken exchange(String realm, String clientId, String code) throws IOException, InterruptedException {
        HttpRequest request = post(URI.create(KeycloakEnvironment.realmUrl(realm) + "/protocol/openid-connect/token"),
                Map.of("grant_type", "authorization_code",
                        "code", code,
                        "client_id", clientId,
                        "redirect_uri", REDIRECT_URI));
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AssertionError("The token endpoint answered " + response.statusCode() + ": " + response.body());
        }
        String idToken = (String) JSON.readValue(response.body(), new TypeReference<Map<String, Object>>() { })
                .get("id_token");
        String payload = idToken.split("\\.", -1)[1];
        byte[] json = Base64.getUrlDecoder().decode(payload);
        return new IdToken(JSON.readValue(json, new TypeReference<Map<String, Object>>() { }));
    }

    // Follows the redirects of Keycloak by hand, because the redirect to the client has no server behind it.
    private LoginResult follow(HttpRequest first) throws IOException, InterruptedException {
        HttpRequest request = first;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 3) {
                return new LoginResult(null, Jsoup.parse(response.body(), request.uri().toString()));
            }
            URI next = request.uri().resolve(response.headers().firstValue("Location").orElseThrow());
            if (next.toString().startsWith(REDIRECT_URI)) {
                Map<String, String> parameters = queryParameters(next);
                if (!parameters.containsKey("code")) {
                    throw new AssertionError("The redirect to the client has no code: " + next);
                }
                return new LoginResult(parameters.get("code"), null);
            }
            request = HttpRequest.newBuilder(next).GET().build();
        }
        throw new AssertionError("More than " + MAX_REDIRECTS + " redirects");
    }

    private static HttpRequest post(URI uri, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static Map<String, String> queryParameters(URI uri) {
        Map<String, String> parameters = new LinkedHashMap<>();
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        for (String pair : query.split("&", -1)) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                parameters.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return parameters;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // Keycloak marks its cookies as Secure also on http, because a real browser treats localhost as secure.
    // The Java cookie store would drop them.
    private static final class CookiesOverHttp extends CookieManager {

        CookiesOverHttp() {
            super(null, CookiePolicy.ACCEPT_ALL);
        }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
            Map<String, List<String>> headers = new HashMap<>();
            responseHeaders.forEach((name, values) ->
                    headers.put(name, values.stream().map(value -> value.replaceAll("(?i);\\s*Secure", "")).toList()));
            super.put(uri, headers);
        }
    }
}
