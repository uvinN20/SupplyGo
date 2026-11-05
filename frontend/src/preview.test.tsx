// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";
import { initialProducts, metrics, filterProducts } from "./lib/demo";
beforeAll(() => {
    window.scrollTo = vi.fn();
    HTMLDialogElement.prototype.showModal = function () {
        this.setAttribute("open", "");
    };
    HTMLDialogElement.prototype.close = function () {
        this.removeAttribute("open");
    };
});
afterEach(cleanup);
async function enter(role = "Admin") {
    const user = userEvent.setup();
    render(<App />);
    await user.selectOptions(screen.getByLabelText("Explore as"), role);
    await user.click(
        screen.getByRole("button", { name: /Explore your workspace/ }),
    );
    return user;
}
describe("Preview workflows", () => {
    it("opens without a password or backend and clearly identifies sample data", async () => {
        const user = await enter();
        expect(
            screen.getByRole("heading", { name: /Your day, at a glance/ }),
        ).toBeInTheDocument();
        expect(
            screen.getByText(/Not connected to the Java backend/),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Leave preview" }));
        expect(
            screen.getByRole("button", { name: /Explore your workspace/ }),
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
    it("rejects duplicate IDs, adds a sample product and resets it on exit", async () => {
        const user = await enter();
        await user.click(screen.getByRole("button", { name: "Inventory" }));
        await user.click(
            screen.getByRole("button", {
                name: "Add sample product",
            }),
        );
        await user.type(screen.getByLabelText("Product ID"), "FR-001");
        await user.type(screen.getByLabelText("Product name"), "Papaya");
        await user.click(
            screen
                .getAllByRole("button", {
                    name: "Add sample product",
                })
                .at(-1)!,
        );
        expect(screen.getByRole("alert")).toHaveTextContent("already exists");
        await user.clear(screen.getByLabelText("Product ID"));
        await user.type(screen.getByLabelText("Product ID"), "FR-009");
        await user.click(
            screen
                .getAllByRole("button", {
                    name: "Add sample product",
                })
                .at(-1)!,
        );
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(screen.getByText("Papaya")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Leave preview" }));
        await user.click(
            screen.getByRole("button", { name: /Explore your workspace/ }),
        );
        await user.click(screen.getByRole("button", { name: "Inventory" }));
        await user.type(
            screen.getByRole("textbox", { name: "Search inventory" }),
            "Papaya",
        );
        expect(screen.getByText("No matching products")).toBeInTheDocument();
    });
    it("shows seller browsing without sample inventory editing", async () => {
        const user = await enter("Seller");
        await user.click(screen.getByRole("button", { name: "Inventory" }));
        expect(
            screen.queryByRole("button", {
                name: "Add sample product",
            }),
        ).not.toBeInTheDocument();
        expect(
            screen.getByText(/Seller preview: inventory browsing only/),
        ).toBeInTheDocument();
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
        const m = metrics(initialProducts);
        expect(m).toEqual({
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
