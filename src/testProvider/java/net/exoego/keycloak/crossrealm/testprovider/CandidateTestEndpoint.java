package net.exoego.keycloak.crossrealm.testprovider;

import java.util.List;
import java.util.Optional;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;

import net.exoego.keycloak.crossrealm.Candidate;
import net.exoego.keycloak.crossrealm.CandidateLookup;
import net.exoego.keycloak.crossrealm.HomeRealmCredentials;

@Produces(MediaType.APPLICATION_JSON)
final class CandidateTestEndpoint {

    record Found(String realmName, String realmId, String userId, String username, boolean enabled) {
        static Found of(Candidate candidate) {
            return new Found(candidate.realm().getName(), candidate.realm().getId(), candidate.user().getId(),
                    candidate.user().getUsername(), candidate.user().isEnabled());
        }
    }

    record Checked(String realmName, boolean valid) {
    }

    private final KeycloakSession session;

    CandidateTestEndpoint(KeycloakSession session) {
        this.session = session;
    }

    @GET
    @Path("candidates")
    public List<Found> candidates(@QueryParam("identifier") String identifier,
            @QueryParam("entryRealm") String entryRealm) {
        RealmModel contextRealm = session.getContext().getRealm();
        // Another realm than the context realm shows that the lookup uses the realm of the caller.
        RealmModel entry = entryRealm == null ? contextRealm : session.realms().getRealmByName(entryRealm);
        List<Candidate> candidates = lookup().findByIdentifier(entry, identifier);
        checkContextRealm(contextRealm);
        return candidates.stream().map(Found::of).toList();
    }

    @GET
    @Path("candidate")
    public Response candidate(@QueryParam("realmId") String realmId, @QueryParam("userId") String userId,
            @QueryParam("after") String after) {
        CandidateLookup lookup = lookup();
        RealmModel entryRealm = session.getContext().getRealm();
        if (after != null) {
            // The session then holds the users of this identifier.
            lookup.findByIdentifier(entryRealm, after);
        }
        Optional<Candidate> candidate = lookup.find(entryRealm, realmId, userId);
        checkContextRealm(entryRealm);
        // Not 404, which Keycloak also answers for a wrong realm or path.
        return candidate.map(found -> Response.ok(Found.of(found)).build())
                .orElseGet(() -> Response.noContent().build());
    }

    @POST
    @Path("check-password")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public List<Checked> checkPassword(@FormParam("identifier") String identifier,
            @FormParam("password") String password) {
        RealmModel entryRealm = session.getContext().getRealm();
        List<Checked> checked = lookup().findByIdentifier(entryRealm, identifier).stream()
                .map(candidate -> new Checked(candidate.realm().getName(), HomeRealmCredentials.isValid(session,
                        candidate, UserCredentialModel.password(password))))
                .toList();
        checkContextRealm(entryRealm);
        return checked;
    }

    private void checkContextRealm(RealmModel before) {
        if (!session.getContext().getRealm().getId().equals(before.getId())) {
            throw new IllegalStateException("The lookup left another context realm");
        }
    }

    private CandidateLookup lookup() {
        return session.getProvider(CandidateLookup.class);
    }
}
