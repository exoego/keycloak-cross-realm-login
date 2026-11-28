package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

final class OsKeys {

    private static final long CHOOSER_WAIT_MILLIS = 3000;
    private static final long KEY_GAP_MILLIS = 500;

    private OsKeys() {
    }

    static void waitForChooser() {
        pause(CHOOSER_WAIT_MILLIS);
        if (isLinux()) {
            run(List.of("xdotool", "mousemove", "0", "0"));
            pause(KEY_GAP_MILLIS);
            debug("after-wait");
        }
        if (isMac()) {
            run(List.of("osascript", "-e", "tell application \"System Events\" to set frontmost of "
                    + "(first process whose name is \"Google Chrome for Testing\") to true"));
            pause(KEY_GAP_MILLIS);
        }
    }

    static void debug(String tag) {
        run(List.of("bash", "-c", "mkdir -p /tmp/dbg; (xprop -root _NET_SUPPORTING_WM_CHECK; xdotool getwindowfocus getwindowname;"
                + " pgrep -a openbox; xwd -root -silent | convert xwd:- /tmp/dbg/" + tag + ".png) > /tmp/dbg/" + tag + ".txt 2>&1; true"));
    }

    static void choose(int index) {
        int tabs = isLinux() ? index + 1 : index;
        for (int i = 0; i < tabs; i++) {
            press("Tab", 48);
            pause(KEY_GAP_MILLIS);
        }
        press("Return", 36);
        pause(1000);
        if (isLinux()) {
            debug("after-enter");
        }
    }

    private static void press(String xdotoolName, int macKeyCode) {
        if (isLinux()) {
            run(List.of("xdotool", "key", xdotoolName));
        } else if (isMac()) {
            run(List.of("osascript", "-e", "tell application \"System Events\" to key code " + macKeyCode));
        } else {
            throw unsupported();
        }
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
