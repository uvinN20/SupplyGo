import { useState } from "react";
import {
    LayoutDashboard,
    Package,
    ShoppingBag,
    Users,
    Route,
    MessageCircle,
    LogOut,
    Menu,
    X,
    ArrowUpRight,
    FlaskConical,
    ArrowLeft,
    Search,
    Construction,
    type LucideIcon,
} from "lucide-react";
import Brand from "./components/Brand";
import Login from "./features/Login";
import Dashboard from "./features/Dashboard";
import Inventory from "./features/Inventory";
import {
    initialProducts,
    sampleOrders,
    type Product,
    type Role,
} from "./lib/demo";
const navigation: { name: string; icon: LucideIcon; phase?: string }[] = [
    { name: "Dashboard", icon: LayoutDashboard },
    { name: "Inventory", icon: Package },
    { name: "Orders", icon: ShoppingBag },
    { name: "Business profiles", icon: Users, phase: "Phase 5" },
    { name: "Routes", icon: Route, phase: "Phase 7" },
    { name: "Chat", icon: MessageCircle, phase: "Phase 8" },
];
export default function App() {
    const [session, setSession] = useState<{ name: string; role: Role } | null>(
        null,
    );
    const [page, setPage] = useState("Dashboard");
    const [menu, setMenu] = useState(false);
    const [products, setProducts] = useState<Product[]>(() =>
        initialProducts.map((p) => ({ ...p })),
    );
    function navigate(next: string) {
        setPage(next);
        setMenu(false);
        window.scrollTo({ top: 0, behavior: "instant" });
    }
    if (!session)
        return (
            <Login
                onEnter={(name, role) => {
                    setSession({ name, role });
                    setPage("Dashboard");
                }}
            />
        );
    return (
        <div className="app-shell">
            <a className="skip-link" href="#main-content">
                Skip to content
            </a>
            {menu && (
                <button
                    className="sidebar-backdrop"
                    aria-label="Close navigation"
                    onClick={() => setMenu(false)}
                />
            )}
            <aside
                className={`sidebar ${menu ? "open" : ""}`}
                aria-label="Main navigation"
            >
                <div className="sidebar-brand">
                    <Brand light />
                    <button
                        className="icon-btn mobile-close"
                        aria-label="Close navigation"
                        onClick={() => setMenu(false)}
                    >
                        <X size={20} />
                    </button>
                </div>
                <div className="workspace-chip">
                    <span className="workspace-mark">S</span>
                    <span>
                        SupplyGo workspace<small>Sample organisation</small>
                    </span>
                    <span className="workspace-dot" />
                </div>
                <span className="nav-label">WORKSPACE</span>
                <nav>
                    {navigation.map(({ name, icon: Icon, phase }) => (
                        <button
                            key={name}
                            className={`nav-item ${page === name ? "active" : ""}`}
                            aria-current={page === name ? "page" : undefined}
                            onClick={() => navigate(name)}
                        >
                            <Icon size={19} />
                            <span>{name}</span>
                            {phase && (
                                <span
                                    className="coming-dot"
                                    title={`${phase}: planned`}
                                />
                            )}
                        </button>
                    ))}
                </nav>
                <div className="sidebar-bottom">
                    <div className="preview-card">
                        <span className="preview-card-icon">
                            <FlaskConical size={22} />
                        </span>
                        <strong>A fresh look is growing.</strong>
                        <p>
                            You’re exploring Phase 1.
                            <br />
                            Made to try. Ready for feedback.
                        </p>
                        <button onClick={() => navigate("About preview")}>
                            About this preview <ArrowUpRight size={15} />
                        </button>
                    </div>
                    <button
                        className="logout"
                        onClick={() => {
                            setSession(null);
                            setProducts(initialProducts.map((p) => ({ ...p })));
                            setMenu(false);
                        }}
                    >
                        <LogOut size={18} /> Leave preview
                    </button>
                    <div className="sidebar-footer">
                        SUPPLYGO <span>v0.1 · WEB PREVIEW</span>
                    </div>
                </div>
            </aside>
            <div className="app-main">
                <header className="topbar">
                    <div className="breadcrumb">
                        <button
                            className="icon-btn menu-toggle"
                            aria-label="Open navigation"
                            aria-expanded={menu}
                            onClick={() => setMenu(true)}
                        >
                            <Menu size={21} />
                        </button>
                        <span>Workspace</span>
                        <span className="breadcrumb-slash">/</span>
                        <strong>{page}</strong>
                    </div>
                    <div className="topbar-right">
                        <span className="demo-badge">
                            <span className="tiny-dot" /> Demo data
                        </span>
                        <div className="user-summary">
                            <span className="avatar">
                                {session.name.slice(0, 2).toUpperCase()}
                            </span>
                            <span>
                                <strong>{session.name}</strong>
                                <small>{session.role} preview</small>
                            </span>
                        </div>
                    </div>
                </header>
                <div className="demo-strip">
                    <FlaskConical size={14} />
                    <span>
                        Design preview · Sample data only. Not connected to the
                        Java backend.
                    </span>
                </div>
                <main id="main-content" className="main-content" tabIndex={-1}>
                    {page === "Dashboard" ? (
                        <Dashboard
                            name={session.name}
                            role={session.role}
                            products={products}
                            navigate={navigate}
                        />
                    ) : page === "Inventory" ? (
                        <Inventory
                            products={products}
                            role={session.role}
                            onAdd={(p) =>
                                setProducts((current) => [...current, p])
                            }
                            onReset={() =>
                                setProducts(
                                    initialProducts.map((p) => ({ ...p })),
                                )
                            }
                        />
                    ) : page === "Orders" ? (
                        <Orders />
                    ) : (
                        <Planned page={page} navigate={navigate} />
                    )}
                </main>
                <footer className="app-footer">
                    <span>SupplyGo — connect smarter, deliver better.</span>
                    <span>
                        Designed for your next move{" "}
                        <span className="footer-star">✦</span>
                    </span>
                </footer>
            </div>
        </div>
    );
}
function Orders() {
    const [filter, setFilter] = useState("All orders");
    const [query, setQuery] = useState("");
    const orders = sampleOrders.filter(
        (o) =>
            (filter === "All orders" || o.status === filter) &&
            `${o.customer} ${o.id} ${o.product}`
                .toLowerCase()
                .includes(query.toLowerCase()),
    );
    return (
        <div className="page-enter">
            <div className="page-heading">
                <div>
                    <span className="mini-label">
                        FROM REQUEST TO FULFILMENT
                    </span>
                    <h1>Orders</h1>
                    <p>
                        Sample records to explore the visual design. Live
                        actions arrive in Phase 4.
                    </p>
                </div>
            </div>
            <section className="surface">
                <div className="inventory-toolbar">
                    <div className="search-input">
                        <Search size={18} />
                        <input
                            aria-label="Search sample orders"
                            placeholder="Search partner, product or order…"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                        />
                    </div>
                    <select
                        aria-label="Order status"
                        value={filter}
                        onChange={(e) => setFilter(e.target.value)}
                    >
                        {[
                            "All orders",
                            "Pending",
                            "Approved",
                            "Fulfilled",
                            "Cancelled",
                        ].map((s) => (
                            <option key={s}>{s}</option>
                        ))}
                    </select>
                </div>
                <div className="table-scroll">
                    <table>
                        <thead>
                            <tr>
                                <th>Order</th>
                                <th>Partner</th>
                                <th>Product</th>
                                <th>Units</th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            {orders.map((o) => (
                                <tr key={o.id}>
                                    <td>
                                        <strong>#{o.id}</strong>
                                    </td>
                                    <td>{o.customer}</td>
                                    <td>{o.product}</td>
                                    <td>{o.units}</td>
                                    <td>
                                        <span
                                            className={`badge ${o.status.toLowerCase()}`}
                                        >
                                            {o.status}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    {orders.length === 0 && (
                        <p className="empty-state">
                            No sample orders match these filters.
                        </p>
                    )}
                </div>
            </section>
            <p className="footnote">
                These are fixed illustrative records, not orders submitted to
                SupplyGo.
            </p>
        </div>
    );
}
function Planned({
    page,
    navigate,
}: {
    page: string;
    navigate: (p: string) => void;
}) {
    const details: Record<string, string> = {
        "Business profiles":
            "Supplier and seller profiles, clear contact details and role-based directories.",
        Routes: "Location selectors, shortest-route results and a visual road graph powered by the Java backend.",
        Chat: "A colourful team conversation view connected through a WebSocket-to-UDP bridge.",
        "About preview":
            "Phase 1 lets you review the design before we connect real accounts and data. Try the dashboard, search inventory, add a sample product as Admin, and explore sample orders. All sample edits reset on reload or when leaving the preview.",
    };
    return (
        <div className="page-enter">
            <div className="page-heading">
                <div>
                    <span className="mini-label">ONE STEP AT A TIME</span>
                    <h1>{page}</h1>
                </div>
            </div>
            <section className="planned-card">
                <span className="planned-icon">
                    <Construction size={36} />
                </span>
                <span className="preview-pill">
                    {navigation.find((n) => n.name === page)?.phase ??
                        "Phase 1"}{" "}
                    · {page === "About preview" ? "Current preview" : "Planned"}
                </span>
                <h2>
                    {page === "About preview"
                        ? "A preview with a purpose."
                        : "Good things are on the way."}
                </h2>
                <p>{details[page]}</p>
                <button
                    className="btn primary"
                    onClick={() => navigate("Dashboard")}
                >
                    <ArrowLeft size={17} /> Back to dashboard
                </button>
            </section>
        </div>
    );
}
