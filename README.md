# 🌿 EcoSystem

**EcoSystem** is a comprehensive, server-authoritative economy mod for Minecraft **NeoForge 1.21.1**. It provides a robust financial framework featuring multi-currency support, dynamic taxation, audit logging, and a premium GUI-based shop system inspired by GUIShop.

---

## ✨ Features

### 💰 Core Economy
- **Multi-Currency Support**: Register multiple currencies with custom Display Names, symbols, and precision.
- **Ledger Service**: Reliable server-side management of player balances.
- **Financial Transactions**: Support for payments (`/pay`), deposits, and withdrawals.
- **Audit Logging**: Every transaction is logged with timestamps, tax details, and participant information for administrative transparency.

### 🏛️ Advanced Taxation
- **Tax Profiles**: Configure custom tax rates for different types of transactions.
- **Automatic Distribution**: Taxes can be redirected to specific "treasury" accounts (e.g., server funds).
- **Tax Previews**: Players can see the tax breakdown before finalizing transactions.

### 🛒 Premium GUI Shop System
- **YAML & JSON Catalogs**: Human-readable configuration for shop items.
- **Slot-Indexed Layouts**: Complete control over GUI design with 9x6 grid pages.
- **Interactive Elements**:
    - **SHOP**: Classic buy/sell entries with quantity support.
    - **DECORATIVE**: Visual-only items for background design.
    - **NAVIGATION**: Buttons to switch pages, shops, or close the interface.
- **Real-time Feedback**: Dynamic tooltips showing prices, lore, and affordability checks.
- **Left-Click to Buy | Right-Click to Sell**.

---

## 🚀 Getting Started

### Prerequisites
- Minecraft **1.21.1**
- **NeoForge** (Latest 1.21.1 version)

### Installation
1. Download the latest `.jar` from the releases page (planned).
2. Drop it into your Minecraft `mods` folder.
3. Launch the game.

---

## 🛠️ Developer & Build Guide

The project uses **Gradle** as its build system.

### Build Instructions
To build the project from source, clone the repository and run:
```bash
./gradlew build
```
The compiled jar will be located in `build/libs/`.

### Run Test Client
```bash
./gradlew runClient
```

---

## ⚙️ Configuration

### Customizing Shops
Shop files are located in `world/serverconfig/ecosystem/shops/`. You can use YAML (`.yml`) for easy editing:

```yaml
shopId: "main_market"
title: "Global Market"
currency: "money"
rows: 6
pages:
  Page0:
    10: # Slot 10
      itemId: "minecraft:diamond"
      type: "SHOP"
      buyPrice: 100
      sellPrice: 50
      quantity: 1
    49: # Slot 49 (Navigation)
      itemId: "minecraft:barrier"
      type: "NAVIGATION"
      navAction: "BACK"
      displayName: "§cClose Menu"
```

---

## ⌨️ Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/eco balance` | User | View your balances across all currencies. |
| `/eco pay <player> <currency> <amount>` | User | Send money to another player. |
| `/eco admin reload` | Admin | Reload all configs and shop catalogs with validation feedback. |
| `/eco admin give/take/set` | Admin | Manage player funds manually. |
| `/eco audit last <count>` | Admin | View recent transaction history. |

---

## 🌎 Localization
Supported languages:
- 🇺🇸 **English (en_us)**
- 🇧🇷 **Portuguese (pt_br)**

---

## 📄 License
Distributed under the **MIT License**. See `LICENSE` for more information.

---

*Developed with ❤️ by Pedro Dalben*
