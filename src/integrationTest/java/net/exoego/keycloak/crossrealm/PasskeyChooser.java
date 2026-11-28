package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

final class PasskeyChooser {

    private static final String MAC_SCRIPT = """
            on run argv
              set userName to item 1 of argv
              set wanted to (item 2 of argv) as integer
              tell application "System Events" to tell process "Google Chrome for Testing"
                repeat 30 times
                  repeat with n from 1 to count of windows
                    try
                      set items_ to entire contents of window n
                      repeat with i from 1 to count of items_
                        try
                          if (role of (item i of items_)) is "AXGroup" then
                            set g to item i of items_
                            set found to {}
                            repeat with k from 1 to count of buttons of g
                              if (name of button k of g) starts with userName then set end of found to k
                            end repeat
                            if (count of found) >= 2 then
                              perform action "AXPress" of (button (item (wanted + 1) of found) of g)
                              return
                            end if
                          end if
                        end try
                      end repeat
                    end try
                  end repeat
                  delay 0.5
                end repeat
              end tell
              error "The passkey chooser did not show two passkeys of " & userName
            end run
            """;

    private static final String ROW_X = "650";
    private static final String[] ROW_Y = {"345", "410"};
    private static final long LINUX_WAIT_MILLIS = 3000;
    private static final long POINTER_WAIT_MILLIS = 500;

    private PasskeyChooser() {
    }

    static void pick(String username, int index) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            run(List.of("osascript", "-", username, Integer.toString(index)), MAC_SCRIPT);
        } else if (os.contains("linux")) {
            run(List.of("bash", "-c", "mkdir -p /tmp/dbg; for t in 0 1 2 3; do xwininfo -root -tree > /tmp/dbg/tree$t.txt 2>&1; sleep 1; done; true"), null);
            pause(LINUX_WAIT_MILLIS);
            run(List.of("xdotool", "mousemove", ROW_X, ROW_Y[index]), null);
            pause(POINTER_WAIT_MILLIS);
            run(List.of("xdotool", "key", "Return"), null);
        } else {
            throw new IllegalStateException("The passkey chooser is supported on macOS and Linux only, not on " + os);
        }
    }

    private static void run(List<String> command, String input) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (var stdin = process.getOutputStream()) {
                if (input != null) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                }
            }
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
