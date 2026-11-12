# SupplyGo web application

A React + TypeScript + Vite browser app connected to the Java server through the local HTTP gateway. Authentication, inventory, orders, business profiles, routes and shared-room chat use live server data. The dashboard includes live inventory and order summaries. Some other workspace screens may still contain preview/sample content; check the in-app status banners.

The browser does not connect directly to the TCP/TLS or UDP ports. The gateway owns the authenticated server connection, and bridges browser chat requests to the UDP chat service.

## Start on Windows

Use Node.js 22.12+ (Node 24 is suitable). From the SupplyGo repository:

```powershell
git fetch origin
git switch main
cd frontend
npm ci
npm run dev
```

First start the TLS TCP server and HTTP gateway using [the gateway setup guide](../docs/WEB_GATEWAY.md). Then open the **Local** URL printed by Vite (normally http://localhost:5173), register or sign in, and keep all three terminals open. Stop each process with Ctrl+C. If PowerShell blocks npm.ps1, use `npm.cmd` or Command Prompt.

For a completed production build preview:

```powershell
npm run build
npm run preview
```

Open the URL printed by the preview command. The dev/preview scripts bind to all interfaces to support development environments; for your laptop only, use `npm run dev -- --host 127.0.0.1`.

## Try the design

- Welcome: editable display name and role; no real credentials collected.
- Dashboard: live inventory and order summaries; no revenue or live delivery statistics are implied.
- Inventory: live products and stock actions saved by the Java server.
- Orders: live order creation, approval, cancellation and fulfilment, with stock reservation/consumption handled by the server.
- Business profiles: live profile editing and role-scoped directories.
- Routes: saved server locations and roads, plus shortest-route lookup.
- Chat: a shared, temporary room bridged by the gateway to UDP. Messages are best-effort and not encrypted; do not send sensitive information.
- At narrow widths, use the navigation menu. Wide tables scroll horizontally. Reduced-motion preferences disable decorative motion.

The server enforces authentication, session isolation and role-based authorization. Some preview-only screens or sample content remain clearly labelled in the app.

The web app uses the Java server's supported inventory, order, profile and route fields. The low-stock indicator is a UI threshold and does not change backend stock rules.

## Checks

```powershell
npm test
npm run build
```

The frontend tests cover authentication preview flows, search, registration, order filters and totals. Run the Java verification from the repository root as well. Dependencies are pinned with package-lock.json; use npm ci for reproducible installs.

## Structure

- `src/features/`: Dashboard, Inventory, Orders, Business Profiles, Routes and Chat screens.
- `src/lib/api.ts`: typed browser API client for the gateway.
- `src/lib/demo.ts`: sample fixtures used only in preview areas.
- `src/styles/main.css`: shared theme and responsive layout.
- `src/App.tsx`: session restoration, navigation and live data integration.

## Current scope

The web app is still under development. Run the server, gateway and frontend locally, verify the screens and workflows on your Windows browser, and run the checks before merging feature changes. Chat uses a gateway REST polling bridge to the existing UDP service (not WebSocket).
