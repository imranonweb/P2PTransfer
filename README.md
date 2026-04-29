# 🚀 P2PTransfer - Peer-to-Peer File Sharing & Chat System

## 📌 Overview

**P2PTransfer** is a Java-based peer-to-peer (P2P) desktop application that enables users to:

* Discover peers on the same network
* Connect securely using session codes
* Transfer files directly between devices
* Chat in real-time (mini WhatsApp-style system)

The application is built using **Java (Swing GUI)** and follows a decentralized architecture — meaning no central server is required.

---

## ✨ Features

### 🔍 Peer Discovery

* Automatically finds devices in the same local network
* Uses a discovery service to detect available peers

### 🔗 Session-Based Connection

* Connect using a unique session code
* Ensures controlled and secure communication

### 📁 File Transfer

* Send and receive files seamlessly
* Supports large file handling
* Queue system for multiple file transfers

### 💬 Real-time Chat

* Built-in messaging system
* Chat bubbles UI (WhatsApp-style)
* Bi-directional communication

### 🖥️ GUI Interface

* Built using Java Swing
* Clean and interactive UI
* Drag & drop file support

### 🔄 Bidirectional Communication

* Once connected, both peers can:

  * Send files
  * Receive files
  * Chat simultaneously

### 🔌 Connection Management

* Connect / Disconnect functionality
* Handles peer connection lifecycle

---

## 🏗️ Project Structure

```
P2PTransfer/
│── src/
│   ├── Main.java
│   ├── AppFrame.java           # Main GUI
│   ├── PeerServer.java         # Server for incoming connections
│   ├── PeerClient.java         # Client for outgoing connections
│   ├── PeerConnection.java     # Core communication logic
│   ├── DiscoveryService.java   # Peer discovery system
│   ├── DiscoveredPeer.java     # Peer model
│   ├── Protocol.java           # Communication protocol
│   ├── SessionCode.java        # Session management
│   ├── LocalNet.java           # Network utilities
│   ├── SwingSync.java          # Thread-safe UI updates
│
│── bin/ / out/                 # Compiled files
│── .idea/                      # IDE config
│── .gitignore
```

---

## ⚙️ Requirements

* Java **JDK 8 or higher**
* OS: Windows / Linux / macOS
* Same network connection (for peer discovery)

---

## ▶️ How to Run

### Step 1: Clone the Repository

```bash
git clone https://github.com/your-username/P2PTransfer.git
cd P2PTransfer
```

### Step 2: Compile the Project

```bash
javac -d bin src/*.java
```

### Step 3: Run the Application

```bash
java -cp bin Main
```

---

## 📸 Demo (Add Your Screenshots Here)

### 🖥️ Home

![Main UI](./assets/main-ui.png.jpeg)

### 🔍 UI

![Peer Discovery](./assets/send.jpeg)


### 📁 File Transfer

![File Transfer](./assets/ransfer.jpeg)


---

## 🔄 Application Workflow

1. Launch the app on two devices
2. Devices discover each other automatically
3. Select a peer or enter session code
4. Establish connection
5. Start:

   * Sending messages 💬
   * Transferring files 📁

---

## 🔐 Security Concept

* Session-based connection prevents unauthorized access
* Direct peer-to-peer communication (no middle server)
* Structured protocol ensures safe data exchange

---

## 🚀 Future Improvements

* 🔒 AES Encryption for secure file transfer
* ⏸ Resume interrupted transfers
* 🌐 Cross-network (internet-based) connection
* 📊 Transfer progress bar & speed tracking
* 📱 Mobile version (Android)

---


### ✅ MIT License (Recommended)

* Free to use, modify, distribute
* Only requires credit

---

## 🙌 Contribution

Contributions are welcome!

Steps:

1. Fork the repository
2. Create a new branch
3. Make changes
4. Submit a Pull Request

---

## 👨‍💻 Author

**Md. Al Imran Emon**

---

## ⭐ Final Note

This project demonstrates:

* Networking fundamentals
* Socket programming
* GUI design (Swing)
* Real-time communication systems

  
** computer networking project
Perfect for learning **system design basics + networking + Java desktop development**.

---

💡 *If you like this project, consider giving it a star ⭐*
