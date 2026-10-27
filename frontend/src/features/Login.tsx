import { useState, type FormEvent } from "react";
import { ArrowRight, ShieldCheck, Sparkles, Truck } from "lucide-react";
import Brand from "../components/Brand";
import SupplyIllustration from "../components/SupplyIllustration";
export default function Login({
    onLogin,
    onRegister,
}: {
    onLogin: (username: string, password: string) => Promise<void>;
    onRegister: (
        username: string,
        password: string,
        role: "SUPPLIER" | "SELLER",
    ) => Promise<void>;
}) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [registering, setRegistering] = useState(false);
    const [role, setRole] = useState<"SUPPLIER" | "SELLER">("SELLER");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    async function submit(e: FormEvent) {
        e.preventDefault();
        setBusy(true);
        setError("");
        setNotice("");
        try {
            if (registering) {
                await onRegister(username.trim(), password, role);
                setRegistering(false);
                setPassword("");
                setNotice("Account created. Sign in with your new account.");
            } else {
                await onLogin(username.trim(), password);
                setPassword("");
            }
        } catch (failure) {
            setError(
                failure instanceof Error
                    ? failure.message
                    : "Sign-in failed. Try again.",
            );
        } finally {
            setBusy(false);
        }
    }
    return (
        <main className="login-page">
            <section className="login-story">
                <Brand light />
                <div className="story-copy">
                    <span className="eyebrow">
                        <span className="live-dot" /> FRESH THINKING. BETTER
                        CONNECTIONS.
                    </span>
                    <h1>
                        Good things
                        <br />
                        grow <em>together.</em>
                    </h1>
                    <p>
                        From your first supplier to your next delivery.
                        <br />
                        Bring your whole supply chain into one happy place.
                    </p>
                </div>
                <SupplyIllustration />
                <div className="story-benefits">
                    <span>
                        <Truck size={18} /> Smarter deliveries
                    </span>
                    <span>
                        <ShieldCheck size={18} /> Connected teams
                    </span>
                </div>
                <footer>Made for the people who keep things moving.</footer>
            </section>
            <section className="login-form-area">
                <span className="preview-pill">
                    <ShieldCheck size={14} /> SECURE JAVA SERVER SIGN-IN
                </span>
                <div className="login-form-wrap">
                    <span className="mini-label">
                        YOUR NEXT CHAPTER STARTS HERE
                    </span>
                    <h2>
                        {registering
                            ? "Create your account"
                            : "Welcome to SupplyGo"}
                        <span>.</span>
                    </h2>
                    <p className="muted">
                        {registering
                            ? "Join your partners on one connected supply chain."
                            : "Sign in to your SupplyGo workspace."}
                    </p>
                    <form onSubmit={submit}>
                        <label htmlFor="username">Username</label>
                        <input
                            id="username"
                            autoComplete="username"
                            minLength={3}
                            maxLength={32}
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                            placeholder="Enter your username"
                        />
                        <label htmlFor="password">Password</label>
                        <input
                            id="password"
                            type="password"
                            autoComplete={
                                registering
                                    ? "new-password"
                                    : "current-password"
                            }
                            minLength={registering ? 12 : 1}
                            maxLength={128}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                            placeholder="Enter your password"
                        />
                        {registering && (
                            <>
                                <label htmlFor="account-role">
                                    Account type
                                </label>
                                <select
                                    id="account-role"
                                    value={role}
                                    onChange={(e) =>
                                        setRole(
                                            e.target.value as
                                                "SUPPLIER" | "SELLER",
                                        )
                                    }
                                >
                                    <option value="SELLER">Seller</option>
                                    <option value="SUPPLIER">Supplier</option>
                                </select>
                                <p className="muted password-hint">
                                    Use a passphrase of 12–128 characters. Admin
                                    and manager accounts are created by the
                                    operator.
                                </p>
                            </>
                        )}
                        {error && (
                            <p className="auth-error" role="alert">
                                {error}
                            </p>
                        )}
                        {notice && (
                            <p className="auth-notice" role="status">
                                {notice}
                            </p>
                        )}
                        <div className="preview-note">
                            <ShieldCheck size={20} />
                            <p>
                                <strong>
                                    Protected by your SupplyGo server.
                                </strong>
                                <br />
                                Your sign-in is verified over TLS. The browser
                                keeps only a protected session cookie.
                            </p>
                        </div>
                        <button
                            className="btn primary login-submit"
                            type="submit"
                            disabled={busy}
                        >
                            {busy
                                ? "Connecting securely…"
                                : registering
                                  ? "Create account"
                                  : "Sign in"}{" "}
                            <ArrowRight size={18} />
                        </button>
                    </form>
                    <button
                        className="auth-switch"
                        type="button"
                        disabled={busy}
                        onClick={() => {
                            setRegistering((v) => !v);
                            setError("");
                            setNotice("");
                        }}
                    >
                        {registering
                            ? "Already have an account? Sign in"
                            : "New supplier or seller? Create an account"}{" "}
                        <Sparkles size={14} />
                    </button>
                </div>
                <footer>
                    SupplyGo / A little more connected. A lot more possible.
                </footer>
            </section>
        </main>
    );
}
