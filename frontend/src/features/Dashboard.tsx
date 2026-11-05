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
import {
    metrics,
    sampleOrders,
    stockState,
    type Product,
    type Role,
} from "../lib/demo";
export default function Dashboard({
    name,
    role,
    products,
    navigate,
}: {
    name: string;
    role: Role;
    products: Product[];
    navigate: (page: string) => void;
}) {
    const m = metrics(products);
    return (
        <div className="page-enter">
            <div className="page-heading">
                <div>
                    <span className="mini-label">LET’S KEEP THINGS MOVING</span>
                    <h1>
                        Your day, at a glance <span className="wave">✦</span>
                    </h1>
                    <p>
                        A fresh start, {name}. Here’s a look at your sample
                        workspace.
                    </p>
                </div>
                <button
                    className="btn white"
                    onClick={() => navigate("Inventory")}
                >
                    View inventory <ArrowUpRight size={17} />
                </button>
            </div>
            <div className="welcome-banner">
                <div>
                    <span className="banner-tag">
                        CONNECTED. ORGANISED. READY.
                    </span>
                    <h2>
                        Big picture.
                        <br />
                        Better possibilities.
                    </h2>
                    <p>Your people, products and plans — all in one place.</p>
                    <button
                        className="btn banner-btn"
                        onClick={() => navigate("Orders")}
                    >
                        Explore sample orders <ArrowRight size={16} />
                    </button>
                </div>
                <div className="banner-art" aria-hidden="true">
                    <div className="orbit orbit-one" />
                    <div className="orbit orbit-two" />
                    <div className="floating-box">
                        <Package size={60} strokeWidth={1.2} />
                    </div>
                    <span className="float-tag tag-top">
                        <CheckCircle2 size={17} /> Stock organised
                    </span>
                    <span className="float-tag tag-bottom">
                        <Route size={17} /> Plans connected
                    </span>
                    <span className="spark spark-one">✦</span>
                    <span className="spark spark-two">✧</span>
                </div>
            </div>
            <section
                className="metric-grid"
                aria-label="Sample workspace totals"
            >
                {[
                    {
                        title: "Product lines",
                        value: m.products,
                        note: "In this demo inventory",
                        icon: Package,
                        color: "blue",
                    },
                    {
                        title: "Available units",
                        value: m.available.toLocaleString(),
                        note: "Whole fruit units",
                        icon: Layers3,
                        color: "teal",
                    },
                    {
                        title: "Sample orders",
                        value: sampleOrders.length,
                        note: "Illustrative order records",
                        icon: ShoppingBag,
                        color: "purple",
                    },
                    {
                        title: "Stock alerts",
                        value: m.low,
                        note: "Below 50 available units",
                        icon: AlertCircle,
                        color: "orange",
                    },
                ].map(({ title, value, note, icon: Icon, color }) => (
                    <article className={`metric ${color}`} key={title}>
                        <div className="metric-top">
                            <span>{title}</span>
                            <span className="metric-icon">
                                <Icon size={21} />
                            </span>
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
                            <h2>Recent sample orders</h2>
                            <p>A small snapshot of the bigger picture.</p>
                        </div>
                        <button
                            className="text-button"
                            onClick={() => navigate("Orders")}
                        >
                            View all <ArrowRight size={15} />
                        </button>
                    </div>
                    <div className="table-scroll">
                        <table>
                            <thead>
                                <tr>
                                    <th>Order / partner</th>
                                    <th>Product</th>
                                    <th>Units</th>
                                    <th>Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                {sampleOrders.slice(0, 4).map((o) => (
                                    <tr key={o.id}>
                                        <td>
                                            <div className="partner">
                                                <span className="avatar small">
                                                    {o.initials}
                                                </span>
                                                <span>
                                                    <strong>
                                                        {o.customer}
                                                    </strong>
                                                    <small>#{o.id}</small>
                                                </span>
                                            </div>
                                        </td>
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
                    </div>
                </section>
                <section className="surface stock-card">
                    <div className="section-heading">
                        <div>
                            <h2>A little attention</h2>
                            <p>Sample stock that needs a top-up.</p>
                        </div>
                        <span className="alert-count">{m.low}</span>
                    </div>
                    <div className="stock-alerts">
                        {products
                            .filter((p) => p.available < 50)
                            .slice(0, 4)
                            .map((p) => (
                                <div className="stock-alert" key={p.id}>
                                    <span
                                        className={`fruit ${p.tone}`}
                                        aria-hidden="true"
                                    >
                                        {p.symbol}
                                    </span>
                                    <div>
                                        <strong>{p.name}</strong>
                                        <small>{stockState(p)}</small>
                                    </div>
                                    <b>
                                        {p.available}
                                        <small>units</small>
                                    </b>
                                </div>
                            ))}
                        {m.low === 0 && (
                            <p className="muted">
                                All sample products are above the alert
                                threshold.
                            </p>
                        )}
                    </div>
                    <button
                        className="btn subtle full"
                        onClick={() => navigate("Inventory")}
                    >
                        Take a look <ArrowRight size={15} />
                    </button>
                </section>
            </div>
            <div className="bottom-grid">
                <section className="surface activity-chart">
                    <div className="section-heading">
                        <div>
                            <h2>Sample order volume</h2>
                            <p>
                                Illustrative week · units requested, all
                                statuses
                            </p>
                        </div>
                        <span className="legend">
                            <i /> Units
                        </span>
                    </div>
                    <div
                        className="bars"
                        role="img"
                        aria-label="Sample orders: Monday 40, Tuesday 60, Wednesday 30, Thursday 25, Friday 15, Saturday 12, Sunday 0 units"
                    >
                        {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map(
                            (day) => {
                                const total = sampleOrders
                                    .filter((o) => o.day === day)
                                    .reduce((n, o) => n + o.units, 0);
                                return (
                                    <div className="bar-column" key={day}>
                                        <small>{total}</small>
                                        <div className="bar-track">
                                            <div
                                                style={{
                                                    height: `${(total / 60) * 100}%`,
                                                }}
                                            />
                                        </div>
                                        <span>{day}</span>
                                    </div>
                                );
                            },
                        )}
                    </div>
                </section>
                <section className="next-card">
                    <span className="next-icon">
                        <MessageCircle size={24} />
                    </span>
                    <h2>Better, together.</h2>
                    <p>
                        You’re exploring the {role} preview. Team chat and live
                        backend connections are coming in later phases.
                    </p>
                    <button
                        className="text-button"
                        onClick={() => navigate("Chat")}
                    >
                        See what’s next <ArrowRight size={17} />
                    </button>
                </section>
            </div>
        </div>
    );
}
