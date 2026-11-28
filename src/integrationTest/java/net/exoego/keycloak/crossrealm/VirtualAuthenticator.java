package net.exoego.keycloak.crossrealm;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.playwright.CDPSession;

final class VirtualAuthenticator {

    private final CDPSession session;
    private final String id;

    VirtualAuthenticator(CDPSession session, String id) {
        this.session = session;
        this.id = id;
    }

    List<JsonObject> credentials() {
        List<JsonObject> credentials = new ArrayList<>();
        for (JsonElement credential : session.send("WebAuthn.getCredentials", params()).getAsJsonArray("credentials")) {
            credentials.add(credential.getAsJsonObject());
        }
        return credentials;
    }

    void remove(JsonObject credential) {
        JsonObject params = params();
        params.add("credentialId", credential.get("credentialId"));
        session.send("WebAuthn.removeCredential", params);
    }

    void add(JsonObject credential) {
        JsonObject params = params();
        params.add("credential", credential);
        session.send("WebAuthn.addCredential", params);
    }

    private JsonObject params() {
        JsonObject params = new JsonObject();
        params.addProperty("authenticatorId", id);
        return params;
    }
}
