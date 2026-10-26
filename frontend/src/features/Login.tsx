import { useState, type FormEvent } from "react";
import { ArrowRight, ShieldCheck, Sparkles, Truck } from "lucide-react";
import Brand from "../components/Brand";
import SupplyIllustration from "../components/SupplyIllustration";
import { roles, type Role } from "../lib/demo";
export default function Login({
    onEnter,
}: {
    onEnter: (name: string, role: Role) => void;
}) {
    const [name, setName] = useState("Uvin");
    const [role, setRole] = useState<Role>("Admin");
    function enter(e: FormEvent) {
        e.preventDefault();
        if (name.trim()) onEnter(name.trim(), role);
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
                    <Sparkles size={14} /> Phase 1 · Design preview
                </span>
                <div className="login-form-wrap">
                    <span className="mini-label">
                        YOUR NEXT CHAPTER STARTS HERE
                    </span>
                    <h2>
                        Welcome to SupplyGo<span>.</span>
                    </h2>
                    <p className="muted">
                        A fresh perspective on your everyday operations.
                    </p>
                    <form onSubmit={enter}>
                        <label htmlFor="display-name">Your display name</label>
                        <input
                            id="display-name"
                            autoComplete="off"
                            maxLength={32}
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            required
                            placeholder="Enter your name"
                        />
                        <label htmlFor="preview-role">Explore as</label>
                        <select
                            id="preview-role"
                            value={role}
                            onChange={(e) => setRole(e.target.value as Role)}
                        >
                            {roles.map((r) => (
                                <option key={r}>{r}</option>
                            ))}
                        </select>
                        <div className="preview-note">
                            <ShieldCheck size={20} />
                            <p>
                                <strong>
                                    No password needed for this preview.
                                </strong>
                                <br />
                                Sample data only. Real sign-in and Java backend
                                integration come in Phase 2. Don’t enter private
                                account details.
                            </p>
                        </div>
                        <button
                            className="btn primary login-submit"
                            type="submit"
                        >
                            Explore your workspace <ArrowRight size={18} />
                        </button>
                    </form>
                    <div className="login-trust">
                        <span className="tiny-dot" /> Browser preview · Nothing
                        is sent to a server
                    </div>
                </div>
                <footer>
                    SupplyGo / A little more connected. A lot more possible.
                </footer>
            </section>
        </main>
    );
}
