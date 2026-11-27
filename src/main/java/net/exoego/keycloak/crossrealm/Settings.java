package net.exoego.keycloak.crossrealm;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The settings of the provider.
 *
 * @param excludedRealmIds the ids of the realms that are never searched
 */
record Settings(Set<String> excludedRealmIds) {

    static Settings of(String excludedRealmIds) {
        Set<String> excluded = Arrays.stream((excludedRealmIds == null ? "" : excludedRealmIds).split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return new Settings(excluded);
    }
}
