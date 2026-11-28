package net.exoego.keycloak.crossrealm;

import java.time.Duration;
import java.util.function.BooleanSupplier;

final class Await {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Duration STAYS = Duration.ofSeconds(2);
    private static final long POLL_MILLIS = 50;

    private Await() {
    }

    static void until(String what, BooleanSupplier condition) {
        long end = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                throw new AssertionError("Timed out after " + TIMEOUT + " while waiting for " + what);
            }
            pause();
        }
    }

    static void staysTrue(String what, BooleanSupplier condition) {
        long end = System.nanoTime() + STAYS.toNanos();
        do {
            if (!condition.getAsBoolean()) {
                throw new AssertionError("This did not hold: " + what);
            }
            pause();
        } while (System.nanoTime() < end);
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", e);
        }
    }
}
