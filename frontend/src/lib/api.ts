import type { Product, Role } from "./demo";
export type AuthUser = { username: string; role: Role };
const csrfHeaders = {
    "Content-Type": "application/json",
    "X-SupplyGo-Request": "1",
};
function roleFromApi(value: string): Role {
    const roles: Record<string, Role> = {
        ADMIN: "Admin",
        SELLER: "Seller",
        SUPPLIER: "Supplier",
        PRODUCTION_MANAGER: "Production Manager",
        SELLER_MANAGER: "Seller Manager",
        SUPPLIER_MANAGER: "Supplier Manager",
    };
    const role = roles[value];
    if (!role) throw new Error("The server returned an unknown account role.");
    return role;
}
async function request(path: string, init?: RequestInit): Promise<Response> {
    let response: Response;
    try {
        response = await fetch(path, {
            ...init,
            credentials: "same-origin",
            headers: { ...(init?.headers ?? {}) },
        });
    } catch {
        throw new Error(
            "Cannot reach SupplyGo. Check that the Java server and web gateway are running.",
        );
    }
    return response;
}
async function readUser(response: Response): Promise<AuthUser> {
    const body = await response.json().catch(() => null);
    if (!response.ok) {
        const message =
            body?.error?.message ||
            (response.status === 401
                ? "Sign in to continue."
                : "SupplyGo could not complete this request.");
        throw new Error(message);
    }
    if (typeof body?.username !== "string" || typeof body?.role !== "string")
        throw new Error("SupplyGo returned an invalid account response.");
    return { username: body.username, role: roleFromApi(body.role) };
}
export async function currentSession(): Promise<AuthUser | null> {
    const response = await request("/api/session");
    if (response.status === 401) return null;
    return readUser(response);
}
export async function signIn(
    username: string,
    password: string,
): Promise<AuthUser> {
    const response = await request("/api/session", {
        method: "POST",
        headers: csrfHeaders,
        body: JSON.stringify({ username, password }),
    });
    return readUser(response);
}
export async function createAccount(
    username: string,
    password: string,
    role: "SUPPLIER" | "SELLER",
): Promise<AuthUser> {
    const response = await request("/api/accounts", {
        method: "POST",
        headers: csrfHeaders,
        body: JSON.stringify({ username, password, role }),
    });
    return readUser(response);
}
export async function signOut(): Promise<void> {
    const response = await request("/api/session", {
        method: "DELETE",
        headers: { "X-SupplyGo-Request": "1" },
    });
    if (!response.ok) throw new Error("Could not end the SupplyGo session.");
}

type InventoryResponse = {
    products: Array<{
        id: string;
        name: string;
        quantity: number;
        reserved: number;
    }>;
};

async function readData<T>(response: Response): Promise<T> {
    const body = await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(
            body?.error?.message || "SupplyGo could not complete this request.",
        );
    }
    return body as T;
}

export async function listInventory(): Promise<Product[]> {
    const body = await readData<InventoryResponse>(
        await request("/api/inventory"),
    );
    return body.products.map((item) => ({
        id: item.id,
        name: item.name,
        category: "Uncategorized",
        available: item.quantity,
        reserved: item.reserved,
        symbol: "📦",
        tone: "mint",
    }));
}

export async function createInventoryProduct(
    product: Product,
): Promise<void> {
    await readData<{ status: string }>(
        await request("/api/inventory/products", {
            method: "POST",
            headers: csrfHeaders,
            body: JSON.stringify({
                id: product.id,
                name: product.name,
                quantity: product.available,
            }),
        }),
    );
}

export async function changeInventoryStock(
    productId: string,
    quantity: number,
    action: "receive" | "withdraw",
): Promise<void> {
    await readData<{ status: string }>(
        await request("/api/inventory/stock", {
            method: "POST",
            headers: csrfHeaders,
            body: JSON.stringify({ productId, quantity, action }),
        }),
    );
}


export type OrderStatus = "PENDING" | "APPROVED" | "FULFILLED" | "CANCELLED";
export type LiveOrder = {
    id: number;
    clientOrderId: string;
    sellerUsername: string;
    productId: string;
    quantity: number;
    status: OrderStatus;
    createdAt: string;
    updatedAt: string;
    updatedBy: string;
};

type OrderResponse = { order: LiveOrder };
type OrdersResponse = { orders: LiveOrder[] };

export async function listOrders(): Promise<LiveOrder[]> {
    const body = await readData<OrdersResponse>(await request("/api/orders"));
    if (!Array.isArray(body.orders)) throw new Error("SupplyGo returned invalid order data.");
    return body.orders;
}

export async function submitOrder(productId: string, quantity: number): Promise<LiveOrder> {
    const body = await readData<OrderResponse>(
        await request("/api/orders", {
            method: "POST",
            headers: csrfHeaders,
            body: JSON.stringify({
                clientOrderId: crypto.randomUUID(),
                productId,
                quantity,
            }),
        }),
    );
    return body.order;
}

export async function updateOrderStatus(id: number, status: OrderStatus): Promise<LiveOrder> {
    const body = await readData<OrderResponse>(
        await request("/api/orders/status", {
            method: "POST",
            headers: csrfHeaders,
            body: JSON.stringify({ orderId: id, status }),
        }),
    );
    return body.order;
}
