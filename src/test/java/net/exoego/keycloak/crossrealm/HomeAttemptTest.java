package net.exoego.keycloak.crossrealm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeAttemptTest {

    @Test
    void anEnabledUserWithTheRightPasswordAndNoLockoutMatches() {
        assertTrue(attempt(true, true, null).matches());
    }

    @Test
    void aWrongPasswordNeverMatches() {
        assertFalse(attempt(false, true, null).matches());
    }

    @Test
    void aDisabledUserNeverMatches() {
        assertFalse(attempt(true, false, null).matches());
    }

    @Test
    void aLockedOutUserNeverMatches() {
        assertFalse(attempt(true, true, "user_temporarily_disabled").matches());
    }

    @Test
    void aWrongPasswordWithoutLockoutCountsAsFailureAlsoForADisabledUser() {
        assertTrue(attempt(false, true, null).countsAsFailure());
        assertTrue(attempt(false, false, null).countsAsFailure());
    }

    @Test
    void aRightPasswordCountsAsNoFailure() {
        assertFalse(attempt(true, false, null).countsAsFailure());
    }

    @Test
    void aLockedOutUserCountsAsNoFailure() {
        assertFalse(attempt(false, true, "user_disabled").countsAsFailure());
    }

    private static HomeAttempt attempt(boolean passwordValid, boolean enabled, String lockoutError) {
        return new HomeAttempt(null, passwordValid, enabled, lockoutError);
    }
}
