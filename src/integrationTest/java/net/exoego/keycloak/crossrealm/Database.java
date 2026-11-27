package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.testcontainers.containers.Container.ExecResult;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.POSTGRES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads the database of Keycloak. It runs SELECT only. */
final class Database {

    private Database() {
    }

    static int count(String table, String where) {
        return Integer.parseInt(query("select count(*) from " + table + " where " + where).get(0));
    }

    // Counts the rows of the user and credential tables that could hold a proxy user, and of every fed_user_ table.
    static final String STORED_FOR_ENTRY_REALM = """
            select
              (select count(*) from user_entity where realm_id = '%1$s' or id like 'f:%%')
              + (select count(*) from credential
                  where user_id like 'f:%%'
                     or user_id in (select id from user_entity where realm_id = '%1$s'))
              + coalesce(sum((xpath('/row/c/text()',
                  query_to_xml('select count(*) as c from ' || quote_ident(table_name), false, true, '')))[1]::text::bigint), 0),
              count(*)
            from information_schema.tables
            where table_type = 'BASE TABLE' and table_name like 'fed\\_user\\_%%'
            """;

    /** Fails when the entry realm stores a user or a credential of a proxy user, in one call to psql. */
    static void assertNothingStoredFor(String entryRealmId) {
        String row = query(STORED_FOR_ENTRY_REALM.formatted(entryRealmId)).get(0);
        int separator = row.indexOf('|');
        long rows = Long.parseLong(row.substring(0, separator));
        long federationTables = Long.parseLong(row.substring(separator + 1));
        assertTrue(federationTables > 0, "No fed_user_ table found, so the check looked at nothing");
        assertEquals(0, rows, "Rows stored for the entry realm in user_entity, credential and fed_user_ tables");
    }

    private static List<String> query(String sql) {
        try {
            ExecResult result = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(),
                    "-d", POSTGRES.getDatabaseName(), "-t", "-A", "-c", sql);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(result.getStderr());
            }
            return Arrays.stream(result.getStdout().split("\n")).filter(line -> !line.isBlank()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
