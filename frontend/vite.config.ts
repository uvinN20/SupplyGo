/** SupplyGo code explanation: Configures the frontend development and production build. */
import { defineConfig } from "vite";

export default defineConfig({
    server: {
        proxy: {
            "/api": {
                target: "http://127.0.0.1:7080",
                changeOrigin: false,
            },
        },
    },
});
