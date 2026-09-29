# VibeStudio IDE

**VibeStudio IDE** is a native Android application designed to provide a lightweight, self-contained AI-driven development environment directly on mobile devices.

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
