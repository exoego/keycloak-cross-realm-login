package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

final class OsKeys {

    private static final long CHOOSER_WAIT_MILLIS = 3000;
    private static final long KEY_GAP_MILLIS = 500;
    private static final String FIRST_ROW_X = "650";
    private static final String FIRST_ROW_Y = "345";
    private static final String SECOND_ROW_Y = "410";

    private OsKeys() {
    }

    static void waitForChooser() {
        pause(CHOOSER_WAIT_MILLIS);
        if (isLinux()) {
            pointAt(FIRST_ROW_Y);
        } else if (isMac()) {
            run(List.of("osascript", "-e", "tell application \"System Events\" to set frontmost of "
                    + "(first process whose name is \"Google Chrome for Testing\") to true"));
            pause(KEY_GAP_MILLIS);
        }
    }

    static void next() {
        if (isLinux()) {
            pointAt(SECOND_ROW_Y);
        } else if (isMac()) {
            run(List.of("osascript", "-e", "tell application \"System Events\" to key code 48"));
            pause(KEY_GAP_MILLIS);
        } else {
            throw unsupported();
        }
    }

    static void enter() {
        if (isLinux()) {
            run(List.of("xdotool", "key", "Return"));
        } else if (isMac()) {
            run(List.of("osascript", "-e", "tell application \"System Events\" to key code 36"));
        } else {
            throw unsupported();
        }
    }

    private static void pointAt(String y) {
        run(List.of("xdotool", "mousemove", FIRST_ROW_X, y));
        pause(KEY_GAP_MILLIS);
    }

    private static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static boolean isMac() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static IllegalStateException unsupported() {
        return new IllegalStateException("OS key presses are supported on macOS and Linux only, not on "
                + System.getProperty("os.name"));
    }

    private static void run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException(command + " exited with " + exit + ": " + output);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot run " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running " + command, e);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
