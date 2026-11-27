package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.testcontainers.containers.Container.ExecResult;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.POSTGRES;

/** Reads the database of Keycloak. It runs SELECT only. */
final class Database {

    private Database() {
    }

    static int count(String table, String where) {
        return Integer.parseInt(query("select count(*) from " + table + " where " + where).get(0));
    }

    /** The names of the tables of Keycloak that start with the prefix. */
    static List<String> tables(String prefix) {
        return query("select table_name from information_schema.tables where table_name like '" + prefix + "%'");
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
