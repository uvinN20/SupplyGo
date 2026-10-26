# Delivery-route planning

SupplyGo stores a small, manually maintained road network and uses Dijkstra's algorithm to find the shortest total distance between two locations. The result is a sequence of location IDs and an integer total in meters. All roads are two-way. This is an academic graph planner, not a live navigation service: it has no GPS, traffic, map provider, travel-time estimates or automatic assignment to orders.

## Desktop walkthrough

Start the TLS server and client using AUTHENTICATION.md. Sign in as ADMIN or PRODUCTION_MANAGER, open **Routes**, and save these locations:

| ID | Name |
| --- | --- |
| MATARA | Matara |
| WELIGAMA | Weligama |
| GALLE | Galle |

Enter From ID and To ID, set the road distance and choose **Save road**. Saving the same unordered pair replaces its distance. The following distances are illustrative, not verified geography:

| From | To | Meters |
| --- | --- | --- |
| MATARA | GALLE | 50000 |
| MATARA | WELIGAMA | 20000 |
| WELIGAMA | GALLE | 25000 |

Find MATARA to GALLE: the result is MATARA → WELIGAMA → GALLE, 45000 meters. Reverse travel has the same distance. Refresh the Locations or Roads view to inspect saved entries; each page contains up to ten rows. A new server has an empty graph. Existing IDs update their names and existing roads update their distances; deletion is not included in this increment.

All signed-in roles may view entries and find routes. Only ADMIN and PRODUCTION_MANAGER may save locations/roads. The server enforces permissions. Requests require TLS and a live login session. Swing uses background workers for network calls; logout or account changes clear displayed data and inputs.

## Validation and persistence

IDs use 1–24 uppercase letters, digits, underscores or hyphens. Names must be trimmed printable text of 1–64 UTF-16 code units. A road must connect two distinct saved locations, with a whole distance from 1 to 1000000000 meters. Negative/zero/fractional distances and self-loops are rejected. At most 100 locations and 1000 roads are allowed.

The server maintains routes.json (schema 1) and routes.lock inside supplygo.data.dir, default data. A missing file starts empty. Corrupt schemas, duplicate records and dangling road references prevent startup. A process lock prevents a second server from opening the store. Synchronized updates write and force a temporary file, atomically replace the destination, then publish memory. Failed replacement leaves prior memory intact. Back up the store with the server stopped. Do not commit runtime data.

Dijkstra runs on one immutable snapshot and therefore cannot mix partial graph changes. Distances use long, allowing paths above Integer.MAX_VALUE. The priority queue rejects stale entries; adjacency and queue ties are ordered by location ID for deterministic results. Equal-length alternatives need not produce the lexicographically smallest entire path. With lazy queue entries, complexity is O((V+E) log E) time and O(V+E) auxiliary space for this undirected graph.

Same start/end returns a single ID and zero meters. Unknown IDs produce INVALID_ROUTE; disconnected saved locations produce ROUTE_NOT_FOUND. Saved-road edits do not update an already displayed result on another client: find the route again for a new snapshot.

## TCP protocol

Use the existing version-1 request envelope and 8192-byte newline framing.

| Request | Exact payload | Success |
| --- | --- | --- |
| LIST_LOCATIONS | offset integer 0–1000 | ROUTE_LOCATIONS: locations [{id,name}], nextOffset |
| LIST_ROADS | offset integer 0–1000 | ROUTE_ROADS: roads [{from,to,meters}], nextOffset |
| SAVE_LOCATION | locationId, name strings | ROAD_NETWORK_UPDATED |
| SAVE_ROAD | from, to strings; meters integer | ROAD_NETWORK_UPDATED |
| FIND_ROUTE | from, to strings | ROUTE: path [IDs], meters integer |

Next offset is -1 on the last page. Roads are normalized with the smaller ID first. Unknown payload fields, including client-supplied roles, are rejected. Errors include TLS_REQUIRED, AUTH_REQUIRED, SESSION_EXPIRED, FORBIDDEN, INVALID_ROUTE, ROUTE_NOT_FOUND, ROUTES_UNAVAILABLE and STORAGE_ERROR. Normal application errors keep the connection usable. If a connection drops during Save, reconnect and refresh before retrying. Setting an ID or unordered pair is an upsert, so repeating the same successful save does not create a duplicate.

## Verification

Run mvn clean verify on Java 25. Tests compare Dijkstra against a Floyd–Warshall reference on random graphs, exercise direction/ties/disconnected locations and long totals, and check authorization, pagination, concurrency, restart persistence, corrupt stores, failed saves, protocol limits and real TLS routing requests. Swing control tests cover role access, busy states and logout clearing. Run the walkthrough above for a visual desktop check on your own system.
