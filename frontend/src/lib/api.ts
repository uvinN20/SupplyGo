import type { Role } from "./demo";
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
