# Browser UI migration: Phase 1

SupplyGo is moving toward a browser frontend while retaining its Java 25 TCP/TLS and UDP coursework services. The initial React preview lives in `frontend/`. See [frontend/README.md](../frontend/README.md) for Windows startup commands, scope and validation.

This branch starts from main and does not depend on the Swing redesign branch. Existing Java commands and persisted data are unchanged. The preview deliberately uses sample fixtures, an explicit preview role selector and no password field. It must not be presented as a working authentication system.

## Review checklist

1. Open the welcome screen; confirm colour, typography and illustration match the desired direction.
2. Enter a display name and explore the Admin preview. Check dashboard cards and navigation.
3. Search Inventory for `lime`, then clear search and filter by category/status. Check pagination and empty results.
4. Add a sample product, attempt a duplicate ID and confirm validation. Reset demo inventory.
5. Leave preview and enter as Seller: check the Add sample product action is absent. All data remains synthetic.
6. Search/filter sample Orders; open Routes/Chat to see their planned status.
7. Check browser widths around 1440, 1024 and 390 pixels, mobile menu, horizontal table scrolling, keyboard focus, modal Escape/Tab and reduced-motion settings.
8. Refresh: sample changes reset. No Java server files should be touched.

## Validation record

Production build/type checking and six component/data tests are run before the PR. Interactive visual QA in the hosted browser was blocked by the environment's browser access policy; desktop/mobile appearance and native dialog keyboard behavior still need a Windows browser walkthrough. No claim of live backend or automated browser end-to-end verification is made.

## Development sequence

1. Review this preview and agree on visual direction.
2. Add Java web gateway and real browser authentication.
3. Integrate inventory, orders and business profiles.
4. Add role-scoped live dashboard summaries.
5. Integrate route planning and the chat bridge.
6. Integrate reviewed ranking work and coursework simulations.
7. Complete browser tests, accessibility checks, setup automation and demonstration documentation.
