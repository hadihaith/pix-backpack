
# Unofficial Pix Studio

An independent Android application designed to connect to and manage the original **PIX Backpack** by Divoom.

Unofficial Pix Studio aims to provide an alternative way to interact with the PIX Backpack through Bluetooth Low Energy (BLE), with a focus on device communication, pixel art, and animation customization.

## ✨ Planned Features

- 📡 **Bluetooth LE Discovery** — Scan for nearby PIX Backpack devices.
- 🔍 **Device Identification** — Identify compatible devices using known BLE service UUIDs and device-name patterns.
- 🔗 **Device Connectivity** — Establish Bluetooth connections with the backpack.
- 🛠️ **GATT Inspection** — Discover and inspect Bluetooth services and characteristics.
- 🎨 **Pixel Art Editor** — Create and customize pixel art.
- 🎞️ **Animation Editor** — Design and manage custom pixel animations.
- 📤 **Content Transfer** — Send images and animations to compatible devices.
- 🔧 **Protocol Implementation** — Implement the PIX Backpack communication protocol with firmware compatibility in mind.

## 🧰 Technology Stack

- **Kotlin** — Primary programming language.
- **Jetpack Compose** — Modern declarative Android UI.
- **Material 3** — UI components and styling.
- **Android Bluetooth LE API** — Wireless device discovery and communication.
- **Gradle Kotlin DSL** — Build configuration.

## 📱 Compatibility

The initial development target is the original PIX Backpack.

| Firmware | Status |
|---|---|
| 2.4.0 | Hardware identification and BLE UUIDs verified |
| 2.5.1 | Community protocol reference available; testing pending |
| Other 2.x versions | Compatibility testing pending |

Compatibility with other PIX Backpack models and firmware versions has not yet been established.

## ⚠️ Disclaimer

Unofficial Pix Studio is an independent, community-oriented project. It is not affiliated with, endorsed by, or sponsored by Divoom.

All product names and trademarks belong to their respective owners.

## 🚧 Development Status

This project is actively under development. Bluetooth discovery and identification have been implemented, and GATT connectivity and protocol investigation are ongoing.

Features listed as planned are not necessarily available in the current release.

