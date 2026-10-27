# SupplyGo web preview — Phase 1

A React + TypeScript + Vite browser interface for reviewing SupplyGo's Apple-inspired liquid-glass visual direction, with frosted panels, rounded widgets, layered colour and gentle motion. This preview uses local sample data. It does **not** authenticate users or call the Java server, TCP service, UDP chat or production repositories.

## Start on Windows

Use Node.js 22.12+ (Node 24 is suitable). From the SupplyGo repository:

```powershell
git fetch origin
git switch feature/web-ui-preview
cd frontend
npm ci
npm run dev
```

Open the **Local** URL printed by Vite (normally http://localhost:5173). Enter a display name, choose a preview role and click **Explore your workspace**. No password, TLS certificate, Java server or Maven build is needed for this design preview. Stop with Ctrl+C. If PowerShell blocks npm.ps1, use `npm.cmd` in place of `npm` or run the commands in Command Prompt.

For a completed production build preview:

```powershell
npm run build
npm run preview
```

Open the URL printed by the preview command. The dev/preview scripts bind to all interfaces to support development environments; for your laptop only, use `npm run dev -- --host 127.0.0.1`.

## Try the design

- Welcome: editable display name and role; no real credentials collected.
- Dashboard: totals calculated from sample inventory, sample orders, stock alerts and an illustrative weekly order-volume chart. No revenue or live delivery statistics are implied.
- Inventory: search across all samples, category/stock filters, five-row pagination, empty results, and sample-product creation for Admin/Production Manager previews. Duplicate IDs and invalid values are rejected.
- Orders: read-only samples with status and text filtering.
- Business profiles, Routes and Chat: clearly labelled planned destinations, not implemented web features.
- Leave preview: returns to the welcome page and resets sample edits. Reload also resets everything; there is no local storage or server persistence.
- At narrow widths, use the navigation menu. Wide tables scroll horizontally. Reduced-motion preferences disable decorative motion.

Role selection is a **design demonstration**, not access control. All records are synthetic and all roles can view those samples. Real session isolation, role-scoped records and server-side authorization belong to Phase 2 and subsequent feature integrations.

Sample inventory uses categories and a low-stock threshold of 50 solely for the visual preview. These fields/rules are not part of the existing Java schema yet. API integration must map supported fields explicitly or add reviewed server changes.

## Checks

```powershell
npm test
npm run build
```

Tests cover preview entry/exit, cross-page search and combined filters, duplicate IDs, sample reset, role-dependent editing controls, order filtering and derived totals. Dependencies are pinned with package-lock.json; use npm ci for reproducible installs. The Java backend is unchanged in this branch.

## Structure

- `src/features/`: Welcome, Dashboard and Inventory screens.
- `src/components/`: brand and original SVG illustration.
- `src/lib/demo.ts`: typed sample fixtures and filter/summary functions.
- `src/styles/main.css`: shared theme, responsive layout and reduced-motion rules.
- `src/App.tsx`: preview navigation, sample Orders and planned-feature pages.

## Next milestone

Review the appearance on your Windows browser first. Phase 2 adds a Java HTTP gateway, real login and cookie-backed browser sessions, with per-user TCP/TLS connections to the existing backend. Chat later uses a WebSocket-to-UDP bridge. The current browser preview has no such connections.
