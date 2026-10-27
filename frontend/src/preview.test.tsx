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
        render(<App />);
        expect(await screen.findByText("yasiru")).toBeInTheDocument();
        expect(
            screen.getByText(
                /Workspace content and actions below are sample data/,
            ),
        ).toBeInTheDocument();
    });
    it("signs in through the API and signs out of the server session", async () => {
        const user = await enter();
        expect(api.signIn).toHaveBeenCalledWith(
            "admin",
            "a strong test password",
        );
        expect(screen.getByText(/sample data/)).toBeInTheDocument();
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
    it("filters sample orders and explains planned destinations", async () => {
        const user = await enter();
        await user.click(screen.getByRole("button", { name: "Orders" }));
        await user.selectOptions(
            screen.getByLabelText("Order status"),
            "Cancelled",
        );
        expect(screen.getByText("#SG-1043")).toBeInTheDocument();
        expect(screen.queryByText("#SG-1048")).not.toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Chat" }));
        expect(screen.getByText("Phase 8 · Planned")).toBeInTheDocument();
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
