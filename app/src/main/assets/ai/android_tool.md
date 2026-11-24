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
- `android_install_apk`: Installs an APK through the Automator's PackageInstaller (no Shizuku needed). The user may have to confirm the system install dialog; tap it via the UI tree. Pass the absolute path of the APK inside the IDE home (e.g. ~/Project/bin/App.apk); the IDE streams it to the Automator via ParcelFileDescriptor. NEVER copy the APK to /sdcard or shared storage first: it is not available and will not work.
- `android_logcat`, `android_force_stop`: Shizuku only.
- `android_emergency_stop`: Stops the session immediately.

## Android Tool Rules & Best Practices
1. **Pick the cheapest observation that is sufficient**: a screenshot is not always needed. A UI tree (`android_dump_ui_tree`) is often cheaper in tokens than an image and gives exact text, ids and bounds, so prefer it when the screen is small or you only need to find a control, read short text or fill a form. Prefer a screenshot when the tree is huge or truncated (rich apps such as LinkedIn, feeds, Compose: 25 KB+) or when layout, images or visual state matter. Never observe the same unchanged screen twice. For critical navigation, where a wrong tap can break or delete something (send, submit, delete, pay, confirm, irreversible settings), validate with a screenshot before and after the action, in addition to the tree. If an action did not work or the result is unclear, verify with a screenshot before retrying.

2. **Ids are per dump**: Node ids change whenever the screen changes. Dump again after every action that changes the UI.
3. **Coordinates are window-relative**: screenshot pixels, `bounds` in the UI tree and `android_tap` / `android_swipe` all use the same coordinates, relative to the app window (also for a floating `quarter` window). Real pixel = screenshot coordinate / `scaleFactor`. Tap the center of a node's `bounds` or a point from the screenshot directly; never add a window offset yourself.
3a. **Fresh screens load**: right after launch or a tab switch the tree/screenshot may show a skeleton or spinner. Take one more screenshot before concluding anything.
4. **Scope**: Only packages on the user's allowlist can be tapped or typed into. If an action fails, check `android_status` for the allowlist instead of retrying blindly.
5. **Errors**: Error 10 = the user paused the agent; 15 = the user stopped it. Do not retry; tell the user and call `task_complete`.
6. **Respect the user**: The user can pause or stop you from the Automator bubble at any time. Never work around this.
7. **Permission / Settings screens**: If a tool error starts with `PERMISSION_REQUIRED` (or the Automator opened a system Settings screen), stop working immediately: do not tap or type in Settings or other system screens, press BACK once to leave them, then call `task_complete` asking the user to grant the permission manually and re-run the task.
8. **Session**: The Automator frame and bubble stay visible for the whole task and are removed by the IDE when you call `task_complete`. Always finish with `task_complete`.
9. **Clean up apps you opened**: Unless the user said otherwise, before `task_complete` close every app you launched yourself with `android_force_stop`, and bring the user back to VibeStudio (`android_launch_app` with the IDE package, or BACK/HOME). Never close apps the user had open before you started. Without Shizuku `android_force_stop` fails (missing KILL_BACKGROUND_PROCESSES): do not retry, tell the user the app is still open.
10. **Floating window (`quarter`)**: works without Shizuku. Launching an app that is already running just brings its existing window back to the front (no second instance). The app's window belongs to this chat session: launch it once, then keep working in that window; do not call `android_launch_app` again unless it was closed. The user may interact with it too (open a DM, navigate, type): re-take a screenshot before acting and continue from the state you find, never reset it. The first freeform launch may show a system tutorial overlay (e.g. Motorola) that the user must dismiss. `ok:true` only means the activity started; verify with one screenshot.
11. **Shortcuts**: prefer existing UI shortcuts (bottom tabs, recent searches, saved filters) over typing. Keep tool calls sequential when each depends on the previous screen; batch only independent reads.