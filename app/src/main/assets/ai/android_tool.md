# Skill: android

The `android` skill inspects, navigates and tests native Android apps on this device through the VibeStudioAutomator companion app (AccessibilityService, works fully offline; Shizuku is an optional extra). It is lazy: load it with the skill-schema tool before use. It is offered only while VibeStudioAutomator is installed. The first use in a chat session asks the user for permission.

## Key Capabilities:
- `android_status`: Driver mode (ACCESSIBILITY | SHIZUKU | UNAVAILABLE), capabilities, allowlist and run state. Call it first.
- `android_dump_ui_tree`: Compact UI tree of the foreground app; every node gets an integer `id` valid only for that dump.
- `android_tap_node`: Tap a node by `id` from the latest dump.
- `android_tap` / `android_swipe`: Gestures in native pixels. For `android_tap` with screenshot coordinates pass the screenshot's `scaleFactor`.
- `android_input_text`: Set text on a node (or the focused one if `id` is omitted).
- `android_global_action`: BACK, HOME, RECENTS or NOTIFICATIONS.
- `android_launch_app`: Launch an app by package name.
- `android_screenshot`: JSON with `base64`, `width`, `height`, `scaleFactor`; the image is at most 64 KB.
- `android_install_apk`: Installs an APK through the Automator's PackageInstaller (no Shizuku needed). The user may have to confirm the system install dialog; tap it via the UI tree. Pass a path readable by the Automator (e.g. shared storage), not the IDE's private home.
- `android_logcat`, `android_force_stop`: Shizuku only.
- `android_emergency_stop`: Stops the session immediately.

## Android Tool Rules & Best Practices
1. **Prefer the UI tree over screenshots**: Use `android_dump_ui_tree` + `android_tap_node` first. Take a screenshot only when the tree lacks the information (canvas, games, images).
2. **Ids are per dump**: Node ids change whenever the screen changes. Dump again after every action that changes the UI.
3. **Screenshot coordinates**: Real pixel = screenshot coordinate / `scaleFactor`.
4. **Scope**: Only packages on the user's allowlist can be tapped or typed into. If an action fails, check `android_status` for the allowlist instead of retrying blindly.
5. **Errors**: Error 10 = the user paused the agent; 15 = the user stopped it. Do not retry; tell the user and call `task_complete`.
6. **Respect the user**: The user can pause or stop you from the Automator bubble at any time. Never work around this.
