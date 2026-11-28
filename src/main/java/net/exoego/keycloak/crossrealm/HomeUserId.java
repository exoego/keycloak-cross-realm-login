package net.exoego.keycloak.crossrealm;

import java.util.Optional;

record HomeUserId(String realmId, String userId) {

    static Optional<HomeUserId> parse(String externalId) {
        int colon = externalId.indexOf(':');
        if (colon < 1 || colon == externalId.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new HomeUserId(externalId.substring(0, colon), externalId.substring(colon + 1)));
    }

    String externalId() {
        return realmId + ":" + userId;
    }
}
