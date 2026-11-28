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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;

/**
 * Stands for the browser of one person. Each instance has its own cookies, so it has its own sessions.
 * The instances of one thread share one headless Chromium.
 */
final class Browser implements AutoCloseable {

    // The realm files register http://127.0.0.1/callback. Keycloak ignores the port of a loopback IP redirect URI.
    static final String REDIRECT_URI = "http://127.0.0.1:" + ClientApp.PORT + "/callback";

    /** Set by the Gradle property "headed", to watch the browser while debugging. */
    static final boolean HEADED = Boolean.getBoolean("browser.headed");

    private static final String LOGIN_FORM = "form#kc-form-login";
    private static final String CHOOSER = "form#cross-realm-choices";
    private static final String MESSAGE_SELECTOR = "#input-error, #kc-error-message, .kc-feedback-text";
    private static final String STEP_END_SELECTOR = MESSAGE_SELECTOR + ", " + CHOOSER;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final BrowserContext context;
    private final Page page;

    Browser() {
        this(Chromium.get().newContext());
    }

    /** A browser that asks for pages in a language. */
    Browser(String locale) {
        this(Chromium.get().newContext(new com.microsoft.playwright.Browser.NewContextOptions().setLocale(locale)));
    }

    private Browser(BrowserContext context) {
        this.context = context;
        this.page = context.newPage();
    }

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
        waitForEnd(LOGIN_FORM + ", " + STEP_END_SELECTOR);
        if (!page.url().startsWith(REDIRECT_URI) && page.locator(LOGIN_FORM).count() > 0) {
            page.locator("#username").fill(username);
            page.locator("#password").fill(password);
            page.locator("#kc-login").click();
            waitForEnd(STEP_END_SELECTOR);
        }
        return result();
    }

    /** Submits the login form without the username field, as a client that is not a browser can. */
    LoginResult loginWithoutUsername(String realm, String clientId, String password) {
        open(realm, clientId);
        waitForEnd(LOGIN_FORM + ", " + STEP_END_SELECTOR);
        page.locator("#username").evaluate("field => field.remove()");
        page.locator("#password").fill(password);
        page.locator("#kc-login").click();
        waitForEnd(STEP_END_SELECTOR);
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
        waitForEnd(STEP_END_SELECTOR);
        return result();
    }

    /** The realms that the chooser page lists, in the order of the page. */
    List<Choice> choices() {
        return page.locator(CHOOSER + " button").all().stream()
                .map(button -> new Choice(text(button.locator(".choice-label")), text(button.locator(".choice-realm"))))
                .toList();
    }

    /** What one button of the chooser page shows. The realm name is empty when the page shows none. */
    record Choice(String label, String realmName) {
    }

    /** The title of the page. */
    String title() {
        return text(page.locator("#kc-page-title"));
    }

    /** The markup of the chooser page, to look for elements that a display name must not add. */
    String chooserHtml() {
        return (String) page.locator(CHOOSER).evaluate("form => form.outerHTML");
    }

    /** Counts the elements of the page that match a CSS selector. */
    int count(String selector) {
        return page.locator(selector).count();
    }

    /** The identifier that the page shows above the form, or null when it shows none. */
    String attemptedUsername() {
        var field = page.locator("#kc-attempted-username");
        return field.count() == 0 ? null : field.inputValue();
    }

    /** Clicks the link that starts the login again. The result is the page where the login ends. */
    LoginResult restartLogin() {
        markPage();
        page.locator("#reset-login").click();
        waitForEnd(LOGIN_FORM);
        return result();
    }

    /** Opens the action URL of the chooser page with GET, as a reload of the flow page does. */
    LoginResult reopenFlowPage() {
        String action = page.locator(CHOOSER).getAttribute("action");
        markPage();
        page.navigate(action);
        waitForEnd(LOGIN_FORM + ", " + STEP_END_SELECTOR);
        return result();
    }

    /** Clicks the button of a realm on the chooser page. */
    LoginResult choose(int index) {
        markPage();
        page.locator(CHOOSER + " button").nth(index).click();
        waitForEnd(STEP_END_SELECTOR);
        return result();
    }

    /** Changes the value that a button of the chooser page posts, and clicks it. */
    LoginResult chooseWithValue(int index, String value) {
        var button = page.locator(CHOOSER + " button").nth(index);
        button.evaluate("(element, value) => element.value = value", value);
        markPage();
        button.click();
        waitForEnd(STEP_END_SELECTOR);
        return result();
    }

    /** Posts a username and a password to the action of the chooser page, as the password form does. */
    LoginResult postPasswordFromChooser(String username, String password) {
        markPage();
        page.locator(CHOOSER).evaluate("(form, values) => {"
                + " const copy = document.createElement('form');"
                + " copy.method = 'post';"
                + " copy.action = form.action;"
                + " for (const [name, value] of Object.entries(values)) {"
                + "   const input = document.createElement('input');"
                + "   input.name = name; input.value = value; copy.appendChild(input);"
                + " }"
                + " document.body.appendChild(copy);"
                + " copy.submit();"
                + " }", Map.of("username", username, "password", password));
        waitForEnd(LOGIN_FORM + ", " + STEP_END_SELECTOR);
        return result();
    }

    /** Adds a choice to the login form and posts it. A real login form never does this. */
    LoginResult postChoiceFromLoginForm(String value) {
        markPage();
        page.locator(LOGIN_FORM).evaluate("(form, value) => {"
                + " const input = document.createElement('input');"
                + " input.name = 'cross-realm-choice'; input.value = value;"
                + " form.appendChild(input);"
                + " form.submit();"
                + " }", value);
        waitForEnd(LOGIN_FORM + ", " + STEP_END_SELECTOR);
        return result();
    }

    private static String text(Locator locator) {
        return locator.count() == 0 ? "" : locator.first().innerText().strip().replaceAll("\\s+", " ");
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
        page.waitForFunction("([redirectUri, selector]) => window.stale !== true"
                + " && (location.href.startsWith(redirectUri) || document.querySelector(selector) !== null)",
                List.of(REDIRECT_URI, selector));
    }

    /** Marks the page, so that a wait does not take the old page for the new one. */
    private void markPage() {
        page.evaluate("window.stale = true");
    }

    private LoginResult result() {
        if (page.url().startsWith(REDIRECT_URI)) {
            Object code = page.evaluate("new URL(location.href).searchParams.get('code')");
            if (code == null) {
                throw new AssertionError("The redirect to the client has no code: " + page.url());
            }
            return new LoginResult((String) code, false, false, "");
        }
        var error = page.locator(MESSAGE_SELECTOR);
        String text = error.count() == 0 ? "" : error.first().innerText().strip().replaceAll("\\s+", " ");
        boolean loginForm = page.locator(LOGIN_FORM).count() > 0;
        return new LoginResult(null, loginForm, page.locator(CHOOSER).count() > 0, text);
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

    /**
     * One Playwright and one Chromium for each thread, because Playwright must stay on the thread that made it.
     * They start with the first browser of a thread and close when the JVM ends.
     */
    private static final class Chromium {

        private static final List<Playwright> ALL = new CopyOnWriteArrayList<>();
        private static final ThreadLocal<com.microsoft.playwright.Browser> LOCAL = ThreadLocal.withInitial(() -> {
            Playwright playwright = Playwright.create();
            ALL.add(playwright);
            return playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(!HEADED));
        });

        static {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> ALL.forEach(Playwright::close)));
        }

        private Chromium() {
        }

        static com.microsoft.playwright.Browser get() {
            return LOCAL.get();
        }
    }
}
