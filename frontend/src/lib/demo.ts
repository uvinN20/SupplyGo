export const roles = [
    "Admin",
    "Seller",
    "Supplier",
    "Production Manager",
    "Seller Manager",
    "Supplier Manager",
] as const;
export type Role = (typeof roles)[number];
export type Product = {
    id: string;
    name: string;
    category: "Tropical" | "Citrus" | "Other";
    available: number;
    reserved: number;
    symbol: string;
    tone: string;
};
export const initialProducts: Product[] = [
    {
        id: "FR-001",
        name: "Golden pineapple",
        category: "Tropical",
        available: 240,
        reserved: 40,
        symbol: "🍍",
        tone: "yellow",
    },
    {
        id: "FR-002",
        name: "Fresh bananas",
        category: "Tropical",
        available: 480,
        reserved: 60,
        symbol: "🍌",
        tone: "yellow",
    },
    {
        id: "FR-003",
        name: "Sweet oranges",
        category: "Citrus",
        available: 180,
        reserved: 30,
        symbol: "🍊",
        tone: "peach",
    },
    {
        id: "FR-004",
        name: "Red apples",
        category: "Other",
        available: 32,
        reserved: 12,
        symbol: "🍎",
        tone: "pink",
    },
    {
        id: "FR-005",
        name: "Ripe mangoes",
        category: "Tropical",
        available: 160,
        reserved: 25,
        symbol: "🥭",
        tone: "peach",
    },
    {
        id: "FR-006",
        name: "Green grapes",
        category: "Other",
        available: 24,
        reserved: 8,
        symbol: "🍇",
        tone: "purple",
    },
    {
        id: "FR-007",
        name: "Fresh watermelon",
        category: "Other",
        available: 95,
        reserved: 15,
        symbol: "🍉",
        tone: "mint",
    },
    {
        id: "FR-008",
        name: "Lime",
        category: "Citrus",
        available: 0,
        reserved: 20,
        symbol: "🍋",
        tone: "mint",
    },
];
export const sampleOrders = [
    {
        id: "SG-1048",
        customer: "Green Basket",
        initials: "GB",
        product: "Golden pineapple",
        units: 40,
        status: "Pending",
        day: "Mon",
    },
    {
        id: "SG-1047",
        customer: "Fresh Mart",
        initials: "FM",
        product: "Fresh bananas",
        units: 60,
        status: "Approved",
        day: "Tue",
    },
    {
        id: "SG-1046",
        customer: "City Grocers",
        initials: "CG",
        product: "Sweet oranges",
        units: 30,
        status: "Fulfilled",
        day: "Wed",
    },
    {
        id: "SG-1045",
        customer: "Green Basket",
        initials: "GB",
        product: "Ripe mangoes",
        units: 25,
        status: "Fulfilled",
        day: "Thu",
    },
    {
        id: "SG-1044",
        customer: "Fresh Mart",
        initials: "FM",
        product: "Fresh watermelon",
        units: 15,
        status: "Pending",
        day: "Fri",
    },
    {
        id: "SG-1043",
        customer: "City Grocers",
        initials: "CG",
        product: "Red apples",
        units: 12,
        status: "Cancelled",
        day: "Sat",
    },
];
export const stockState = (p: Product) =>
    p.available === 0
        ? "Out of stock"
        : p.available < 50
          ? "Low stock"
          : "In stock";
export function filterProducts(
    products: Product[],
    query: string,
    category: string,
    status: string,
) {
    return products.filter(
        (p) =>
            `${p.name} ${p.id}`
                .toLowerCase()
                .includes(query.trim().toLowerCase()) &&
            (category === "All categories" || p.category === category) &&
            (status === "All stock" || stockState(p) === status),
    );
}
export function metrics(products: Product[]) {
    return {
        products: products.length,
        available: products.reduce((n, p) => n + p.available, 0),
        reserved: products.reduce((n, p) => n + p.reserved, 0),
        low: products.filter((p) => p.available < 50).length,
    };
}
export const canEdit = (role: Role) =>
    role === "Admin" || role === "Production Manager";
