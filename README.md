# WealthWise – Phase 4

WealthWise is an academic software engineering project (University of Tehran, 2025, 6th semester) focused on modeling, design, and implementation of a financial portfolio management system. The Phase 4 deliverable extends earlier theoretical and design phases into a working Spring Boot–based system with message-driven features and capital raise handling.

---

## 📌 Project Overview

WealthWise simulates a **portfolio management platform** where users (portfolio managers) can track their securities, receive **capital raise announcements**, and manage **stock rights** (buying, selling, or converting them).

This phase emphasizes **implementation** over theory, translating domain models into actual running code. The backbone of the system is available on GitHub:

🔗 [WealthWise Repository](https://github.com/matahho/WealthWise)

---

## ✨ Key Features

- **Portfolio Valuation Service**
  - Compute the value of each portfolio on a given date.
  - Output sorted by stock name, including volume and daily market value.

- **Capital Raise Announcement Handling**
  - Publish announcements via a **Message Broker (Artemis)**.
  - Implement a **Listener** to consume these announcements and update portfolios.
  - Automatically allocate stock rights to all shareholders.

- **Stock Rights Management**
  - Allocate proportional rights based on shareholding.
  - Users can:
    1. **Sell stock rights**
    2. **Buy additional rights**
    3. **Convert rights into main shares** (with payment).

- **Event Sourcing Architecture**
  - Portfolio updates are tracked via `SecurityChange` events.
  - Clear history of rights allocation, conversion, and trading.

- **Testing Coverage**
  - Unit tests for portfolio valuation.
  - Tests for capital raise actions, right usage, buying/selling flows.

---

## 🏗️ Tech Stack

- Java 21
- Spring Boot
- Artemis Message Broker
- Maven
- JUnit

---

## 📂 Deliverables

- Portfolio valuation service (with tests)
- Capital raise announcement publisher & listener
- Stock rights allocation and usage actions
- Buy/sell rights actions with validation rules
- Event-sourced portfolio updates
- End-to-end tests

## References
- Event Sourcing – Martin Fowler: https://martinfowler.com/eaaDev/EventSourcing.html
- Design Patterns – Refactoring Guru: https://refactoring.guru/design-patterns/catalog

## Project designers
- Mahdi Haji Hosseiny 
- Alireza Hosseini 
