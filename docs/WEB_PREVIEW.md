# Browser application review

The React frontend in `frontend/` uses the Java HTTP gateway for authenticated, server-backed workspace features. See [WEB_GATEWAY.md](WEB_GATEWAY.md) for Windows setup commands and limitations.

## Review checklist

1. Start the TLS TCP server, HTTP gateway and Vite frontend in separate PowerShell windows.
2. Register/sign in as a Seller. Check Inventory, create a product and confirm it remains after refresh.
3. Submit an order, then sign in as Admin/Manager to approve and fulfil it. Confirm stock updates.
4. Save a Business Profile, refresh, then check that it appears in the role-appropriate directory.
5. Add locations and roads under Routes; run a shortest-route lookup.
6. Join Chat from two signed-in accounts and exchange a short non-sensitive message. Chat is shared, temporary, best-effort and unencrypted.
7. Check role permissions, keyboard navigation, mobile widths and error states.

## Automated checks

From the repository root, run `mvn clean verify`. In `frontend/`, run `npm test` and `npm run build`. Manual browser checks are also required for the server-backed workflows.

Dashboard, Inventory, Orders, Business Profiles and Routes are connected to backend APIs. Some other screens still use sample content and are labelled in the interface.