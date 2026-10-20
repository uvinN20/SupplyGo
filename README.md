# 🚀 SupplyGo

### Connecting Supply. Simplifying Delivery.

**A Real-Time Distributed Supply Chain Management & Delivery Optimization System**

![Java](https://img.shields.io/badge/Java-21-orange)
![Networking](https://img.shields.io/badge/Networking-TCP%2FIP-blue)
![Multithreading](https://img.shields.io/badge/Concurrency-Multithreaded-green)
![Status](https://img.shields.io/badge/Status-In%20Development-yellow)

---

## 🌟 About SupplyGo

**SupplyGo** is a Java-based distributed supply chain management platform designed to connect suppliers, sellers, and managers through a real-time network.

Our mission is simple:

**Connect smarter. Manage faster. Deliver better.**

SupplyGo combines Java socket programming, concurrent processing, inventory synchronization, and intelligent delivery optimization to create an efficient supply chain management experience.

This project is being developed for **EC5205 – Operating System and Network Programming** at the University of Ruhuna.

## ✨ Planned Features

- 📦 **Supplier Management** — Manage products, availability, and incoming orders.
- 🛒 **Seller Management** — Browse products, place orders, and track deliveries.
- 🌐 **Real-Time Networking** — TCP socket communication between connected clients.
- ⚡ **Concurrent Processing** — Handle multiple users using Java multithreading.
- 🔐 **Inventory Synchronization** — Prevent stock conflicts during simultaneous orders.
- 🔔 **Live Notifications** — Receive instant order and delivery updates.
- 🚚 **Delivery Optimization** — Find efficient routes using Dijkstra's algorithm.
- 📊 **Management Dashboard** — Monitor supply chain activities.

## 🏗️ Proposed System Architecture

```text
       SUPPLYGO NETWORK

  Supplier  Seller  Manager
     |        |        |
     +--------+--------+
              |
        TCP/IP Sockets
              |
      Java Central Server
              |
     Multithreaded Engine
              |
     +--------+--------+
     |        |        |
   Orders  Inventory  Routes
              |
        Data Storage
```

## 🛠️ Technology Stack

| Component | Technology |
|---|---|
| Language | Java |
| Networking | TCP/IP, Java Sockets |
| Concurrency | Java Threads, ExecutorService |
| Synchronization | Java Concurrency Utilities |
| Interface | Java Swing |
| Database | SQLite (planned) |
| Algorithms | Dijkstra, Priority Queues |
| Development | Visual Studio Code |
| Version Control | Git & GitHub |

## 📂 Proposed Project Structure

```text
SupplyGo/
├── src/
│   ├── server/
│   ├── client/
│   ├── model/
│   ├── service/
│   ├── network/
│   └── gui/
├── docs/
├── tests/
└── README.md
```

*The structure will evolve during implementation.*

## 🗺️ Development Roadmap

- [ ] Phase 1 — Project setup
- [ ] Phase 2 — TCP server and client communication
- [ ] Phase 3 — Multithreaded client handling
- [ ] Phase 4 — Supplier and seller management
- [ ] Phase 5 — Real-time order processing
- [ ] Phase 6 — Inventory synchronization
- [ ] Phase 7 — Delivery route optimization
- [ ] Phase 8 — GUI development and integration
- [ ] Phase 9 — Testing and documentation

## 👨‍💻 Development Team

Developed as a collaborative university software engineering project.

**Team collaborator:** [@gsubodha2023-hue](https://github.com/gsubodha2023-hue)

## 🎓 Academic Context

**Module:** EC5205 — Operating System and Network Programming

**Institution:** University of Ruhuna

**Core Learning Areas:**
- Operating systems
- Client-server communication
- Socket programming
- Concurrent programming
- Thread synchronization
- Network application development

## 📌 Project Status

🚧 **Currently in the planning and development stage.**

Source code, setup instructions, screenshots, and demonstrations will be added as implementation progresses.

---

### 💙 SupplyGo

**Smarter Connections. Stronger Supply Chains.**

*Built with Java. Powered by Networking.*
