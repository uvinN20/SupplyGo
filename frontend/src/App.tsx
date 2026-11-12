import { useEffect, useState, type FormEvent } from "react";
import {
    currentSession,
    createAccount,
    signIn,
    signOut,
    listInventory,
    createInventoryProduct,
    changeInventoryStock,
    listOrders,
    submitOrder,
    updateOrderStatus,
    type AuthUser,
    type LiveOrder,
    type OrderStatus,
} from "./lib/api";
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
import BusinessProfiles from "./features/BusinessProfiles";
import Routes from "./features/Routes";
import {
    type Product,
} from "./lib/demo";
const navigation: { name: string; icon: LucideIcon; phase?: string }[] = [
    { name: "Dashboard", icon: LayoutDashboard },
    { name: "Inventory", icon: Package },
    { name: "Orders", icon: ShoppingBag },
    { name: "Business profiles", icon: Users },
    { name: "Routes", icon: Route },
    { name: "Chat", icon: MessageCircle, phase: "Phase 8" },
];
export default function App() {
    const [session, setSession] = useState<AuthUser | null>(null);
    const [checkingSession, setCheckingSession] = useState(true);
    const [logoutError, setLogoutError] = useState("");
    useEffect(() => {
        let active = true;
        currentSession()
            .then((user) => {
                if (active) setSession(user);
            })
            .catch(() => {
                if (active) setSession(null);
            })
            .finally(() => {
                if (active) setCheckingSession(false);
            });
        return () => {
            active = false;
        };
    }, []);
    const [page, setPage] = useState("Dashboard");
    const [menu, setMenu] = useState(false);
    const [inventory, setInventory] = useState<Product[]>([]);
    const [dashboardOrders, setDashboardOrders] = useState<LiveOrder[]>([]);
    const [dashboardOrdersLoading, setDashboardOrdersLoading] = useState(false);
    const [dashboardOrdersError, setDashboardOrdersError] = useState("");
    const [inventoryLoading, setInventoryLoading] = useState(false);
    const [inventoryError, setInventoryError] = useState("");

    useEffect(() => {
        if (!session) return;
        let active = true;
        setInventoryLoading(true);
        setInventoryError("");
        listInventory()
            .then((items) => {
                if (active) setInventory(items);
            })
            .catch((error: unknown) => {
                if (active)
                    setInventoryError(
                        error instanceof Error
                            ? error.message
                            : "Could not load inventory.",
                    );
            })
            .finally(() => {
                if (active) setInventoryLoading(false);
            });
        return () => {
            active = false;
        };
    }, [session]);
    const dashboardOrdersAvailable = session
        ? ["Admin", "Seller", "Seller Manager", "Production Manager"].includes(session.role)
        : false;

    useEffect(() => {
        if (!session || !dashboardOrdersAvailable) {
            setDashboardOrders([]);
            setDashboardOrdersError("");
            setDashboardOrdersLoading(false);
            return;
        }
        let active = true;
        setDashboardOrdersLoading(true);
        setDashboardOrdersError("");
        listOrders()
            .then((items) => {
                if (active) setDashboardOrders(Array.isArray(items) ? items : []);
            })
            .catch((cause: unknown) => {
                if (active) {
                    setDashboardOrders([]);
                    setDashboardOrdersError(
                        cause instanceof Error ? cause.message : "Could not load dashboard orders.",
                    );
                }
            })
            .finally(() => {
                if (active) setDashboardOrdersLoading(false);
            });
        return () => {
            active = false;
        };
    }, [session, dashboardOrdersAvailable, page]);

    function navigate(next: string) {
        setPage(next);
        setMenu(false);
        window.scrollTo({ top: 0, behavior: "instant" });
    }
    if (checkingSession)
        return (
            <main className="session-loading" aria-live="polite">
                Connecting securely to SupplyGo…
            </main>
        );
    if (!session)
        return (
            <Login
                onLogin={async (username, password) => {
                    setSession(await signIn(username, password));
                    setPage("Dashboard");
                }}
                onRegister={async (username, password, role) => {
                    await createAccount(username, password, role);
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
                            You’re exploring the web preview.
                            <br />
                            Dashboard, Inventory, Orders, Business profiles and Routes are connected to your SupplyGo server. Chat is planned.
                        </p>
                        <button onClick={() => navigate("About preview")}>
                            About this preview <ArrowUpRight size={15} />
                        </button>
                    </div>
                    <button
                        className="logout"
                        onClick={async () => {
                            setLogoutError("");
                            try {
                                await signOut();
                                setSession(null);
                                setInventory([]);
                                setMenu(false);
                            } catch {
                                setLogoutError(
                                    "Could not reach the server to end your session. Try again.",
                                );
                            }
                        }}
                    >
                        <LogOut size={18} /> Sign out
                    </button>
                    {logoutError && (
                        <p className="auth-error sidebar-error" role="alert">
                            {logoutError}
                        </p>
                    )}
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
                            <span className="tiny-dot" /> {["Inventory", "Orders", "Dashboard", "Business profiles", "Routes"].includes(page) ? "Live server data" : "Demo data"}
                        </span>
                        <div className="user-summary">
                            <span className="avatar">
                                {session.username.slice(0, 2).toUpperCase()}
                            </span>
                            <span>
                                <strong>{session.username}</strong>
                                <small>{session.role}</small>
                            </span>
                        </div>
                    </div>
                </header>
                <div className="demo-strip">
                    {page === "Inventory" ? <Package size={14} /> : <FlaskConical size={14} />}
                    <span>
                        {page === "Inventory"
                            ? inventoryError
                                ? "Could not load inventory from the SupplyGo server."
                                : "Inventory is loaded from your SupplyGo server and saved in its local data store."
                            : page === "Orders"
                                ? "Orders are loaded from your SupplyGo server. Actions update the server and stock."
                                : page === "Business profiles"
                                    ? "Profile details and role-based directories are loaded from your SupplyGo server."
                                    : page === "Routes"
                                        ? "Locations and roads are loaded from your SupplyGo server."
                                        : dashboardOrdersError
                                        ? dashboardOrdersError
                                        : "Dashboard totals and recent orders are loaded from your SupplyGo server."}
                    </span>
                </div>
                <main id="main-content" className="main-content" tabIndex={-1}>
                    {page === "Dashboard" ? (
                        <Dashboard
                            name={session.username}
                            role={session.role}
                            products={inventory}
                            orders={dashboardOrders}
                            loading={inventoryLoading || dashboardOrdersLoading}
                            error={inventoryError || dashboardOrdersError}
                            ordersAvailable={dashboardOrdersAvailable}
                            navigate={navigate}
                        />
                    ) : page === "Inventory" ? (
                        <Inventory
                            products={inventory}
                            role={session.role}
                            loading={inventoryLoading}
                            error={inventoryError}
                            onAdd={async (product) => {
                                await createInventoryProduct(product);
                                setInventory((current) => [...current, product]);
                            }}
                            onStockChange={async (productId, quantity, action) => {
                                await changeInventoryStock(productId, quantity, action);
                                try {
                                    setInventory(await listInventory());
                                    setInventoryError("");
                                } catch {
                                    setInventoryError("Stock was saved, but inventory could not refresh. Reopen this page to reload it.");
                                }
                            }}
                        />
                    ) : page === "Routes" ? (
                        <Routes role={session.role} />
                    ) : page === "Business profiles" ? (
                        <BusinessProfiles role={session.role} />
                    ) : page === "Orders" ? (
                        <Orders
                            session={session}
                            products={inventory}
                            onInventoryRefresh={async () => {
                                try { setInventory(await listInventory()); } catch { /* Keep the last loaded inventory view. */ }
                            }}
                        />
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
function Orders({
    session,
    products,
    onInventoryRefresh,
}: {
    session: AuthUser;
    products: Product[];
    onInventoryRefresh: () => Promise<void>;
}) {
    const [allOrders, setAllOrders] = useState<LiveOrder[]>([]);
    const [filter, setFilter] = useState("All orders");
    const [query, setQuery] = useState("");
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [productId, setProductId] = useState(products[0]?.id ?? "");
    const [quantity, setQuantity] = useState("1");
    const seller = session.role === "Seller";
    const manager = ["Admin", "Seller Manager", "Production Manager"].includes(session.role);

    async function refresh() {
        setLoading(true);
        setError("");
        try {
            setAllOrders(await listOrders());
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not load orders.");
        } finally {
            setLoading(false);
        }
    }
    useEffect(() => { void refresh(); }, []);
    useEffect(() => {
        if (!productId && products.length > 0) setProductId(products[0].id);
    }, [products, productId]);

    const visibleOrders = allOrders.filter((order) => {
        const statusLabel = order.status[0] + order.status.slice(1).toLowerCase();
        const product = products.find((item) => item.id === order.productId);
        const text = `${order.sellerUsername} ${order.id} ${order.productId} ${product?.name ?? ""}`.toLowerCase();
        return (filter === "All orders" || statusLabel === filter) && text.includes(query.trim().toLowerCase());
    });

    async function placeOrder(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const units = Number(quantity);
        if (!productId || !Number.isInteger(units) || units < 1 || units > 1_000_000) {
            setError("Choose a product and enter a whole quantity from 1 to 1,000,000.");
            return;
        }
        setBusy(true);
        setError("");
        try {
            await submitOrder(productId, units);
            await refresh();
            await onInventoryRefresh();
            setQuantity("1");
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not submit the order.");
        } finally {
            setBusy(false);
        }
    }

    async function setStatus(order: LiveOrder, status: OrderStatus) {
        setBusy(true);
        setError("");
        try {
            await updateOrderStatus(order.id, status);
            await refresh();
            if (status === "FULFILLED" || status === "CANCELLED") await onInventoryRefresh();
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not update the order.");
        } finally {
            setBusy(false);
        }
    }

    return (
        <div className="page-enter">
            <div className="page-heading">
                <div>
                    <span className="mini-label">FROM REQUEST TO FULFILMENT</span>
                    <h1>Orders</h1>
                    <p>Live orders from the SupplyGo server.</p>
                </div>
            </div>
            {seller && (
                <section className="surface" style={{ marginBottom: 20, padding: 24 }}>
                    <h2>Place an order</h2>
                    <form className="inventory-toolbar" onSubmit={placeOrder}>
                        <label>
                            Product
                            <select aria-label="Order product" value={productId} onChange={(event) => setProductId(event.target.value)} required>
                                <option value="" disabled>Select a product</option>
                                {products.map((product) => <option key={product.id} value={product.id}>{product.name} ({product.id}) · {product.available} available</option>)}
                            </select>
                        </label>
                        <label>
                            Units
                            <input aria-label="Order quantity" type="number" min="1" max="1000000" step="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} required />
                        </label>
                        <button className="btn primary" type="submit" disabled={busy || products.length === 0}>
                            {busy ? "Saving…" : "Submit order"}
                        </button>
                    </form>
                </section>
            )}
            {error && <p className="auth-error" role="alert">{error}</p>}
            <section className="surface">
                <div className="inventory-toolbar">
                    <div className="search-input">
                        <Search size={18} />
                        <input aria-label="Search orders" placeholder="Search seller, product or order…" value={query} onChange={(event) => setQuery(event.target.value)} />
                    </div>
                    <select aria-label="Order status" value={filter} onChange={(event) => setFilter(event.target.value)}>
                        {["All orders", "Pending", "Approved", "Fulfilled", "Cancelled"].map((status) => <option key={status}>{status}</option>)}
                    </select>
                </div>
                <div className="table-scroll">
                    <table>
                        <thead>
                            <tr><th>Order</th><th>Seller</th><th>Product</th><th>Units</th><th>Status</th><th>Actions</th></tr>
                        </thead>
                        <tbody>
                            {visibleOrders.map((order) => {
                                const product = products.find((item) => item.id === order.productId);
                                const canCancel = order.status === "PENDING" && (manager || (seller && order.sellerUsername === session.username));
                                return (
                                    <tr key={order.id}>
                                        <td><strong>#SG-{String(order.id).padStart(4, "0")}</strong></td>
                                        <td>{order.sellerUsername}</td>
                                        <td>{product?.name ?? order.productId}</td>
                                        <td>{order.quantity}</td>
                                        <td><span className={`badge ${order.status.toLowerCase()}`}>{order.status[0] + order.status.slice(1).toLowerCase()}</span></td>
                                        <td>
                                            <div className="order-actions">
                                                {manager && order.status === "PENDING" && <button className="btn subtle" disabled={busy} onClick={() => void setStatus(order, "APPROVED")}>Approve</button>}
                                                {manager && order.status === "APPROVED" && <button className="btn subtle" disabled={busy} onClick={() => void setStatus(order, "FULFILLED")}>Fulfil</button>}
                                                {canCancel && <button className="btn subtle" disabled={busy} onClick={() => void setStatus(order, "CANCELLED")}>Cancel</button>}
                                                {!manager && !canCancel && <span>—</span>}
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                    {loading && <p className="empty-state" role="status">Loading orders…</p>}
                    {!loading && visibleOrders.length === 0 && <p className="empty-state">{error ? "Orders could not be loaded." : "No orders match these filters."}</p>}
                </div>
            </section>
            <p className="footnote">Pending and approved orders reserve stock. Fulfilment consumes reserved stock; cancellation releases it.</p>
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
            "Your account, inventory, orders, and dashboard are connected to the Java server.",
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
                        "Web preview"}{" "}
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
