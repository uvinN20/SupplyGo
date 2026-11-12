import { FormEvent, useEffect, useRef, useState } from "react";
import {
    joinChat,
    leaveChat,
    listChatMessages,
    sendChatMessage,
    type ChatMessage,
} from "../lib/api";

export default function Chat() {
    const [joined, setJoined] = useState(false);
    const [joining, setJoining] = useState(false);
    const [sending, setSending] = useState(false);
    const [username, setUsername] = useState("");
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [draft, setDraft] = useState("");
    const [status, setStatus] = useState("");
    const [error, setError] = useState("");
    const cursor = useRef(0);
    const endOfMessages = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!joined) return;
        let stopped = false;
        let timer: number | undefined;

        const poll = async () => {
            try {
                const batch = await listChatMessages(cursor.current);
                if (stopped) return;
                if (batch.truncated) {
                    setStatus("Some older live messages have already left the temporary room buffer.");
                }
                if (batch.messages.length > 0) {
                    setMessages((current) => {
                        const seen = new Set(current.map((message) => message.id));
                        return [...current, ...batch.messages.filter((message) => !seen.has(message.id))].slice(-200);
                    });
                }
                cursor.current = batch.cursor;
                setError("");
            } catch (cause) {
                if (stopped) return;
                setError(cause instanceof Error ? cause.message : "The chat connection was lost. Join the room again.");
                setJoined(false);
            } finally {
                if (!stopped) timer = window.setTimeout(poll, 1500);
            }
        };

        void poll();
        return () => {
            stopped = true;
            if (timer !== undefined) window.clearTimeout(timer);
        };
    }, [joined]);

    useEffect(() => {
        endOfMessages.current?.scrollIntoView({ behavior: "smooth", block: "end" });
    }, [messages]);

    async function handleJoin() {
        setJoining(true);
        setError("");
        setStatus("");
        try {
            const result = await joinChat();
            cursor.current = 0;
            setMessages([]);
            setUsername(result.username);
            setStatus("You joined the shared room.");
            setJoined(true);
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not join the chat room.");
        } finally {
            setJoining(false);
        }
    }

    async function handleLeave() {
        setJoining(true);
        try {
            await leaveChat();
            setJoined(false);
            setMessages([]);
            cursor.current = 0;
            setStatus("You left the shared room.");
            setError("");
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "Could not leave the chat room.");
        } finally {
            setJoining(false);
        }
    }

    async function handleSend(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const text = draft.trim();
        if (!text || !joined) return;
        setSending(true);
        setError("");
        try {
            const receipt = await sendChatMessage(text);
            setDraft("");
            setStatus(receipt);
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "The server did not confirm this message.");
        } finally {
            setSending(false);
        }
    }

    return (
        <section className="chat-page">
            <div className="page-heading">
                <span className="eyebrow">A SHARED SPACE FOR YOUR NETWORK</span>
                <h1>Chat</h1>
                <p>Talk with other people who have joined the SupplyGo room.</p>
            </div>

            <div className="chat-layout">
                <section className="chat-panel" aria-label="SupplyGo shared chat">
                    <header className="chat-panel-heading">
                        <div className="chat-room-mark" aria-hidden="true">SG</div>
                        <div className="chat-room-title">
                            <h2>SupplyGo shared room</h2>
                            <p>{joined ? "Joined · live messages" : "Join to read and send messages"}</p>
                        </div>
                        {joined ? (
                            <button className="btn secondary chat-leave" type="button" onClick={handleLeave} disabled={joining}>
                                Leave room
                            </button>
                        ) : null}
                    </header>

                    <div className="chat-message-list" role="log" aria-label="Chat messages" aria-live="polite">
                        {!joined ? (
                            <div className="chat-empty">
                                <span className="chat-empty-icon" aria-hidden="true">•••</span>
                                <h3>Join the conversation</h3>
                                <p>Messages appear here while you are connected to the shared room.</p>
                            </div>
                        ) : messages.length === 0 ? (
                            <div className="chat-empty">
                                <span className="chat-empty-icon" aria-hidden="true">•••</span>
                                <h3>No messages yet</h3>
                                <p>Send a short message to start the conversation.</p>
                            </div>
                        ) : messages.map((message) => {
                            const own = message.sender === username;
                            return (
                                <article key={message.id} className={`chat-message ${own ? "chat-message-own" : ""}`}>
                                    <div className="chat-message-meta">
                                        <strong>{message.sender}</strong>
                                        <span>{message.role.replaceAll("_", " ")}</span>
                                        <time dateTime={message.time}>
                                            {new Date(message.time).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                                        </time>
                                    </div>
                                    <p>{message.text}</p>
                                </article>
                            );
                        })}
                        <div ref={endOfMessages} />
                    </div>

                    {status ? <p className="chat-status" role="status">{status}</p> : null}
                    {error ? <p className="chat-error" role="alert">{error}</p> : null}

                    {joined ? (
                        <form className="chat-composer" onSubmit={handleSend}>
                            <label className="sr-only" htmlFor="chat-message">Message</label>
                            <input
                                id="chat-message"
                                value={draft}
                                onChange={(event) => setDraft(event.target.value)}
                                maxLength={240}
                                placeholder="Write a message…"
                                autoComplete="off"
                                disabled={sending}
                            />
                            <button className="btn primary" type="submit" disabled={sending || !draft.trim()}>
                                {sending ? "Sending…" : "Send"}
                            </button>
                        </form>
                    ) : (
                        <div className="chat-join-row">
                            <button className="btn primary" type="button" onClick={handleJoin} disabled={joining}>
                                {joining ? "Joining…" : "Join room"}
                            </button>
                        </div>
                    )}
                </section>

                <aside className="chat-info-card">
                    <span className="chat-info-icon" aria-hidden="true">i</span>
                    <h3>About this room</h3>
                    <p>Everyone who joins can read messages. This is a shared room, not a private chat.</p>
                    <ul>
                        <li>Messages are limited to 240 characters.</li>
                        <li>Messages are temporary and are not restored after leaving.</li>
                        <li>UDP delivery is best-effort; a server acceptance is not proof that everyone received it.</li>
                    </ul>
                    <p className="chat-privacy-note">Messages are not encrypted. Do not share passwords or sensitive business information.</p>
                </aside>
            </div>
        </section>
    );
}
