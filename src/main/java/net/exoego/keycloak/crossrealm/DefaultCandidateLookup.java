package net.exoego.keycloak.crossrealm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.keycloak.Config;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.validation.Validation;

final class DefaultCandidateLookup implements CandidateLookup {

    // The join lets the database probe the unique index on (REALM_ID, USERNAME) once for each realm.
    // USER_ENTITY has no index on USERNAME alone.
    private static final String BY_USERNAME = "select u.realmId, u.id, u.emailVerified from UserEntity u, RealmEntity r"
            + " where u.realmId = r.id and u.username = :identifier and u.serviceAccountClientLink is null";
    // IDX_USER_EMAIL serves this one. The verified flag is read here, not filtered,
    // because a direct login picks the user by email first, verified or not.
    private static final String BY_EMAIL = "select u.realmId, u.id, u.emailVerified from UserEntity u, RealmEntity r"
            + " where u.realmId = r.id and u.email = :identifier and u.serviceAccountClientLink is null"
            + " and r.loginWithEmailAllowed = true";
    private static final String SEARCHED_REALM = " and r.enabled = true and r.name <> :master";
    private static final String NOT_EXCLUDED = " and r.id not in :excludedIds";

    private final KeycloakSession session;
    private final Settings settings;

    DefaultCandidateLookup(KeycloakSession session, Settings settings) {
        this.session = session;
        this.settings = settings;
    }

    @Override
    public List<Candidate> findByIdentifier(RealmModel entryRealm, String identifier) {
        Objects.requireNonNull(entryRealm, "entryRealm");
        if (identifier == null) {
            return List.of();
        }
        String value = KeycloakModelUtils.toLowerCaseSafe(identifier.trim());
        if (value.isEmpty() || value.length() > Validation.MAX_USERNAME_LENGTH) {
            return List.of();
        }
        Set<String> excludedIds = excludedIds(entryRealm);

        // Keycloak looks for the email address first, and for the username only when no user has that address.
        Map<String, List<Object[]>> byEmail = value.indexOf('@') == -1
                ? Map.of() : byRealm(query(BY_EMAIL, value, excludedIds));
        Map<String, List<Object[]>> byUsername = byRealm(query(BY_USERNAME, value, excludedIds));

        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<Object[]>> entry : byUsername.entrySet()) {
            if (!byEmail.containsKey(entry.getKey())) {
                addCandidate(candidates, entry.getKey(), (String) entry.getValue().get(0)[1]);
            }
        }
        for (Map.Entry<String, List<Object[]>> entry : byEmail.entrySet()) {
            List<Object[]> rows = entry.getValue();
            // Two users with one address is a failure of a direct login too.
            if (rows.size() == 1 && Boolean.TRUE.equals(rows.get(0)[2])) {
                addCandidate(candidates, entry.getKey(), (String) rows.get(0)[1]);
            }
        }
        candidates.sort(Comparator.comparing(candidate -> candidate.realm().getName()));
        return candidates;
    }

    @Override
    public Optional<Candidate> find(RealmModel entryRealm, String realmId, String userId) {
        Objects.requireNonNull(entryRealm, "entryRealm");
        if (realmId == null || userId == null) {
            return Optional.empty();
        }
        RealmModel realm = session.realms().getRealm(realmId);
        if (realm == null || !isSearched(entryRealm, realm)) {
            return Optional.empty();
        }
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null || user.getServiceAccountClientLink() != null) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(realm, user));
    }

    @Override
    public void close() {
    }

    private boolean isSearched(RealmModel entryRealm, RealmModel realm) {
        return realm.isEnabled()
                && !realm.getName().equals(Config.getAdminRealm())
                && !excludedIds(entryRealm).contains(realm.getId());
    }

    private Set<String> excludedIds(RealmModel entryRealm) {
        Set<String> ids = new HashSet<>(settings.excludedRealmIds());
        ids.add(entryRealm.getId());
        return ids;
    }

    private List<Object[]> query(String select, String identifier, Set<String> excludedIds) {
        EntityManager entityManager = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        var query = entityManager
                .createQuery(select + SEARCHED_REALM + (excludedIds.isEmpty() ? "" : NOT_EXCLUDED), Object[].class)
                .setParameter("identifier", identifier)
                .setParameter("master", Config.getAdminRealm());
        if (!excludedIds.isEmpty()) {
            query.setParameter("excludedIds", excludedIds);
        }
        return query.getResultList();
    }

    private static Map<String, List<Object[]>> byRealm(List<Object[]> rows) {
        Map<String, List<Object[]>> byRealm = new HashMap<>();
        for (Object[] row : rows) {
            byRealm.computeIfAbsent((String) row[0], id -> new ArrayList<>()).add(row);
        }
        return byRealm;
    }

    private void addCandidate(List<Candidate> candidates, String realmId, String userId) {
        RealmModel realm = session.realms().getRealm(realmId);
        if (realm == null) {
            return;
        }
        UserModel user = session.users().getUserById(realm, userId);
        if (user != null) {
            candidates.add(new Candidate(realm, user));
        }
    }
}
