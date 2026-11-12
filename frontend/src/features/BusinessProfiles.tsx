import { useEffect, useMemo, useState, type FormEvent } from "react";
import {
    getBusinessProfile,
    listBusinessProfiles,
    saveBusinessProfile,
    type BusinessProfile,
    type ProfileDetails,
    type AuthUser,
} from "../lib/api";

const blank: ProfileDetails = {
    fullName: "",
    businessName: "",
    contactNumber: "",
    location: "",
};

const fields: Array<{ key: keyof ProfileDetails; label: string; max: number; hint: string }> = [
    { key: "fullName", label: "Full name", max: 64, hint: "Your name as shown to business partners." },
    { key: "businessName", label: "Business name", max: 96, hint: "The name customers and suppliers know." },
    { key: "contactNumber", label: "Contact number", max: 32, hint: "Digits, spaces, brackets or hyphens; optional leading +." },
    { key: "location", label: "Town or location", max: 64, hint: "For example, Matara." },
];

export default function BusinessProfiles({ role }: { role: AuthUser["role"] }) {
    const canEdit = role === "Seller" || role === "Supplier";
    const directories = role === "Admin"
        ? ["suppliers", "sellers"] as const
        : role === "Supplier Manager"
            ? ["suppliers"] as const
            : role === "Seller Manager"
                ? ["sellers"] as const
                : [];
    const [details, setDetails] = useState<ProfileDetails>(blank);
    const [savedProfile, setSavedProfile] = useState<BusinessProfile | null>(null);
    const [directory, setDirectory] = useState<"suppliers" | "sellers">(directories[0] ?? "suppliers");
    const [profiles, setProfiles] = useState<BusinessProfile[]>([]);
    const [offset, setOffset] = useState(0);
    const [nextOffset, setNextOffset] = useState(-1);
    const [query, setQuery] = useState("");
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");

    useEffect(() => {
        let active = true;
        setLoading(true);
        setError("");
        if (!canEdit) {
            setLoading(false);
            return () => { active = false; };
        }
        getBusinessProfile()
            .then((profile) => {
                if (!active) return;
                setSavedProfile(profile);
                setDetails(profile ? {
                    fullName: profile.fullName,
                    businessName: profile.businessName,
                    contactNumber: profile.contactNumber,
                    location: profile.location,
                } : blank);
            })
            .catch((cause: unknown) => {
                if (active) setError(cause instanceof Error ? cause.message : "Could not load your business profile.");
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [canEdit]);

    useEffect(() => {
        let active = true;
        if (canEdit || directories.length === 0) return () => { active = false; };
        setLoading(true);
        setError("");
        listBusinessProfiles(directory, offset)
            .then((page) => {
                if (!active) return;
                setProfiles(page.profiles);
                setNextOffset(page.nextOffset);
            })
            .catch((cause: unknown) => {
                if (active) {
                    setProfiles([]);
                    setNextOffset(-1);
                    setError(cause instanceof Error ? cause.message : "Could not load the business directory.");
                }
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [canEdit, directories.length, directory, offset]);

    const visibleProfiles = useMemo(() => {
        const needle = query.trim().toLowerCase();
        if (!needle) return profiles;
        return profiles.filter((profile) =>
            `${profile.username} ${profile.fullName} ${profile.businessName} ${profile.contactNumber} ${profile.location}`
                .toLowerCase().includes(needle),
        );
    }, [profiles, query]);

    async function save(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true);
        setError("");
        setNotice("");
        try {
            const profile = await saveBusinessProfile(details);
            setSavedProfile(profile);
            setDetails({
                fullName: profile.fullName,
                businessName: profile.businessName,
                contactNumber: profile.contactNumber,
                location: profile.location,
            });
            setNotice("Business profile saved to the SupplyGo server.");
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not save your business profile.");
        } finally {
            setBusy(false);
        }
    }

    function changeDirectory(value: "suppliers" | "sellers") {
        setDirectory(value);
        setOffset(0);
        setQuery("");
        setProfiles([]);
    }

    return (
        <div className="page-enter">
            <div className="page-heading">
                <div>
                    <span className="mini-label">YOUR BUSINESS DETAILS</span>
                    <h1>Business profiles</h1>
                    <p>Keep your partner-facing business information up to date.</p>
                </div>
            </div>
            {canEdit ? (
                <section className="surface business-profile-card">
                    <div className="profile-section-heading">
                        <div>
                            <h2>My business profile</h2>
                            <p>These details are saved securely by your SupplyGo server.</p>
                        </div>
                        {savedProfile && <span className="badge fulfilled">Saved</span>}
                    </div>
                    {error && <p className="auth-error" role="alert">{error}</p>}
                    {notice && <p className="auth-notice" role="status">{notice}</p>}
                    {loading ? <p className="empty-state" role="status">Loading your profile…</p> : (
                        <form className="business-profile-form" onSubmit={save}>
                            {fields.map(({ key, label, max, hint }) => (
                                <label key={key}>
                                    <span>{label}</span>
                                    <input
                                        aria-label={label}
                                        maxLength={max}
                                        required
                                        value={details[key]}
                                        onChange={(event) => setDetails((current) => ({ ...current, [key]: event.target.value }))}
                                        pattern={key === "contactNumber" ? "\\+?[0-9][0-9 ()-]{5,30}[0-9]" : undefined}
                                    />
                                    <small>{hint}</small>
                                </label>
                            ))}
                            <div className="profile-form-actions">
                                <button className="btn primary" type="submit" disabled={busy || loading}>
                                    {busy ? "Saving…" : "Save profile"}
                                </button>
                                {savedProfile && <span>Last saved {new Date(savedProfile.updatedAt).toLocaleString()}</span>}
                            </div>
                        </form>
                    )}
                </section>
            ) : directories.length > 0 ? (
                <section className="surface business-profile-card">
                    <div className="profile-section-heading">
                        <div>
                            <h2>Business directory</h2>
                            <p>Contact details saved by registered suppliers and sellers.</p>
                        </div>
                        {directories.length > 1 && (
                            <select aria-label="Business directory" value={directory} onChange={(event) => changeDirectory(event.target.value as "suppliers" | "sellers")}>
                                {directories.map((item) => <option key={item} value={item}>{item === "suppliers" ? "Suppliers" : "Sellers"}</option>)}
                            </select>
                        )}
                    </div>
                    {error && <p className="auth-error" role="alert">{error}</p>}
                    <div className="inventory-toolbar">
                        <div className="search-input">
                            <input aria-label="Search business profiles" placeholder="Search name, business, username or location…" value={query} onChange={(event) => setQuery(event.target.value)} />
                        </div>
                        <button className="btn subtle" type="button" onClick={() => { setOffset(0); setQuery(""); }}>Refresh</button>
                    </div>
                    <div className="business-profile-list">
                        {visibleProfiles.map((profile) => (
                            <article className="business-profile-row" key={profile.username}>
                                <div className="profile-avatar">{profile.fullName.slice(0, 1).toUpperCase()}</div>
                                <div className="profile-row-main">
                                    <strong>{profile.businessName}</strong>
                                    <span>{profile.fullName} · @{profile.username}</span>
                                </div>
                                <div className="profile-row-contact">
                                    <span>{profile.contactNumber}</span>
                                    <span>{profile.location}</span>
                                </div>
                            </article>
                        ))}
                        {loading && <p className="empty-state" role="status">Loading directory…</p>}
                        {!loading && visibleProfiles.length === 0 && <p className="empty-state">{error ? "The directory could not be loaded." : "No saved business profiles match this search."}</p>}
                    </div>
                    <div className="profile-pagination">
                        <span>Page {Math.floor(offset / 10) + 1}</span>
                        <div>
                            <button className="btn subtle" type="button" disabled={loading || offset === 0} onClick={() => setOffset((current) => Math.max(0, current - 10))}>Previous</button>
                            <button className="btn subtle" type="button" disabled={loading || nextOffset < 0} onClick={() => setOffset(nextOffset)}>Next</button>
                        </div>
                    </div>
                </section>
            ) : (
                <section className="planned-card">
                    <h2>No business directory for this role</h2>
                    <p>Your account can use its own workspace features, but cannot view supplier or seller directories.</p>
                </section>
            )}
        </div>
    );
}
