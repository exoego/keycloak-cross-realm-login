package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;

/**
 * Stands for the browser of one person. Each instance has its own cookies, so it has its own sessions.
 * All instances share one headless Chromium.
 */
final class Browser implements AutoCloseable {

    // The realm files register http://127.0.0.1/callback. Keycloak ignores the port of a loopback IP redirect URI.
    static final String REDIRECT_URI = "http://127.0.0.1:" + ClientApp.PORT + "/callback";

    /** Set by the Gradle property "headed", to watch the browser while debugging. */
    static final boolean HEADED = Boolean.getBoolean("browser.headed");

    private static final String LOGIN_FORM = "form#kc-form-login";
    private static final String ERROR_SELECTOR = "#input-error, #kc-error-message, .kc-feedback-text";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final BrowserContext context = Chromium.INSTANCE.newContext();
    private final Page page = context.newPage();

    /**
     * Opens the login of a client at a realm in the page of this browser.
     * With a valid SSO session of this browser, the page ends on the redirect to the client.
     */
    void open(String realm, String clientId) {
        open(realm, clientId, "");
    }

    /** Runs JavaScript in the page of this browser and returns the result. */
    Object evaluate(String expression) {
        return page.evaluate(expression);
    }

    /**
     * Opens the login of a client at a realm and types the username and the password.
     * A login that Keycloak does not accept ends on a page, so look at the result.
     * When this browser has an SSO session at the realm, Keycloak does not ask for the password.
     */
    LoginResult login(String realm, String clientId, String username, String password) {
        open(realm, clientId);
        waitForEnd(LOGIN_FORM + ", " + ERROR_SELECTOR);
        if (!page.url().startsWith(REDIRECT_URI) && page.locator(LOGIN_FORM).count() > 0) {
            page.locator("#username").fill(username);
            page.locator("#password").fill(password);
            page.locator("#kc-login").click();
            waitForEnd(ERROR_SELECTOR);
        }
        return result();
    }

    /**
     * Logs in with kc_action=UPDATE_PASSWORD, then types the new password twice on the page that Keycloak shows.
     * The result is the page where the login ends.
     */
    LoginResult loginAndUpdatePassword(String realm, String clientId, String username, String password,
            String newPassword) {
        open(realm, clientId, "&kc_action=UPDATE_PASSWORD");
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("#kc-login").click();
        page.locator("#password-new").fill(newPassword);
        page.locator("#password-confirm").fill(newPassword);
        page.locator("#kc-submit").click();
        waitForEnd(ERROR_SELECTOR);
        return result();
    }

    /** The URL of the page of this browser. */
    String url() {
        return page.url();
    }

    /** Exchanges the code at the token endpoint of the realm. The client must be a public one. */
    IdToken exchange(String realm, String clientId, String code) throws IOException, InterruptedException {
        String body = Map.of("grant_type", "authorization_code",
                        "code", code,
                        "client_id", clientId,
                        "redirect_uri", REDIRECT_URI)
                .entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(KeycloakEnvironment.realmUrl(realm) + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
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

    @Override
    public void close() {
        context.close();
    }

    private void open(String realm, String clientId, String extraQuery) {
        page.navigate(KeycloakEnvironment.realmUrl(realm) + "/protocol/openid-connect/auth"
                + "?response_type=code&scope=openid&state=test"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(REDIRECT_URI)
                + extraQuery);
    }

    /** Waits until the page is the redirect to the client, or shows the given element. */
    private void waitForEnd(String selector) {
        page.waitForFunction("([redirectUri, selector]) => location.href.startsWith(redirectUri)"
                + " || document.querySelector(selector) !== null",
                List.of(REDIRECT_URI, selector));
    }

    private LoginResult result() {
        if (page.url().startsWith(REDIRECT_URI)) {
            Object code = page.evaluate("new URL(location.href).searchParams.get('code')");
            if (code == null) {
                throw new AssertionError("The redirect to the client has no code: " + page.url());
            }
            return new LoginResult((String) code, false, "");
        }
        var error = page.locator(ERROR_SELECTOR);
        String text = error.count() == 0 ? "" : error.first().innerText().strip().replaceAll("\\s+", " ");
        boolean loginForm = page.locator(LOGIN_FORM).count() > 0;
        return new LoginResult(null, loginForm, text);
    }

    /** The markup of the login form without what changes for each request, such as the action URL and the values. */
    String loginFormShape() {
        return (String) page.locator(LOGIN_FORM).evaluate("form => {"
                + " const copy = form.cloneNode(true);"
                + " copy.removeAttribute('action');"
                + " copy.querySelectorAll('[value]').forEach(element => element.removeAttribute('value'));"
                + " return copy.outerHTML.replace(/\\s+/g, ' ');"
                + " }");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Stands for the client application. It answers every request with an empty page. */
    private static final class ClientApp {

        static final int PORT;

        static {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
                server.createContext("/", exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
                server.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
                PORT = server.getAddress().getPort();
            } catch (IOException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private ClientApp() {
        }
    }

    /** One Playwright and one Chromium for the JVM. They start with the first browser. */
    private static final class Chromium {

        static final com.microsoft.playwright.Browser INSTANCE;

        static {
            Playwright playwright = Playwright.create();
            Runtime.getRuntime().addShutdownHook(new Thread(playwright::close));
            INSTANCE = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(!HEADED));
        }

        private Chromium() {
        }
    }
}
