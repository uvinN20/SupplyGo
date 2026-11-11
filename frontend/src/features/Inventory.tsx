import { useEffect, useRef, useState, type FormEvent } from "react";
import {
    Plus,
    Minus,
    Search,
    SlidersHorizontal,
    X,
    Package,
    ChevronLeft,
    ChevronRight,
} from "lucide-react";
import {
    canEdit,
    filterProducts,
    metrics,
    stockState,
    type Product,
    type Role,
} from "../lib/demo";
export default function Inventory({
    products,
    role,
    loading,
    error,
    onAdd,
    onStockChange,
}: {
    products: Product[];
    role: Role;
    loading: boolean;
    error: string;
    onAdd: (p: Product) => Promise<void>;
    onStockChange: (
        productId: string,
        quantity: number,
        action: "receive" | "withdraw",
    ) => Promise<void>;
}) {
    const [query, setQuery] = useState("");
    const [category, setCategory] = useState("All categories");
    const [status, setStatus] = useState("All stock");
    const [page, setPage] = useState(0);
    const [adding, setAdding] = useState(false);
    const [notice, setNotice] = useState("");
    const [stockEdit, setStockEdit] = useState<{
        id: string;
        name: string;
        action: "receive" | "withdraw";
    } | null>(null);
    const filtered = filterProducts(products, query, category, status);
    const totalPages = Math.max(1, Math.ceil(filtered.length / 5));
    const safePage = Math.min(page, totalPages - 1);
    const m = metrics(products);
    return (
        <div className="page-enter">
            <div className="page-heading">
                <div>
                    <span className="mini-label">
                        A FRESH VIEW OF YOUR STOCK
                    </span>
                    <h1>Inventory</h1>
                    <p>Everything in its place. Every product accounted for.</p>
                </div>
                {canEdit(role) && (
                    <button
                        className="btn primary"
                        onClick={() => setAdding(true)}
                    >
                        <Plus size={18} /> Add product
                    </button>
                )}
            </div>
            <div className="inventory-summary">
                <span>
                    <Package size={19} />
                    <strong>{m.products}</strong> product lines
                </span>
                <span>
                    <i className="tiny-dot" />
                    <strong>{m.available.toLocaleString()}</strong> available
                    units
                </span>
                <span>
                    <strong>{m.reserved}</strong> reserved units
                </span>
            </div>
            {loading && <p className="read-only-note" role="status">Loading inventory from the SupplyGo server…</p>}
            {error && <p className="auth-error" role="alert">{error}</p>}
            <section className="surface">
                <div className="inventory-toolbar">
                    <div className="search-input">
                        <Search size={18} />
                        <input
                            aria-label="Search inventory"
                            value={query}
                            onChange={(e) => {
                                setQuery(e.target.value);
                                setPage(0);
                            }}
                            placeholder="Search product name or ID…"
                        />
                    </div>
                    <div className="filters">
                        <SlidersHorizontal size={17} />
                        <select
                            aria-label="Category"
                            value={category}
                            onChange={(e) => {
                                setCategory(e.target.value);
                                setPage(0);
                            }}
                        >
                            {[
                                "All categories",
                                "Uncategorized",
                            ].map((c) => (
                                <option key={c}>{c}</option>
                            ))}
                        </select>
                        <select
                            aria-label="Stock status"
                            value={status}
                            onChange={(e) => {
                                setStatus(e.target.value);
                                setPage(0);
                            }}
                        >
                            {[
                                "All stock",
                                "In stock",
                                "Low stock",
                                "Out of stock",
                            ].map((c) => (
                                <option key={c}>{c}</option>
                            ))}
                        </select>
                    </div>
                </div>
                <div className="table-scroll">
                    <table className="inventory-table">
                        <thead>
                            <tr>
                                <th>Product</th>
                                <th>Category</th>
                                <th>On hand</th>
                                <th>Reserved</th>
                                <th>Available</th>
                                <th>Status</th>
                                {canEdit(role) && <th>Stock actions</th>}
                            </tr>
                        </thead>
                        <tbody>
                            {filtered
                                .slice(safePage * 5, safePage * 5 + 5)
                                .map((p) => (
                                    <tr key={p.id}>
                                        <td>
                                            <div className="partner">
                                                <span
                                                    className={`fruit ${p.tone}`}
                                                    aria-hidden="true"
                                                >
                                                    {p.symbol}
                                                </span>
                                                <span>
                                                    <strong>{p.name}</strong>
                                                    <small>{p.id}</small>
                                                </span>
                                            </div>
                                        </td>
                                        <td>{p.category}</td>
                                        <td>{p.available + p.reserved}</td>
                                        <td>{p.reserved}</td>
                                        <td>
                                            <strong>{p.available}</strong>
                                            <small className="inline-unit">
                                                {" "}
                                                units
                                            </small>
                                        </td>
                                        <td>
                                            <span
                                                className={`badge ${p.available === 0 ? "cancelled" : p.available < 50 ? "pending" : "fulfilled"}`}
                                            >
                                                {stockState(p)}
                                            </span>
                                        </td>
                                        {canEdit(role) && (
                                            <td>
                                                <div className="filters">
                                                    <button
                                                        className="icon-btn"
                                                        aria-label={`Receive stock for ${p.name}`}
                                                        title="Receive stock"
                                                        onClick={() =>
                                                            setStockEdit({
                                                                id: p.id,
                                                                name: p.name,
                                                                action: "receive",
                                                            })
                                                        }
                                                    >
                                                        <Plus size={16} />
                                                    </button>
                                                    <button
                                                        className="icon-btn"
                                                        aria-label={`Withdraw stock for ${p.name}`}
                                                        title="Withdraw stock"
                                                        onClick={() =>
                                                            setStockEdit({
                                                                id: p.id,
                                                                name: p.name,
                                                                action: "withdraw",
                                                            })
                                                        }
                                                    >
                                                        <Minus size={16} />
                                                    </button>
                                                </div>
                                            </td>
                                        )}
                                    </tr>
                                ))}
                        </tbody>
                    </table>
                    {filtered.length === 0 && (
                        <div className="empty-state">
                            <Search size={30} />
                            <h2>No matching products</h2>
                            <p>
                                Try a different name, category or stock status.
                            </p>
                            <button
                                className="btn white"
                                onClick={() => {
                                    setQuery("");
                                    setCategory("All categories");
                                    setStatus("All stock");
                                    setPage(0);
                                }}
                            >
                                Clear filters
                            </button>
                        </div>
                    )}
                </div>
                <div className="pagination">
                    <span>
                        {filtered.length === 0
                            ? "0"
                            : `${safePage * 5 + 1}–${Math.min(safePage * 5 + 5, filtered.length)}`}{" "}
                        of {filtered.length} products
                    </span>
                    <div>
                        <button
                            className="icon-btn"
                            aria-label="Previous inventory page"
                            disabled={safePage === 0}
                            onClick={() => setPage(safePage - 1)}
                        >
                            <ChevronLeft size={18} />
                        </button>
                        <span>
                            Page {safePage + 1} of {totalPages}
                        </span>
                        <button
                            className="icon-btn"
                            aria-label="Next inventory page"
                            disabled={safePage + 1 >= totalPages}
                            onClick={() => setPage(safePage + 1)}
                        >
                            <ChevronRight size={18} />
                        </button>
                    </div>
                </div>
            </section>
            <p className="footnote">
                Quantities are whole fruit units. Low stock means fewer than
                50 available units. Inventory changes are saved by the SupplyGo server.
            </p>
            {!canEdit(role) && (
                <p className="read-only-note">
                    {role}: inventory browsing only. Editing is available
                    to Admin and Production Manager roles.
                </p>
            )}
            <p role="status" className="notice">
                {notice}
            </p>
            {stockEdit && (
                <StockChange
                    product={stockEdit}
                    onClose={() => setStockEdit(null)}
                    onSave={async (quantity) => {
                        await onStockChange(
                            stockEdit.id,
                            quantity,
                            stockEdit.action,
                        );
                        setStockEdit(null);
                        setNotice(
                            `Stock was ${stockEdit.action === "receive" ? "received" : "withdrawn"} for ${stockEdit.name}.`,
                        );
                    }}
                />
            )}
            {adding && (
                <AddProduct
                    products={products}
                    onClose={() => setAdding(false)}
                    onSave={async (p) => {
                        await onAdd(p);
                        setAdding(false);
                        setQuery("");
                        setCategory("All categories");
                        setStatus("All stock");
                        setPage(Math.floor(products.length / 5));
                        setNotice(`${p.name} was saved to the SupplyGo server.`);
                    }}
                />
            )}{" "}
        </div>
    );
}
function AddProduct({
    products,
    onClose,
    onSave,
}: {
    products: Product[];
    onClose: () => void;
    onSave: (p: Product) => Promise<void>;
}) {
    const dialog = useRef<HTMLDialogElement>(null);
    const [error, setError] = useState("");
    useEffect(() => {
        const element = dialog.current;
        element?.showModal();
        return () => element?.close();
    }, []);
    async function save(e: FormEvent<HTMLFormElement>) {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const id = String(data.get("id")).trim().toUpperCase();
        const name = String(data.get("name")).trim();
        const available = Number(data.get("quantity"));
        if (
            !/^[A-Z0-9_-]{1,24}$/.test(id) ||
            !name ||
            name.length > 60 ||
            !Number.isInteger(available) ||
            available < 0 ||
            available > 1_000_000
        ) {
            setError(
                "Use a valid ID, a name of 1–64 characters and a whole quantity from 0 to 1000000.",
            );
            return;
        }
        if (products.some((p) => p.id === id)) {
            setError("That product ID already exists. Choose a different ID.");
            return;
        }
        try {
            await onSave({
                id,
                name,
                available,
                category: "Uncategorized",
                reserved: 0,
                symbol: "📦",
                tone: "mint",
            });
        } catch (error) {
            setError(
                error instanceof Error
                    ? error.message
                    : "Could not save the product.",
            );
        }
    }
    return (
        <dialog
            ref={dialog}
            onCancel={(e) => {
                e.preventDefault();
                onClose();
            }}
            aria-labelledby="add-title"
        >
            <form onSubmit={save}>
                <div className="section-heading">
                    <div>
                        <span className="mini-label">SERVER INVENTORY</span>
                        <h2 id="add-title">Add product</h2>
                    </div>
                    <button
                        type="button"
                        className="icon-btn"
                        aria-label="Close product form"
                        onClick={onClose}
                    >
                        <X size={20} />
                    </button>
                </div>
                <p className="muted">
                    Create a product and save its opening stock to the server.
                </p>
                <label htmlFor="product-id">Product ID</label>
                <input
                    autoFocus
                    id="product-id"
                    name="id"
                    required
                    maxLength={24}
                    placeholder="FR-009"
                />
                <label htmlFor="product-name">Product name</label>
                <input
                    id="product-name"
                    name="name"
                    required
                    maxLength={60}
                    placeholder="Fresh papaya"
                />
                <label htmlFor="product-quantity">Opening stock (whole units)</label>
                <input
                    id="product-quantity"
                    name="quantity"
                    type="number"
                    min="0"
                    max="1000000"
                    step="1"
                    defaultValue="0"
                    required
                />
                <p role="alert" className="error">
                    {error}
                </p>
                <div className="modal-actions">
                    <button
                        type="button"
                        className="btn white"
                        onClick={onClose}
                    >
                        Cancel
                    </button>
                    <button className="btn primary" type="submit">
                        Save product
                    </button>
                </div>
            </form>
        </dialog>
    );
}

function StockChange({
    product,
    onClose,
    onSave,
}: {
    product: { id: string; name: string; action: "receive" | "withdraw" };
    onClose: () => void;
    onSave: (quantity: number) => Promise<void>;
}) {
    const dialog = useRef<HTMLDialogElement>(null);
    const [error, setError] = useState("");
    const [saving, setSaving] = useState(false);
    useEffect(() => {
        const element = dialog.current;
        element?.showModal();
        return () => element?.close();
    }, []);
    async function save(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const quantity = Number(form.get("quantity"));
        if (!Number.isInteger(quantity) || quantity < 1 || quantity > 1_000_000) {
            setError("Enter a whole quantity from 1 to 1000000.");
            return;
        }
        setSaving(true);
        setError("");
        try {
            await onSave(quantity);
        } catch (failure) {
            setError(
                failure instanceof Error
                    ? failure.message
                    : "Could not update stock.",
            );
        } finally {
            setSaving(false);
        }
    }
    const receiving = product.action === "receive";
    return (
        <dialog ref={dialog} aria-labelledby="stock-title">
            <form onSubmit={save}>
                <div className="section-heading">
                    <div>
                        <span className="mini-label">SERVER INVENTORY</span>
                        <h2 id="stock-title">{receiving ? "Receive stock" : "Withdraw stock"}</h2>
                    </div>
                    <button
                        type="button"
                        className="icon-btn"
                        aria-label="Close stock form"
                        onClick={onClose}
                    >
                        <X size={20} />
                    </button>
                </div>
                <p className="muted">{product.name} · {product.id}</p>
                <label htmlFor="stock-quantity">Quantity (whole units)</label>
                <input
                    autoFocus
                    id="stock-quantity"
                    name="quantity"
                    type="number"
                    min="1"
                    max="1000000"
                    step="1"
                    defaultValue="1"
                    required
                />
                <p role="alert" className="error">{error}</p>
                <div className="modal-actions">
                    <button type="button" className="btn white" onClick={onClose}>
                        Cancel
                    </button>
                    <button className="btn primary" type="submit" disabled={saving}>
                        {saving ? "Saving…" : receiving ? "Receive stock" : "Withdraw stock"}
                    </button>
                </div>
            </form>
        </dialog>
    );
}
