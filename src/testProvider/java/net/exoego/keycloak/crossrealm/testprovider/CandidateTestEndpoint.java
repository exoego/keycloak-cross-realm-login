package net.exoego.keycloak.crossrealm.testprovider;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.models.KeycloakSession;

import net.exoego.keycloak.crossrealm.Candidate;
import net.exoego.keycloak.crossrealm.CandidateLookup;

@Produces(MediaType.APPLICATION_JSON)
final class CandidateTestEndpoint {

    record Found(String realmName, String realmId, String userId, String username) {
        static Found of(Candidate candidate) {
            return new Found(candidate.realm().getName(), candidate.realm().getId(), candidate.user().getId(),
                    candidate.user().getUsername());
        }
    }

    private final KeycloakSession session;

    CandidateTestEndpoint(KeycloakSession session) {
        this.session = session;
    }

    @GET
    @Path("candidates")
    public List<Found> candidates(@QueryParam("identifier") String identifier) {
        return lookup().findByIdentifier(session.getContext().getRealm(), identifier).stream().map(Found::of).toList();
    }

    @GET
    @Path("candidate")
    public Response candidate(@QueryParam("realmId") String realmId, @QueryParam("userId") String userId) {
        return lookup().find(session.getContext().getRealm(), realmId, userId)
                .map(candidate -> Response.ok(Found.of(candidate)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    private CandidateLookup lookup() {
        return session.getProvider(CandidateLookup.class);
    }
}
