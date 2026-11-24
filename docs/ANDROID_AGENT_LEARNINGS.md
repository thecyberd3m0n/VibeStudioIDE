# Android agent: learnings (LinkedIn task, floating window)

Cost/speed findings for future agents using the android_* tools.

- `android_dump_ui_tree` on rich apps is ~30 KB and gets truncated; Compose screens often show only skeleton views while loading. A screenshot (<=64 KB) is cheaper and shows the whole screen. Use the tree for small screens or exact node ids.
- Reading results = screenshot. Navigating known UI (bottom tabs, recent searches) = tap_node or a single tap; do not retype queries.
- Floating window: screenshot is window-only, `android_tap` is absolute screen pixels. Add the window origin (root bounds in the tree). Forgetting this was the main wasted step.
- Screenshots never contain the HUD frame/bubble, so the agent cannot verify them; ask the user.
- `launch_app` with `quarter` needs the hidden `ActivityOptions.setLaunchWindowingMode(5)`. Plain reflection is blocked; meta-reflection (`Class.getDeclaredMethod` via reflection) works without Shizuku. Errors are now returned instead of swallowed. `ok:true` is not proof the window is floating.
- An already running full-screen app is not resized by a new `launch_app`; the user must close it first. Without Shizuku the agent cannot force-stop it.
- Do not retry failing calls blindly (force_stop, install timeouts). `android_install_apk` waits for the user's confirmation; time-outs usually mean a pending system dialog.
- After editing the Automator, reinstall via `android_install_apk` with an ABSOLUTE path (`~` is not expanded) and ask the user to confirm.
- HUD frame: margins were doubled (2dp in setTarget + 1.5*stroke in onDraw), making the frame smaller than the window; now it matches the window bounds.
