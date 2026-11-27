package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.representations.idm.RealmRepresentation;

/** An entry realm with a random name that exists for one test. It has the settings of entry-realm.json. */
final class TempEntryRealm extends TempRealmBase {

    private TempEntryRealm(boolean withUserStorage, boolean withDefaultRequiredActions) {
        super("tmp-entry-", configured(withUserStorage, withDefaultRequiredActions));
    }

    /** The realm of entry-realm.json as it is. */
    static TempEntryRealm withUserStorage() {
        return new TempEntryRealm(true, false);
    }

    /** The realm has no cross-realm user storage. */
    static TempEntryRealm withoutUserStorage() {
        return new TempEntryRealm(false, false);
    }

    /** The realm has the default required actions, like one made in the admin console. */
    static TempEntryRealm withDefaultRequiredActions() {
        return new TempEntryRealm(true, true);
    }

    /** Turns on the languages of the login pages. */
    void supportLocales(String... locales) {
        RealmRepresentation rep = resource().toRepresentation();
        rep.setInternationalizationEnabled(true);
        rep.setSupportedLocales(Set.of(locales));
        rep.setDefaultLocale("en");
        resource().update(rep);
    }

    private static RealmRepresentation configured(boolean withUserStorage, boolean withDefaultRequiredActions) {
        RealmRepresentation rep = read();
        if (withDefaultRequiredActions) {
            rep.setRequiredActions(null);
        }
        if (!withUserStorage) {
            rep.setComponents(null);
        }
        return rep;
    }

    private static RealmRepresentation read() {
        try (InputStream in = TempEntryRealm.class.getResourceAsStream("/entry-realm.json")) {
            return new ObjectMapper().readValue(in, RealmRepresentation.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
