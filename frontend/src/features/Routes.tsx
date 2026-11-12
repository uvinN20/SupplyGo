import { useEffect, useMemo, useState, type FormEvent } from "react";
import {
    findShortestRoute,
    listRouteLocations,
    listRouteRoads,
    saveRouteLocation,
    saveRouteRoad,
    type AuthUser,
    type RouteLocation,
    type RouteRoad,
    type ShortestRoute,
} from "../lib/api";

export default function Routes({ role }: { role: AuthUser["role"] }) {
    const canManage = role === "Admin" || role === "Production Manager";
    const [locations, setLocations] = useState<RouteLocation[]>([]);
    const [roads, setRoads] = useState<RouteRoad[]>([]);
    const [view, setView] = useState<"locations" | "roads">("locations");
    const [search, setSearch] = useState("");
    const [locationId, setLocationId] = useState("");
    const [locationName, setLocationName] = useState("");
    const [from, setFrom] = useState("");
    const [to, setTo] = useState("");
    const [meters, setMeters] = useState("1000");
    const [route, setRoute] = useState<ShortestRoute | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");

    async function refresh(showLoading = true) {
        if (showLoading) setLoading(true);
        setError("");
        try {
            const [nextLocations, nextRoads] = await Promise.all([
                listRouteLocations(),
                listRouteRoads(),
            ]);
            setLocations(nextLocations);
            setRoads(nextRoads);
            setFrom((current) => current && nextLocations.some((item) => item.id === current) ? current : nextLocations[0]?.id ?? "");
            setTo((current) => current && nextLocations.some((item) => item.id === current) ? current : nextLocations[1]?.id ?? nextLocations[0]?.id ?? "");
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not load the road network.");
        } finally {
            if (showLoading) setLoading(false);
        }
    }

    useEffect(() => { void refresh(); }, []);

    const visibleLocations = useMemo(() => {
        const needle = search.trim().toLowerCase();
        return locations.filter((item) => `${item.id} ${item.name}`.toLowerCase().includes(needle));
    }, [locations, search]);
    const visibleRoads = useMemo(() => {
        const needle = search.trim().toLowerCase();
        return roads.filter((item) => {
            const fromName = locations.find((location) => location.id === item.from)?.name ?? "";
            const toName = locations.find((location) => location.id === item.to)?.name ?? "";
            return `${item.from} ${item.to} ${fromName} ${toName} ${item.meters}`.toLowerCase().includes(needle);
        });
    }, [roads, locations, search]);
    const names = useMemo(() => new Map(locations.map((item) => [item.id, item.name])), [locations]);

    async function addLocation(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true);
        setError("");
        setNotice("");
        setRoute(null);
        try {
            await saveRouteLocation(locationId.trim().toUpperCase(), locationName);
            setLocationId("");
            setLocationName("");
            await refresh(false);
            setNotice("Location saved to the SupplyGo server.");
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not save the location.");
        } finally {
            setBusy(false);
        }
    }

    async function addRoad(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const distance = Number(meters);
        if (!from || !to || from === to || !Number.isSafeInteger(distance) || distance < 1 || distance > 1_000_000_000) {
            setError("Choose two different locations and enter a whole distance from 1 to 1,000,000,000 meters.");
            return;
        }
        setBusy(true);
        setError("");
        setNotice("");
        setRoute(null);
        try {
            await saveRouteRoad(from, to, distance);
            await refresh(false);
            setNotice("Two-way road saved to the SupplyGo server.");
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not save the road.");
        } finally {
            setBusy(false);
        }
    }

    async function findRoute(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true);
        setError("");
        setNotice("");
        setRoute(null);
        try {
            setRoute(await findShortestRoute(from, to));
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not find a route.");
        } finally {
            setBusy(false);
        }
    }

    return (
        <div className="page-enter">
            <div className="page-heading">
                <div>
                    <span className="mini-label">PLAN A SMARTER JOURNEY</span>
                    <h1>Routes</h1>
                    <p>Explore the saved road network and find the shortest distance.</p>
                </div>
            </div>

            {error && <p className="auth-error" role="alert">{error}</p>}
            {notice && <p className="auth-notice" role="status">{notice}</p>}

            <section className="surface route-planner">
                <div className="profile-section-heading">
                    <div>
                        <h2>Find the shortest route</h2>
                        <p>Distances use the roads saved on your SupplyGo server.</p>
                    </div>
                </div>
                <form className="route-search-form" onSubmit={findRoute}>
                    <label>
                        From
                        <select aria-label="Route origin" value={from} onChange={(event) => { setFrom(event.target.value); setRoute(null); }} required disabled={loading || locations.length === 0}>
                            {locations.map((item) => <option key={item.id} value={item.id}>{item.name} ({item.id})</option>)}
                        </select>
                    </label>
                    <label>
                        To
                        <select aria-label="Route destination" value={to} onChange={(event) => { setTo(event.target.value); setRoute(null); }} required disabled={loading || locations.length === 0}>
                            {locations.map((item) => <option key={item.id} value={item.id}>{item.name} ({item.id})</option>)}
                        </select>
                    </label>
                    <button className="btn primary" type="submit" disabled={busy || loading || locations.length === 0}>
                        {busy ? "Working…" : "Find route"}
                    </button>
                </form>
                {route && (
                    <div className="route-result" role="status">
                        <strong>{route.path.map((id) => names.get(id) ?? id).join(" → ")}</strong>
                        <span>{route.meters.toLocaleString()} meters total</span>
                    </div>
                )}
                {!loading && locations.length === 0 && <p className="empty-state">No locations have been saved yet. An Admin or Production Manager can add the road network below.</p>}
            </section>

            {canManage && (
                <div className="route-admin-grid">
                    <section className="surface route-admin-card">
                        <h2>Add or update a location</h2>
                        <form className="route-admin-form" onSubmit={addLocation}>
                            <label>
                                Location ID
                                <input aria-label="Location ID" maxLength={24} pattern="[A-Z0-9_-]{1,24}" placeholder="e.g. MATARA" required value={locationId} onChange={(event) => setLocationId(event.target.value.toUpperCase().replace(/[^A-Z0-9_-]/g, "").slice(0, 24))} />
                            </label>
                            <label>
                                Location name
                                <input aria-label="Location name" maxLength={64} required value={locationName} onChange={(event) => setLocationName(event.target.value)} />
                            </label>
                            <button className="btn primary" type="submit" disabled={busy}>{busy ? "Saving…" : "Save location"}</button>
                        </form>
                    </section>
                    <section className="surface route-admin-card">
                        <h2>Add or update a two-way road</h2>
                        <form className="route-admin-form" onSubmit={addRoad}>
                            <label>
                                From
                                <select aria-label="Road start" value={from} onChange={(event) => setFrom(event.target.value)} required disabled={locations.length < 2}>
                                    {locations.map((item) => <option key={item.id} value={item.id}>{item.name} ({item.id})</option>)}
                                </select>
                            </label>
                            <label>
                                To
                                <select aria-label="Road end" value={to} onChange={(event) => setTo(event.target.value)} required disabled={locations.length < 2}>
                                    {locations.map((item) => <option key={item.id} value={item.id}>{item.name} ({item.id})</option>)}
                                </select>
                            </label>
                            <label>
                                Distance (meters)
                                <input aria-label="Road distance" type="number" min="1" max="1000000000" step="1" required value={meters} onChange={(event) => setMeters(event.target.value)} />
                            </label>
                            <button className="btn primary" type="submit" disabled={busy || locations.length < 2}>{busy ? "Saving…" : "Save road"}</button>
                        </form>
                    </section>
                </div>
            )}

            <section className="surface route-network">
                <div className="profile-section-heading">
                    <div>
                        <h2>Saved road network</h2>
                        <p>{locations.length} locations · {roads.length} two-way roads</p>
                    </div>
                    <button className="btn subtle" type="button" disabled={loading || busy} onClick={() => { setSearch(""); void refresh(); }}>Refresh</button>
                </div>
                <div className="inventory-toolbar route-network-toolbar">
                    <div className="search-input">
                        <input aria-label="Search routes" placeholder="Search locations or roads…" value={search} onChange={(event) => setSearch(event.target.value)} />
                    </div>
                    <select aria-label="Network view" value={view} onChange={(event) => setView(event.target.value as "locations" | "roads")}>
                        <option value="locations">Locations</option>
                        <option value="roads">Roads</option>
                    </select>
                </div>
                <div className="table-scroll">
                    <table>
                        <thead>
                            {view === "locations"
                                ? <tr><th>ID</th><th>Location</th></tr>
                                : <tr><th>From</th><th>To</th><th>Distance</th></tr>}
                        </thead>
                        <tbody>
                            {view === "locations"
                                ? visibleLocations.map((item) => <tr key={item.id}><td><strong>{item.id}</strong></td><td>{item.name}</td></tr>)
                                : visibleRoads.map((item) => <tr key={`${item.from}-${item.to}`}><td>{names.get(item.from) ?? item.from}</td><td>{names.get(item.to) ?? item.to}</td><td>{item.meters.toLocaleString()} m</td></tr>)}
                        </tbody>
                    </table>
                    {loading && <p className="empty-state" role="status">Loading road network…</p>}
                    {!loading && (view === "locations" ? visibleLocations.length : visibleRoads.length) === 0 && <p className="empty-state">{error ? "The road network could not be loaded." : "No entries match this search."}</p>}
                </div>
                <p className="footnote">Roads are two-way. Only Admin and Production Manager accounts can change the saved network.</p>
            </section>
        </div>
    );
}
