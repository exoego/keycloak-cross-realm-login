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
import org.keycloak.models.jpa.entities.UserEntity;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.validation.Validation;

final class DefaultCandidateLookup implements CandidateLookup {

    private static final String BY_USERNAME = "select u.realmId, u.id, u.username from UserEntity u, RealmEntity r"
            + " where u.realmId = r.id and u.username = :identifier and u.serviceAccountClientLink is null";
    private static final String BY_EMAIL = "select u.realmId, u.id, u.email, u.emailVerified, u.serviceAccountClientLink"
            + " from UserEntity u, RealmEntity r"
            + " where u.realmId = r.id and u.email = :identifier"
            + " and r.loginWithEmailAllowed = true";
    private static final String SEARCHED_REALM = " and r.enabled = true and r.name <> :master"
            + " and r.id not in :excludedIds";

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

        Map<String, List<Object[]>> byEmail = value.indexOf('@') == -1
                ? Map.of() : byRealm(query(BY_EMAIL, value, excludedIds));
        Map<String, List<Object[]>> byUsername = byRealm(query(BY_USERNAME, value, excludedIds));

        Set<String> realmIds = new HashSet<>(byUsername.keySet());
        realmIds.addAll(byEmail.keySet());
        List<Candidate> candidates = new ArrayList<>();
        for (String realmId : realmIds) {
            String userId = match(value, byEmail.get(realmId), byUsername.get(realmId));
            if (userId != null) {
                addCandidate(candidates, realmId, userId);
            }
        }
        candidates.sort(Comparator.comparing(candidate -> candidate.realm().getName()));
        return List.copyOf(candidates);
    }

    static String match(String value, List<Object[]> byEmail, List<Object[]> byUsername) {
        // Keycloak compares again in Java, because a collation can ignore accents and match more.
        String named = byUsername != null && value.equalsIgnoreCase((String) byUsername.get(0)[2])
                ? (String) byUsername.get(0)[1] : null;
        if (byEmail == null) {
            return named;
        }
        if (byEmail.size() != 1) {
            return null;
        }
        Object[] row = byEmail.get(0);
        if (!value.equalsIgnoreCase((String) row[2])) {
            return named;
        }
        String userId = (String) row[1];
        boolean verified = Boolean.TRUE.equals(row[3]);
        boolean serviceAccount = row[4] != null;
        return !serviceAccount && (verified || userId.equals(named)) ? userId : null;
    }

    @Override
    public Optional<Candidate> find(RealmModel entryRealm, String realmId, String userId) {
        Objects.requireNonNull(entryRealm, "entryRealm");
        if (realmId == null || userId == null) {
            return Optional.empty();
        }
        RealmModel realm = session.realms().getRealm(realmId);
        if (realm == null || !isSearched(entryRealm, realm) || !isStoredIn(realm, userId)) {
            return Optional.empty();
        }
        UserModel user = loadUser(realm, userId);
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

    private boolean isStoredIn(RealmModel realm, String userId) {
        UserEntity user = entityManager().find(UserEntity.class, userId);
        return user != null && realm.getId().equals(user.getRealmId());
    }

    private UserModel loadUser(RealmModel realm, String userId) {
        return HomeRealmContext.call(session, realm, () -> session.users().getUserById(realm, userId));
    }

    private EntityManager entityManager() {
        return session.getProvider(JpaConnectionProvider.class).getEntityManager();
    }

    private List<Object[]> query(String select, String identifier, Set<String> excludedIds) {
        return entityManager()
                .createQuery(select + SEARCHED_REALM, Object[].class)
                .setParameter("identifier", identifier)
                .setParameter("master", Config.getAdminRealm())
                .setParameter("excludedIds", excludedIds)
                .getResultList();
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
        UserModel user = loadUser(realm, userId);
        if (user != null) {
            candidates.add(new Candidate(realm, user));
        }
    }
}
