# Theo screen-aware automation

Theo uses Android's native Accessibility Service API to inspect the active app's accessibility tree and perform a small, validated set of semantic interactions. This version does not use fixed screen coordinates, per-app button sequences, a bundled model, or a second AI provider.

## Enable it

1. Open Theo and go to **Settings**.
2. Under **Screen-aware Automation**, tap **Enable**.
3. Android opens Accessibility settings. You must enable **Theo Screen Automation** manually.
4. Return to Theo and repeat your request. Android controls this permission; Theo cannot grant it silently.

## Runtime behavior

- Theo resolves a named app against launchable apps installed on the device. A unique exact match can open directly. Unclear/partial matches show an app chooser. No match is reported instead of silently launching a different app.
- When you request a screen task, Theo captures a fresh accessibility-tree snapshot and asks the AI provider configured in Theo Settings (Gemini or your configured local Qwen endpoint) for one structured step at a time.
- The executor accepts only validated actions: click an accessible node, type into an editable node, scroll a scrollable node, go back, wait, finish, or ask you to clarify. The tree is read again after each step; node IDs are paths into the current tree, not fixed screen coordinates.
- Each task is limited to 12 planned steps. Two consecutive failed accessibility actions stop the run. Unsupported or malformed model actions stop instead of being guessed.
- Sensitive requests such as sending, deleting, purchasing, sharing, or submitting require a confirmation. If a task that looked safe reaches a potentially sensitive control, Theo pauses before activating it.
- For actions such as launching an app or clicking a control, Android/API dispatch is not proof that the requested task finished. Theo reports that limitation. A model judging a task complete is not independent verification, and media playback cannot be independently confirmed in this version.
- Theo does not record accessibility events, store screen text, capture screenshots, or include the current value of editable fields/passwords in the model prompt. During an explicitly requested task, visible non-editable text and accessible labels are sent to the AI provider selected in Theo Settings to plan an action. Review provider configuration before using it on confidential screens.
- Some apps expose incomplete or no accessibility data. Theo reports that it cannot continue safely; screenshot/visual fallback is not included in this version.

## Reference project and license

The accessibility-service lifecycle and step-by-step action-loop architecture were informed by [MobileAgent-Android](https://github.com/GiggleWang/MobileAgent-Android), licensed under MIT. The service and task flow have been adapted to Theo's existing Kotlin/Compose app and configured AI clients. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
