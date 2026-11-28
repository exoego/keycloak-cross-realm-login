package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.Optional;

import org.keycloak.models.RealmModel;
import org.keycloak.provider.Provider;

public interface CandidateLookup extends Provider {

    List<Candidate> findByIdentifier(RealmModel entryRealm, String identifier);

    Optional<Candidate> find(RealmModel entryRealm, String realmId, String userId);

    Optional<Candidate> findByUserId(RealmModel entryRealm, String userId);
}
