package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.representations.idm.RealmRepresentation;

final class TempEntryRealm extends TempRealmBase {

    private TempEntryRealm(boolean withUserStorage, boolean withDefaultRequiredActions) {
        super("tmp-entry-", configured(withUserStorage, withDefaultRequiredActions));
    }

    static TempEntryRealm withUserStorage() {
        return new TempEntryRealm(true, false);
    }

    static TempEntryRealm withoutUserStorage() {
        return new TempEntryRealm(false, false);
    }

    static TempEntryRealm withDefaultRequiredActions() {
        return new TempEntryRealm(true, true);
    }

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
