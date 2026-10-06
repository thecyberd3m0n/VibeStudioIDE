# VibeStudio IDE

## Demo Videos

- **Vibe 1 Demo:** [Watch / Download vibe1.mp4](docs/vibe1.mp4)
- **Vibe 2 Demo:** [Watch / Download vibe2.mp4](docs/vibe2.mp4)

**VibeStudio IDE** is a native Android application designed to provide a lightweight, self-contained AI-driven development environment directly on mobile devices.

---

## Product Thesis & Non-Negotiables

VibeStudio exists to **replace a PC IDE on your phone — without ever sending your work to a remote server.**

It should feel like *VS Code, but mobile*: you open it, and a full development
environment is simply there. No cloud build box, no account required to run code,
no hidden dependency that phones home. The device is the computer.

**Why on-device only:** remote-dev tools (Codespaces, Replit, Gitpod, …) are a
non-starter for the people who need a mobile IDE most — proprietary code that
can't leave the device, security/compliance rules that forbid cloud build, and
developers who simply won't pay a recurring bill for what their phone can already
run. Serving those users *is* the product; a server-side execution model would
break the one promise that makes VibeStudio worth using.

Non-negotiable principles (every architecture decision is checked against these):

- **No remote execution.** The user's code and builds run **only** on the device.
  Nothing compiles, runs, or is analyzed on our servers — ever.
- **No hidden costs or dependencies.** No mandatory cloud service, no per-use
  metering, no silent third-party backend required for core functionality.
- **Offline-capable by default.** Editing, building, and running work with the
  network off. Model providers are user-chosen and optional.
- **Servers may only ever touch entitlements and *user-initiated* sync** —
  never the user's source, and never the build. If a proposal quietly
  reintroduces a server for execution, it violates the thesis and is rejected.
- **It must feel like a local IDE.** The runtime (proot on F-Droid, an on-device
  VM/emulator on Play) is an implementation detail the user should never have to
  think about. Same environment, same packages, both channels.

---

## Features

- **Models Management**: Interface to configure, monitor, and switch between local or remote AI models.
- **MCP Integration**: Connect and manage Model Context Protocol (MCP) tool providers and services.
- **Built-in Terminal**: Direct terminal interface for executing shell commands and scripts.
- **Built-in Agentic Browser**: Test your work, automate your online actions thanks to WebView with dedicated MCP.
- **Interactive AI Chat**: Chat experience connected with your active AI models and tools.
- **Permissions Management**: Granular control over system permissions and access scopes.
- **Native Stepper Onboarding**: Guided setup walkthrough for first-time app configuration.

---

## Getting Started

### 1. Clone the Repository
VibeStudio uses git submodules for its core terminal functionality (`libtermux-android`). Clone recursively to ensure all components are downloaded:

```bash
git clone --recursive https://github.com/AeonCoreX-Lab/VibeStudio.git
cd VibeStudio
```

If you already cloned without submodules, run:
```bash
git submodule update --init --recursive
```

---

## Building & Development

### 1. In Termux (CLI Build)

#### Step 1: Install Dependencies (One-time setup)
Like `npm install`:
```bash
./install_dependencies_termux.sh
```

#### Step 2: Build the APK
Like `npm start` / `npm run build`:
```bash
./build.sh
```
The compiled, aligned, and signed APK will be created at `bin/VibeStudio.apk`.

---

### 2. In Android Studio (IDE)
1. Open Android Studio.
2. Select **Open** and navigate to the `VibeStudio` root directory.
3. Wait for Gradle sync to finish and click **Run** or **Build APK**.

---

## License

Work in Progress.
