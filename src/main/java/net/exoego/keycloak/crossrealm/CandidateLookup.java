package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.Optional;

import org.keycloak.models.RealmModel;
import org.keycloak.provider.Provider;

/**
 * Finds the users of the home realms that an identifier matches.
 *
 * <p>The home realms are all enabled realms except the {@code master} realm, the entry realm,
 * and the realms of the setting {@code excluded-realm-ids}.
 * Only users in the Keycloak database are found. Service accounts are never found.
 * A disabled user is found too. The caller decides what to do with it.
 */
public interface CandidateLookup extends Provider {

    /**
     * Finds the candidates of an identifier, at most one for each realm, in the order of the realm names.
     * The identifier is trimmed and set to lower case, as a direct login does.
     * An email address matches only when the home realm allows login with email and the address is verified.
     */
    List<Candidate> findByIdentifier(RealmModel entryRealm, String identifier);

    /**
     * Returns the user when the realm may be searched and the user is not a service account.
     * The entry realm is never searched.
     */
    Optional<Candidate> find(RealmModel entryRealm, String realmId, String userId);
}
