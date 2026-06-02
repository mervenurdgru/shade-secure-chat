# 🔐 Shade — Secure Chat

End-to-end encrypted messaging application built as a graduation project.  
Three-platform system: **Android** · **Web** · **Backend**

> The server never sees plaintext. Ever.

---

## Architecture

```
┌─────────────────┐        ┌──────────────────┐        ┌─────────────────┐
│  Android (Tel1) │◄──────►│   Go Backend     │◄──────►│  Android (Tel2) │
│  Shade App      │  WSS   │   :8080          │  WSS   │  Shade App      │
└─────────────────┘        └──────────────────┘        └─────────────────┘
                                    ▲
                           ┌────────┴────────┐
                           │   Web Browser   │
                           │   Shade Web     │
                           └─────────────────┘
```

| Component | Tech Stack |
|-----------|------------|
| Android   | Kotlin, Jetpack Compose, Room, Hilt, OkHttp |
| Backend   | Go, Fiber, WebSocket, PostgreSQL |
| Web       | React, TypeScript, Vite, TailwindCSS, Zustand |

---

## Key Features

- **End-to-end encryption** — X25519 key exchange + ChaCha20-Poly1305 encryption
- **Shade ID** — unique identifier instead of phone number or email (e.g. `CG-8FA4-B942`)
- **Web pairing via QR** — securely transfer message history from Android to browser
- **Forward secrecy** — ephemeral keys per ECDH session
- **Real-time messaging** — WebSocket-based, with exponential backoff reconnection
- **Dark mode** — system theme aware (Android & Web)
- **Multi-language** — Turkish & English

---

## Encryption Architecture

### Message Encryption
```
Sender:    ciphertext = ChaCha20-Poly1305.encrypt(
               key = ECDH(senderPriv, receiverPub),
               plaintext = message
           )

Receiver:  plaintext  = ChaCha20-Poly1305.decrypt(
               key = ECDH(receiverPriv, senderPub),
               ciphertext
           )
```

### Web Pairing (QR-based)
```
1. Web generates ephemeral X25519 key pair
2. QR encodes: shade://web-auth?s={sessionId}&k={webPubKeyHex}
3. Android scans QR, derives shared secret via ECDH
4. Transfer key = HKDF-SHA256(sharedSecret, ...)
5. Android encrypts credentials with ChaCha20-Poly1305
6. Web decrypts → session established
```

---

## Project Structure

```
shade-secure-chat/
├── core-android-main/   # Android app (Kotlin/Compose)
├── core-backend/        # Go REST + WebSocket server
└── core-web/            # React web client
```

---

## Developed by

**Merve Nur Erdem** & **[Enes Canbay](https://www.linkedin.com/in/enescnby/)**  
Computer Engineering — Graduation Project, 2026
