import {
    ArrowUpRight,
    ArrowRight,
    Package,
    ShoppingBag,
    Layers3,
    AlertCircle,
    Route,
    MessageCircle,
    CheckCircle2,
} from "lucide-react";
import { metrics, stockState, type Product, type Role } from "../lib/demo";
import type { LiveOrder } from "../lib/api";

export default function Dashboard({
    name,
    role,
    products,
    orders,
    loading,
    error,
    ordersAvailable,
    navigate,
}: {
    name: string;
    role: Role;
    products: Product[];
    orders: LiveOrder[];
    loading: boolean;
    error: string;
    ordersAvailable: boolean;
    navigate: (page: string) => void;
}) {
    const m = metrics(products);
    const recentOrders = [...orders]
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
        .slice(0, 4);
    const today = new Date();
    const week = Array.from({ length: 7 }, (_, index) => {
        const date = new Date(today);
        date.setDate(today.getDate() - (6 - index));
        const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
        return {
            key,
            day: date.toLocaleDateString(undefined, { weekday: "short" }),
            total: orders
                .filter((order) => {
                    const created = new Date(order.createdAt);
                    return created.getFullYear() === date.getFullYear()
                        && created.getMonth() === date.getMonth()
                        && created.getDate() === date.getDate();
                })
                .reduce((sum, order) => sum + order.quantity, 0),
        };
    });
    const maxUnits = Math.max(1, ...week.map((day) => day.total));
    const latestOrderText = (order: LiveOrder) =>
        order.status[0] + order.status.slice(1).toLowerCase();

    return (
        <div className="page-enter">
            <div className="page-heading">
                <div>
                    <span className="mini-label">YOUR SUPPLYGO WORKSPACE</span>
                    <h1>Your day, at a glance <span className="wave">✦</span></h1>
                    <p>A fresh start, {name}. Here’s a live view of your workspace.</p>
                </div>
                <button className="btn white" onClick={() => navigate("Inventory")}>
                    View inventory <ArrowUpRight size={17} />
                </button>
            </div>

            {error && <p className="auth-error" role="alert">{error}</p>}

            <div className="welcome-banner">
                <div>
                    <span className="banner-tag">CONNECTED. ORGANISED. READY.</span>
                    <h2>Big picture.<br />Better possibilities.</h2>
                    <p>Your live inventory and orders, together in one place.</p>
                    <button className="btn banner-btn" onClick={() => navigate("Orders")}>
                        View live orders <ArrowRight size={16} />
                    </button>
                </div>
                <div className="banner-art" aria-hidden="true">
                    <div className="orbit orbit-one" />
                    <div className="orbit orbit-two" />
                    <div className="floating-box"><Package size={60} strokeWidth={1.2} /></div>
                    <span className="float-tag tag-top"><CheckCircle2 size={17} /> Stock organised</span>
                    <span className="float-tag tag-bottom"><Route size={17} /> Orders connected</span>
                    <span className="spark spark-one">✦</span>
                    <span className="spark spark-two">✧</span>
                </div>
            </div>

            <section className="metric-grid" aria-label="Live workspace totals">
                {[
                    {
                        title: "Product lines",
                        value: loading ? "…" : m.products,
                        note: "From your server inventory",
                        icon: Package,
                        color: "blue",
                    },
                    {
                        title: "Available units",
                        value: loading ? "…" : m.available.toLocaleString(),
                        note: "Whole fruit units",
                        icon: Layers3,
                        color: "teal",
                    },
                    {
                        title: role === "Seller" ? "My orders" : "Orders",
                        value: loading ? "…" : ordersAvailable ? orders.length : "—",
                        note: ordersAvailable ? "Visible to your account" : "Not available for this role",
                        icon: ShoppingBag,
                        color: "purple",
                    },
                    {
                        title: "Stock alerts",
                        value: loading ? "…" : m.low,
                        note: "Below 50 available units",
                        icon: AlertCircle,
                        color: "orange",
                    },
                ].map(({ title, value, note, icon: Icon, color }) => (
                    <article className={`metric ${color}`} key={title}>
                        <div className="metric-top">
                            <span>{title}</span>
                            <span className="metric-icon"><Icon size={21} /></span>
                        </div>
                        <strong>{value}</strong>
                        <small>{note}</small>
                    </article>
                ))}
            </section>

            <div className="dashboard-grid">
                <section className="surface orders-card">
                    <div className="section-heading">
                        <div>
                            <h2>Recent orders</h2>
                            <p>Latest records saved by the SupplyGo server.</p>
                        </div>
                        <button className="text-button" onClick={() => navigate("Orders")}>
                            View all <ArrowRight size={15} />
                        </button>
                    </div>
                    <div className="table-scroll">
                        <table>
                            <thead><tr><th>Order / seller</th><th>Product</th><th>Units</th><th>Status</th></tr></thead>
                            <tbody>
                                {recentOrders.map((order) => {
                                    const product = products.find((item) => item.id === order.productId);
                                    return (
                                        <tr key={order.id}>
                                            <td>
                                                <div className="partner">
                                                    <span className="avatar small">{order.sellerUsername.slice(0, 2).toUpperCase()}</span>
                                                    <span><strong>{order.sellerUsername}</strong><small>#SG-{String(order.id).padStart(4, "0")}</small></span>
                                                </div>
                                            </td>
                                            <td>{product?.name ?? order.productId}</td>
                                            <td>{order.quantity}</td>
                                            <td><span className={`badge ${order.status.toLowerCase()}`}>{latestOrderText(order)}</span></td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                        {!loading && ordersAvailable && recentOrders.length === 0 && <p className="empty-state">No orders yet.</p>}
                        {!loading && !ordersAvailable && <p className="empty-state">Orders are not available for this account role.</p>}
                        {loading && <p className="empty-state" role="status">Loading live workspace data…</p>}
                    </div>
                </section>

                <section className="surface stock-card">
                    <div className="section-heading">
                        <div><h2>Stock alerts</h2><p>Live products needing a top-up.</p></div>
                        <span className="alert-count">{loading ? "…" : m.low}</span>
                    </div>
                    <div className="stock-alerts">
                        {products.filter((product) => product.available < 50).slice(0, 4).map((product) => (
                            <div className="stock-alert" key={product.id}>
                                <span className={`fruit ${product.tone}`} aria-hidden="true">{product.symbol}</span>
                                <div><strong>{product.name}</strong><small>{stockState(product)}</small></div>
                                <b>{product.available}<small>units</small></b>
                            </div>
                        ))}
                        {!loading && m.low === 0 && <p className="muted">No products are below the low-stock threshold.</p>}
                    </div>
                    <button className="btn subtle full" onClick={() => navigate("Inventory")}>
                        View inventory <ArrowRight size={15} />
                    </button>
                </section>
            </div>

            <div className="bottom-grid">
                <section className="surface activity-chart">
                    <div className="section-heading">
                        <div><h2>Order volume</h2><p>Units requested during the last seven days</p></div>
                        <span className="legend"><i /> Units</span>
                    </div>
                    <div className="bars" role="img" aria-label={`Live order units for the last seven days: ${week.map((day) => `${day.day} ${day.total}`).join(", ")}`}>
                        {week.map(({ key, day, total }) => (
                            <div className="bar-column" key={key}>
                                <small>{total}</small>
                                <div className="bar-track"><div style={{ height: `${(total / maxUnits) * 100}%` }} /></div>
                                <span>{day}</span>
                            </div>
                        ))}
                    </div>
                </section>
                <section className="next-card">
                    <span className="next-icon"><MessageCircle size={24} /></span>
                    <h2>Better, together.</h2>
                    <p>You’re exploring the {role} preview. Team chat, business profiles and routes are planned next.</p>
                    <button className="text-button" onClick={() => navigate("Chat")}>
                        See what’s next <ArrowRight size={17} />
                    </button>
                </section>
            </div>
        </div>
    );
}
