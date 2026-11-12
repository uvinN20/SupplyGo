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

export type BusinessProfile = {
    username: string;
    role: Role;
    fullName: string;
    businessName: string;
    contactNumber: string;
    location: string;
    createdAt: string;
    updatedAt: string;
};
export type ProfileDetails = Pick<BusinessProfile, "fullName" | "businessName" | "contactNumber" | "location">;
type BusinessProfileResponse = { profile: Record<string, unknown> | null };
type BusinessDirectoryResponse = { profiles: Array<Record<string, unknown>>; nextOffset: number };

function profileFromApi(value: Record<string, unknown>): BusinessProfile {
    if (typeof value.username !== "string" || typeof value.role !== "string"
        || typeof value.fullName !== "string" || typeof value.businessName !== "string"
        || typeof value.contactNumber !== "string" || typeof value.location !== "string"
        || typeof value.createdAt !== "string" || typeof value.updatedAt !== "string")
        throw new Error("SupplyGo returned invalid business profile data.");
    return {
        username: value.username,
        role: roleFromApi(value.role),
        fullName: value.fullName,
        businessName: value.businessName,
        contactNumber: value.contactNumber,
        location: value.location,
        createdAt: value.createdAt,
        updatedAt: value.updatedAt,
    };
}

export async function getBusinessProfile(): Promise<BusinessProfile | null> {
    const body = await readData<BusinessProfileResponse>(await request("/api/business-profile"));
    return body.profile === null ? null : profileFromApi(body.profile);
}

export async function saveBusinessProfile(details: ProfileDetails): Promise<BusinessProfile> {
    const body = await readData<BusinessProfileResponse>(
        await request("/api/business-profile", {
            method: "PUT",
            headers: csrfHeaders,
            body: JSON.stringify({
                fullName: details.fullName.trim(),
                businessName: details.businessName.trim(),
                contactNumber: details.contactNumber.trim(),
                location: details.location.trim(),
            }),
        }),
    );
    if (body.profile === null) throw new Error("SupplyGo did not return the saved business profile.");
    return profileFromApi(body.profile);
}

export async function listBusinessProfiles(
    directory: "suppliers" | "sellers",
    offset = 0,
): Promise<{ profiles: BusinessProfile[]; nextOffset: number }> {
    const body = await readData<BusinessDirectoryResponse>(
        await request(`/api/business-profiles/${directory}?offset=${offset}`),
    );
    if (!Array.isArray(body.profiles) || !Number.isInteger(body.nextOffset))
        throw new Error("SupplyGo returned invalid business directory data.");
    return { profiles: body.profiles.map(profileFromApi), nextOffset: body.nextOffset };
}

export type RouteLocation = { id: string; name: string };
export type RouteRoad = { from: string; to: string; meters: number };
export type ShortestRoute = { path: string[]; meters: number };

export async function listRouteLocations(): Promise<RouteLocation[]> {
    const all: RouteLocation[] = [];
    let offset = 0;
    for (let page = 0; page < 10; page++) {
        const body = await readData<{ locations: RouteLocation[]; nextOffset: number }>(
            await request(`/api/routes/locations?offset=${offset}`),
        );
        if (!Array.isArray(body.locations) || !Number.isInteger(body.nextOffset))
            throw new Error("SupplyGo returned invalid route locations.");
        all.push(...body.locations);
        if (body.nextOffset < 0) return all;
        if (body.nextOffset <= offset || body.nextOffset > 1000)
            throw new Error("SupplyGo returned invalid route pagination.");
        offset = body.nextOffset;
    }
    throw new Error("SupplyGo returned too many location pages.");
}

export async function listRouteRoads(): Promise<RouteRoad[]> {
    const all: RouteRoad[] = [];
    let offset = 0;
    for (let page = 0; page < 100; page++) {
        const body = await readData<{ roads: RouteRoad[]; nextOffset: number }>(
            await request(`/api/routes/roads?offset=${offset}`),
        );
        if (!Array.isArray(body.roads) || !Number.isInteger(body.nextOffset))
            throw new Error("SupplyGo returned invalid road data.");
        all.push(...body.roads);
        if (body.nextOffset < 0) return all;
        if (body.nextOffset <= offset || body.nextOffset > 1000)
            throw new Error("SupplyGo returned invalid route pagination.");
        offset = body.nextOffset;
    }
    throw new Error("SupplyGo returned too many road pages.");
}

export async function saveRouteLocation(id: string, name: string): Promise<void> {
    await readData<{ status: string }>(
        await request("/api/routes/locations", {
            method: "POST",
            headers: csrfHeaders,
            body: JSON.stringify({ id, name: name.trim() }),
        }),
    );
}

export async function saveRouteRoad(from: string, to: string, meters: number): Promise<void> {
    await readData<{ status: string }>(
        await request("/api/routes/roads", {
            method: "POST",
            headers: csrfHeaders,
            body: JSON.stringify({ from, to, meters }),
        }),
    );
}

export async function findShortestRoute(from: string, to: string): Promise<ShortestRoute> {
    const body = await readData<{ route: ShortestRoute }>(
        await request(`/api/routes/shortest?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
    );
    if (!Array.isArray(body.route?.path) || !Number.isSafeInteger(body.route.meters))
        throw new Error("SupplyGo returned an invalid route result.");
    return body.route;
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


export type ChatMessage = {
    id: number;
    sender: string;
    role: string;
    text: string;
    time: string;
};

type ChatBatchResponse = {
    messages: ChatMessage[];
    cursor: number;
    truncated: boolean;
};

export async function joinChat(): Promise<{ username: string }> {
    const body = await readData<{ status: string; username: string }>(
        await request("/api/chat/session", {
            method: "POST",
            headers: csrfHeaders,
        }),
    );
    if (body.status !== "joined" || typeof body.username !== "string")
        throw new Error("SupplyGo returned an invalid chat session.");
    return { username: body.username };
}

export async function leaveChat(): Promise<void> {
    const response = await request("/api/chat/session", {
        method: "DELETE",
        headers: { "X-SupplyGo-Request": "1" },
    });
    await readData<{ status: string }>(response);
}

export async function sendChatMessage(text: string): Promise<string> {
    const body = await readData<{ status: string; message: string }>(
        await request("/api/chat/messages", {
            method: "POST",
            headers: csrfHeaders,
            body: JSON.stringify({ text }),
        }),
    );
    if (body.status !== "accepted" || typeof body.message !== "string")
        throw new Error("SupplyGo did not confirm this chat message.");
    return body.message;
}

export async function listChatMessages(after: number): Promise<ChatBatchResponse> {
    if (!Number.isSafeInteger(after) || after < 0)
        throw new Error("Invalid chat message cursor.");
    const body = await readData<ChatBatchResponse>(
        await request(`/api/chat/messages?after=${after}`),
    );
    if (!Array.isArray(body.messages) || !Number.isSafeInteger(body.cursor)
        || typeof body.truncated !== "boolean"
        || body.messages.some((message) => !Number.isSafeInteger(message.id)
            || typeof message.sender !== "string" || typeof message.role !== "string"
            || typeof message.text !== "string" || typeof message.time !== "string"))
        throw new Error("SupplyGo returned invalid chat messages.");
    return body;
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
