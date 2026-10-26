# Colourful desktop workspace

The Java 25 Swing client uses FlatLaf 3.7.2 (Apache-2.0) with a blue/teal welcome panel, navy navigation, and blue, green, purple and orange workspace cards. Theme setup follows https://www.formdev.com/flatlaf/ .

Start the TLS server and client using AUTHENTICATION.md. Connect first, then sign in. Successful sign-in opens Dashboard. Inventory, Orders, Business profiles, Routes, Chat and Activity open from the sidebar. Sign Out returns to the welcome screen, where connection settings and Disconnect are available. Plain TCP ping remains available before login. New supplier/seller registration remains available on the welcome screen; registration does not sign in automatically.

Dashboard cards are shortcuts, not aggregate counts. Open each workspace and refresh to retrieve real server data. All existing permissions, TLS and networking operations remain enforced by the backend. Network calls use background workers; the footer displays progress and the latest result, including sign-in and connection errors. Activity contains the session log. No passwords are displayed in feedback.

This first redesign changes the login, theme, navigation and workspace framing. It does not add reporting endpoints, totals, decorative animation, or new business operations. Existing detailed forms retain their behavior. At smaller window sizes, workspace scrolling preserves access to wide tables and forms. Minimum window size is 980 by 700; the initial size is 1240 by 820.

## Desktop acceptance check

1. Launch with the TLS truststore. Confirm welcome fields and connection status are readable.
2. Connect; try a wrong password and confirm visible feedback, then sign in with a valid account.
3. Visit every sidebar destination and use the existing feature walkthroughs.
4. Check a seller cannot save roads or mutate inventory; check administrator controls remain available.
5. Sign out and sign in as another role: confirm Dashboard appears and previous account data is cleared by the existing feature panels.
6. Stop the server during a request: confirm return to the welcome screen and a connection error. Restart and reconnect.
7. Check resizing, keyboard Tab/Enter, and your Windows display scaling.

Automated coverage includes shell session transitions and existing panel permission/reset and real network integration tests. Offscreen renders help check layout; they do not replace the Windows acceptance check.
