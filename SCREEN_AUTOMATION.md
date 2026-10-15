# Theo screen-aware automation

Theo uses Android's native Accessibility Service API to inspect the active app's accessibility tree and perform a small, validated set of semantic interactions. On Android 11+, it can also capture a one-shot screenshot through AccessibilityService.takeScreenshot() for visual understanding. Theo does not use fixed screen coordinates or per-app button sequences.

## Enable it

1. Open Theo and go to **Settings**.
2. Under **Screen-aware Automation**, tap **Enable**.
3. Android opens Accessibility settings. You must enable **Theo Screen Automation** manually.
4. Return to Theo and repeat your request. Android controls this permission; Theo cannot grant it silently.

## Runtime behavior

- Theo resolves a named app against launchable apps installed on the device. A unique exact match can open directly. Unclear/partial matches show an app chooser. No match is reported instead of silently launching a different app.
- Ask “What is on my screen?”, “Look at my screen”, or “Describe the current screen” to trigger a native screen inspection rather than an ordinary chat guess. Theo uses the enabled Accessibility Service and does not need Termux or ADB for this capture path.
- When you request a screen task, Theo captures a fresh accessibility-tree snapshot. The selected planner (Gemini or your configured local Qwen endpoint) proposes one structured step at a time. Theo is accessibility-first: it skips the extra screenshot/vision network round-trip when the screen already exposes actionable controls, and tries transient screenshot vision only when no actionable controls are exposed.
- Screenshot bytes are held in memory only and cleared after analysis. Theo does not save screenshots. Visible content in the screenshot may include private information, so avoid running inspection on sensitive screens unless you intend to send that image to your configured cloud vision provider.
- The executor accepts only validated actions: click an accessible node, type into an editable node, scroll a scrollable node, go back, wait, finish, or ask you to clarify. Before a semantic click, Theo briefly outlines the target in cyan so you can see which control it is about to activate. The tree is read again after each step; node IDs are paths into the current tree, not fixed screen coordinates.
- Each task is limited to 12 planned steps. Two consecutive failed accessibility actions stop the run. Unsupported or malformed model actions stop instead of being guessed.
- Sensitive requests such as sending, deleting, purchasing, sharing, or submitting require a confirmation. If a task that looked safe reaches a potentially sensitive control, Theo pauses before activating it.
- Music requests open a provider search and then continue into the same native screen-action loop instead of stopping after the search page opens. For actions such as launching an app or clicking a control, Android dispatch is not proof that the requested task finished. A model judging a task complete is not independent verification; Theo must not claim playback succeeded unless the app exposes visible evidence.
- Theo does not record accessibility events or store screen text, and excludes current editable values/passwords from the accessibility tree. During an explicitly requested task or inspection, visible labels are sent to the configured planner; if cloud vision is configured, the current screenshot is sent to Groq or Gemini for that request only.
- If an app exposes no actionable accessibility elements, Theo may still provide a visual description from the screenshot, but it will not guess taps or operate controls without a matching accessible node.
- Android 10 and earlier do not support this native screenshot path. Secure/DRM-protected windows may be blacked out or block capture. The LOGS tab records the concrete Android screenshot failure reason where available.

## Reference project and license

The accessibility-service lifecycle and step-by-step action-loop architecture were informed by [MobileAgent-Android](https://github.com/GiggleWang/MobileAgent-Android), licensed under MIT. The service and task flow have been adapted to Theo's existing Kotlin/Compose app and configured AI clients. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
