<div align="center">

# 🚀 SupplyGo

### Connect Smarter. Deliver Faster.

**A Real-Time Networked Supply Chain Management & Delivery Optimization System**

[![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
![TCP/IP](https://img.shields.io/badge/Networking-TCP%2FIP-2563eb?style=for-the-badge)
![Concurrency](https://img.shields.io/badge/Concurrency-Multithreaded-16a34a?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-In%20Development-f59e0b?style=for-the-badge)

**Connecting suppliers, sellers, and managers through smarter networking.**

[Overview](#-overview) · [Features](#-planned-features) · [Architecture](#-system-architecture) · [Roadmap](#-development-roadmap) · [Team](#-meet-the-team)

</div>

---

## 🌍 Overview

**SupplyGo** is a Java-based academic project focused on building a **real-time, multi-client supply chain platform**. It aims to connect suppliers, sellers, and managers through a centralized server, enabling order coordination, shared inventory updates, and delivery planning.

Rather than a traditional standalone inventory application, SupplyGo is designed to demonstrate **socket programming, concurrent client handling, and thread-safe shared data**.

> **Our vision:** Every supply connection should be simple, responsive, and reliable.

### 🎯 Project Objectives

- Establish reliable communication between multiple Java clients and a central TCP server.
- Process simultaneous client requests using Java threads or an `ExecutorService`.
- Protect shared inventory and order data against concurrent updates.
- Deliver real-time order-status notifications to connected clients.
- Explore delivery route optimization using **Dijkstra's shortest-path algorithm**.

## ✨ Planned Features

| Module | Planned capabilities |
| :--- | :--- |
| 📦 **Supplier Hub** | Manage products, update stock, receive and process orders |
| 🛒 **Seller Portal** | View stock, place orders, track order status |
| 📊 **Manager Console** | Monitor suppliers, orders, inventory, and system activity |
| 🌐 **Live TCP Networking** | Connect multiple Java clients through socket communication |
| ⚡ **Concurrent Server** | Handle simultaneous users with multithreading |
| 🔒 **Safe Inventory Updates** | Synchronize critical stock changes to avoid conflicts |
| 🔔 **Instant Updates** | Send order and delivery-status notifications |
| 🚚 **Smart Routing** | Use graph algorithms to explore efficient delivery routes |

## 🏗️ System Architecture

```text
  +----------------+    +----------------+    +----------------+
  | Supplier Client|    |  Seller Client |    | Manager Client |
  +--------+-------+    +--------+-------+    +--------+-------+
           |                     |                     |
           +---------------------+---------------------+
                                 |
                          TCP/IP Network
                                 |
                    +------------v------------+
                    |    Java Socket Server   |
                    |  ServerSocket + Threads |
                    +------------+------------+
                                 |
              +------------------+------------------+
              |                  |                  |
        Order Service     Inventory Service   Route Service
              |                  |                  |
              +------------------+------------------+
                                 |
                       Persistence (planned)
```

### 🔄 Example Order Journey

1. A **seller** submits a product order.
2. The **server** checks inventory and safely reserves available stock.
3. The relevant **supplier** receives an order notification.
4. The supplier updates the order's processing or delivery status.
5. The **seller and manager** receive the updated status.
6. The route service can calculate a suitable delivery path.

## 🧰 Technology Stack

| Area | Technology |
| :--- | :--- |
| Programming language | **Java 25** |
| Networking | TCP/IP, `Socket`, `ServerSocket` |
| Concurrency | Java Threads, `ExecutorService` |
| Synchronization | Java concurrency utilities |
| Desktop interface | Java Swing *(planned)* |
| Persistence | SQLite *(planned)* |
| Algorithms | Dijkstra's algorithm, priority queues |
| IDE | Visual Studio Code |
| Collaboration | Git & GitHub |

## 📁 Proposed Project Structure

```text
SupplyGo/
├── src/
│   ├── main/java/com/supplygo/
│   │   ├── server/       # Socket server and client handlers
│   │   ├── client/       # Supplier, seller and manager clients
│   │   ├── model/        # Product, order and user models
│   │   ├── network/      # Message protocol and communication
│   │   ├── service/      # Business logic and route optimization
│   │   └── gui/          # Swing screens (planned)
│   └── test/java/       # Tests
├── docs/                 # Design and project documentation
└── README.md
```

*This layout is a proposal and may change during development.*

## 🗺️ Development Roadmap

- [ ] **Phase 1:** Set up the Java project and agree on a message protocol
- [ ] **Phase 2:** Implement TCP server and basic client communication
- [ ] **Phase 3:** Support multiple clients concurrently
- [ ] **Phase 4:** Implement supplier, seller, and manager operations
- [ ] **Phase 5:** Add synchronized inventory and order processing
- [ ] **Phase 6:** Implement real-time notifications
- [ ] **Phase 7:** Integrate delivery route optimization
- [ ] **Phase 8:** Develop the GUI and persistent storage
- [ ] **Phase 9:** Test, document, and demonstrate the application

## 🧪 Testing Goals

The project will aim to demonstrate:

- Two or more clients connected to the same server
- Simultaneous order requests without inventory inconsistencies
- Order updates received by other connected clients
- Graceful handling of client disconnections
- Correct shortest-path results on sample delivery networks

## 👥 Meet the Team

| Contributor | GitHub |
| :--- | :--- |
| Project member | [@uvinN20](https://github.com/uvinN20) |
| Project member | [@gsubodha2023-hue](https://github.com/gsubodha2023-hue) |

## 🎓 Academic Context

**EC5205 — Operating System and Network Programming**  
**University of Ruhuna**

The project focuses on **client-server communication, socket programming, concurrent execution, synchronization, and network application development**.

## 📌 Project Status

> 🚧 **In development.** This README describes the intended architecture and planned functionality. Features should not be assumed to be implemented until corresponding source code and tests are added.

Setup commands, screenshots, and usage instructions will be added as development progresses.

---

<div align="center">

### 💙 SupplyGo
**Smarter Connections. Stronger Supply Chains.**

*Built with Java. Powered by Networking.*

</div>
