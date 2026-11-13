// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";
import { initialProducts, metrics, filterProducts } from "./lib/demo";

const api = vi.hoisted(() => ({
    currentSession: vi.fn(),
    signIn: vi.fn(),
    signOut: vi.fn(),
    createAccount: vi.fn(),
    listInventory: vi.fn(),
    createInventoryProduct: vi.fn(),
    listOrders: vi.fn(),
    submitOrder: vi.fn(),
    updateOrderStatus: vi.fn(),
    getBusinessProfile: vi.fn(),
    saveBusinessProfile: vi.fn(),
    listBusinessProfiles: vi.fn(),
    listRouteLocations: vi.fn(),
    listRouteRoads: vi.fn(),
    saveRouteLocation: vi.fn(),
    saveRouteRoad: vi.fn(),
    findShortestRoute: vi.fn(),
    joinChat: vi.fn(),
    leaveChat: vi.fn(),
    listChatMessages: vi.fn(),
    sendChatMessage: vi.fn(),
}));
vi.mock("./lib/api", () => api);

beforeAll(() => {
    window.scrollTo = vi.fn();
    HTMLDialogElement.prototype.showModal = function () {
        this.setAttribute("open", "");
    };
    HTMLDialogElement.prototype.close = function () {
        this.removeAttribute("open");
    };
});
afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});
async function enter(role = "Admin") {
    api.currentSession.mockResolvedValue(null);
    api.signIn.mockResolvedValue({ username: role.toLowerCase(), role });
    api.signOut.mockResolvedValue(undefined);
    api.listInventory.mockResolvedValue(initialProducts);
    api.createInventoryProduct.mockResolvedValue(undefined);
    api.listOrders.mockResolvedValue([]);
    api.submitOrder.mockResolvedValue(undefined);
    api.updateOrderStatus.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<App />);
    const username = await screen.findByLabelText("Username");
    await user.type(username, role.toLowerCase());
    await user.type(
        screen.getByLabelText("Password"),
        "a strong test password",
    );
    await user.click(screen.getByRole("button", { name: /Sign in/ }));
    await screen.findByRole("heading", { name: /Your day, at a glance/ });
    return user;
}
describe("Authenticated web preview", () => {
    it("restores the server session and clearly identifies sample workspace data", async () => {
        api.currentSession.mockResolvedValue({
            username: "yasiru",
            role: "Admin",
        });
        api.listInventory.mockResolvedValue(initialProducts);
        api.listOrders.mockResolvedValue([]);
        render(<App />);
        expect(await screen.findByText("yasiru")).toBeInTheDocument();
        expect(
            screen.getByText(
                /Dashboard totals and recent orders are loaded/,
            ),
        ).toBeInTheDocument();
    });
    it("signs in through the API and signs out of the server session", async () => {
        const user = await enter();
        expect(api.signIn).toHaveBeenCalledWith(
            "admin",
            "a strong test password",
        );
        expect(
            screen.getByText(
                /Dashboard totals and recent orders are loaded/,
            ),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Sign out" }));
        expect(api.signOut).toHaveBeenCalledOnce();
        expect(
            await screen.findByRole("button", { name: /Sign in/ }),
        ).toBeInTheDocument();
    });
    it("searches every sample record rather than only the current page", async () => {
        const user = await enter();
        await user.click(screen.getByRole("button", { name: "Inventory" }));
        await user.type(
            screen.getByRole("textbox", { name: "Search inventory" }),
            "lime",
        );
        expect(screen.getByText("FR-008")).toBeInTheDocument();
        expect(screen.queryByText("FR-001")).not.toBeInTheDocument();
        await user.clear(
            screen.getByRole("textbox", { name: "Search inventory" }),
        );
        await user.click(
            screen.getByRole("button", { name: "Next inventory page" }),
        );
        expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
        await user.selectOptions(
            screen.getByLabelText("Stock status"),
            "Out of stock",
        );
        expect(screen.getByText("FR-008")).toBeInTheDocument();
        expect(screen.getByText("Page 1 of 1")).toBeInTheDocument();
    });
    it("creates a seller account through the API and prompts the new user to sign in", async () => {
        api.currentSession.mockResolvedValue(null);
        api.createAccount.mockResolvedValue({
            username: "newshop",
            role: "SELLER",
        });
        render(<App />);
        const user = userEvent.setup();
        await user.click(
            await screen.findByRole("button", { name: /Create an account/ }),
        );
        await user.type(screen.getByLabelText("Username"), "newshop");
        await user.type(
            screen.getByLabelText("Password"),
            "a strong new password",
        );
        await user.type(
            screen.getByLabelText("Re-enter password"),
            "a strong new password",
        );
        await user.selectOptions(
            screen.getByLabelText("Account type"),
            "SELLER",
        );
        await user.click(
            screen.getByRole("button", { name: /Create account/ }),
        );
        expect(api.createAccount).toHaveBeenCalledWith(
            "newshop",
            "a strong new password",
            "SELLER",
        );
        expect(await screen.findByRole("status")).toHaveTextContent(
            "Account created",
        );
    });
    it("filters live orders and joins the shared live chat room", async () => {
        const user = await enter();
        api.listOrders.mockResolvedValue([
            { id: 1043, clientOrderId: "00000000-0000-4000-8000-000000000043", sellerUsername: "freshmart", productId: "FR-001", quantity: 12, status: "CANCELLED", createdAt: "2026-11-12T00:00:00Z", updatedAt: "2026-11-12T00:00:00Z", updatedBy: "admin" },
            { id: 1048, clientOrderId: "00000000-0000-4000-8000-000000000048", sellerUsername: "freshmart", productId: "FR-002", quantity: 40, status: "PENDING", createdAt: "2026-11-12T00:00:00Z", updatedAt: "2026-11-12T00:00:00Z", updatedBy: "freshmart" },
        ]);
        await user.click(screen.getByRole("button", { name: "Orders" }));
        await user.selectOptions(
            screen.getByLabelText("Order status"),
            "Cancelled",
        );
        expect(await screen.findByText("#SG-1043")).toBeInTheDocument();
        expect(screen.queryByText("#SG-1048")).not.toBeInTheDocument();
        api.joinChat.mockResolvedValue({ username: "admin" });
        api.leaveChat.mockResolvedValue(undefined);
        api.listChatMessages.mockResolvedValue({ messages: [], cursor: 0, truncated: false });
        await user.click(screen.getByRole("button", { name: "Chat" }));
        expect(screen.getByRole("heading", { name: "SupplyGo shared room" })).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Join room" }));
        expect(await screen.findByText("Joined · live messages")).toBeInTheDocument();
        expect(api.joinChat).toHaveBeenCalledOnce();
        expect(api.listChatMessages).toHaveBeenCalled();
    });
    it("calculates honest totals and combines category/status/search filters", () => {
        expect(metrics(initialProducts)).toEqual({
            products: 8,
            available: 1211,
            reserved: 210,
            low: 3,
        });
        expect(
            filterProducts(
                initialProducts,
                "FR-",
                "Citrus",
                "Out of stock",
            ).map((p) => p.id),
        ).toEqual(["FR-008"]);
    });
});
