package net.exoego.keycloak.crossrealm;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

record PendingMatches(List<HomeUserId> matches) {

    static final String NOTE = "cross-realm-matches";

    String encode() {
        return String.join(",", matches.stream()
                .map(match -> base64(match.realmId()) + ":" + base64(match.userId()))
                .toList());
    }

    static PendingMatches decode(String note) {
        if (note == null || note.isEmpty()) {
            return new PendingMatches(List.of());
        }
        return new PendingMatches(Arrays.stream(note.split(",", -1))
                .map(part -> {
                    String[] ids = part.split(":", -1);
                    return new HomeUserId(unbase64(ids[0]), unbase64(ids[1]));
                })
                .toList());
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unbase64(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
