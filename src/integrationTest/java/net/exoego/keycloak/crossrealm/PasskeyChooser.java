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
              set seen to ""
              tell application "System Events"
                set seen to "processes " & ((name of every process whose name contains "Chrome") as text) & linefeed
                set seen to seen & "front " & (name of first process whose frontmost is true) & linefeed
              end tell
              tell application "System Events" to tell process "Google Chrome for Testing"
                set seen to seen & "windows " & (count of windows) & linefeed
                repeat with n from 1 to count of windows
                  try
                    set seen to seen & "window " & (name of window n) & linefeed
                    set items_ to entire contents of window n
                    repeat with i from 1 to count of items_
                      try
                        if (role of (item i of items_)) is "AXButton" then set seen to seen & "  button " & (name of (item i of items_)) & linefeed
                      end try
                    end repeat
                  on error m
                    set seen to seen & "  error " & m & linefeed
                  end try
                end repeat
              end tell
              error "The passkey chooser did not show two passkeys of " & userName & linefeed & seen
            end run
            """;

    private static final String LINUX_SCRIPT = """
            import sys
            import time

            import gi

            gi.require_version("Atspi", "2.0")
            from gi.repository import Atspi

            user = sys.argv[1]
            wanted = int(sys.argv[2])

            def walk(node, found):
                try:
                    found.append((node.get_role_name(), node.get_name(), node))
                    for i in range(node.get_child_count()):
                        child = node.get_child_at_index(i)
                        if child is not None:
                            walk(child, found)
                except Exception:
                    pass

            seen = []
            deadline = time.time() + 15
            while time.time() < deadline:
                seen = []
                desktop = Atspi.get_desktop(0)
                for i in range(desktop.get_child_count()):
                    app = desktop.get_child_at_index(i)
                    if app is not None:
                        walk(app, seen)
                rows = [n for role, name, n in seen if role == "push button" and name and name.startswith(user)]
                if len(rows) >= 2:
                    if wanted >= len(rows):
                        sys.exit("The passkey chooser has " + str(len(rows)) + " rows, not " + str(wanted + 1))
                    Atspi.Action.do_action(rows[wanted], 0)
                    sys.exit(0)
                time.sleep(0.2)
            lines = [role + " " + repr(name) for role, name, n in seen if name]
            sys.exit("The passkey chooser did not show two passkeys of " + user + chr(10) + chr(10).join(lines))
            """;

    private PasskeyChooser() {
    }

    static void pick(String username, int index) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            run(List.of("osascript", "-", username, Integer.toString(index)), MAC_SCRIPT);
        } else if (os.contains("linux")) {
            run(List.of("python3", "-", username, Integer.toString(index)), LINUX_SCRIPT);
        } else {
            throw new IllegalStateException("The passkey chooser is supported on macOS and Linux only, not on " + os);
        }
    }

    private static String run(List<String> command, String input) {
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
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot run " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running " + command, e);
        }
    }
}
