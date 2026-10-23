# Theo learning checkpoint — 2026-10-23

This is a deliberate pause in feature-building. The goal now is to understand the app well enough to explain, debug, and eventually change it without relying on AI-generated code blindly.

## Snapshot

- Working branch: `feature/liquid-glass-ui`
- Last inspected commit before this checkpoint: `08630866a88cdbd2a69b4e1c8324b1c9aa6c4d41`
- Latest verified GitHub Actions run at checkpoint time: run #500, **success**: https://github.com/Kentaakakashi/Theo-AI-mobile/actions/runs/39479416395
- Build success means the React UI and Android debug APK compiled in CI. It does **not** prove that the app launches correctly or that the blank-home-screen issue is fixed on a real phone.
- Keep working on this feature branch during learning. Do not merge to `main) until the user has tested and reviewed it.

## What Theo is

Theo is an Android assistant app. The Android/Kotlin layer owns device capabilities, AI orchestration, stored chat/memory/history, speech, and screen automation. The newer chat presentation is written in React + TypeScript + Tailwind and displayed inside Android's WebView.

The React layer is not a second AI model. It draws the interface, collects text/taps, and sends events through a bridge to Kotlin. Kotlin performs the actual work and sends updated state back to React.

## Mental model: a request round trip

```text
User types / taps
      |
      v
React UI (web-ui/src/App.tsx)
      | calls window.TheoBridge.sendMessage(...)
      v
Android WebView bridge (TheoWebChatScreen.kt)
      | invokes Kotlin callback
      v
TheoViewModel.kt
      | updates state, coordinates AI, database, speech or tools
      v
TheoAiEngine / configured provider / device-action services
      |
      v
TheoViewModel updates StateFlow + message database
      |
      v
TheoWebChatScreen serializes state to JSON
      | calls window.theoUpdate(...)
      v
React re-renders the current screen
```

This is the core architecture to learn first. Don't start by memorizing every file.

## Important folders and files

- `app/src/main/java/com/example/MainActivity.kt` — Android activity and top-level screen/navigation wiring. It chooses the visible screen and connects it to the ViewModel.
- `app/src/main/java/com/example/ui/screens/TheoWebChatScreen.kt` — hosts the React build in WebView; defines the JavaScript-to-Kotlin bridge and sends Kotlin UI state back to the web UI.
- `app/src/main/java/com/example/viewmodel/TheoViewModel.kt` — central state and behavior coordinator. It connects UI events to AI, persistence, speech, and Android services.
- `app/src/main/java/com/example/service/TheoAiEngine.kt` — AI-provider/orchestration layer (inspect this when learning how prompts reach the configured model).
- `app/src/main/java/com/example/data/` — database entities, DAO, and persistence.
- `app/src/main/java/com/example/service/` — device capabilities and supporting services, including screen automation and speech.
- `web-ui/src/App.tsx` — React screen structure, messages, state display, and bridge calls.
- `web-ui/src/components/ui/ai-prompt-input.tsx` — prompt composer; modes add instructional text before sending.
- `web-ui/src/components/ui/liquid-glass.tsx` — imported liquid-glass visual effect.
- `web-ui/src/components/ui/text-shimmer.tsx` — small animated text component adapted from 21st.dev Agent Elements.
- `web-ui/src/index.css` — app-wide web styles, glass surfaces, and animations.
- `web-ui/index.html` and `web-ui/src/main.tsx` — web entry point and React startup/error boundary.
- `.github/workflows/android.yml` — CI recipe that builds the React assets, copies them into Android assets, runs Gradle tests/build, and uploads the debug APK.

## What happens when you press Send

1. `AIPromptInput` reads the text and selected mode.
2. Ask sends the text as-is. Plan/Learn/Code prepend extra instructions; they do not switch to a different model.
3. `App.tsx` calls `window.TheoBridge.sendMessage(text)`.
4. `TheoWebChatScreen.kt` exposes that JavaScript call with `@JavascriptInterface`, then posts it to the Android main thread.
5. The callback from `MainActivity.kt` calls `viewModel.sendMessage(text)`.
6. The ViewModel manages request state and passes the request into Theo's AI layer; messages/state are then updated.
7. Kotlin builds a JSON snapshot of the UI state and invokes `window.theoUpdate(...)` in WebView.
8. React's `setNative` receives that snapshot and re-renders the UI.

The next learning session should trace these steps in the actual source code and verify details before making assumptions about streaming, provider selection, or tool execution.

## Current UI work in this checkpoint

- StarKnightt LiquidGlass component integrated into the React UI.
- 21st.dev-inspired prompt composer with Ask / Plan / Learn / Code modes.
- 21st.dev-inspired TextShimmer adapted to Theo's styling.
- Four starter prompts, state-aware status, thinking indicator, refined chat bubbles and motion.
- Startup diagnostics added because the React/WebView migration previously showed a blank home screen.

Important: these are UI changes. The prompt modes currently change the instruction text sent to the same send handler; they are not separate AI agents or guaranteed capabilities.

## Current risks / things not yet proven

- The blank-screen issue was not confirmed fixed on the user's phone. CI passing only proves compilation.
- The prompt composer sends a prefixed prompt for Plan/Learn/Code. Check how that interacts with any system prompt, token limits, and the ViewModel's state updates.
- LiquidGlass is a visual component; it must not block foreground controls or make the mid-range phone sluggish.
- WebView bridge methods are powerful boundaries. Calls that can control device behavior must remain validated and protected by the native safety policy.
- Never claim screen automation, voice, local Qwen, or offline support works merely because relevant source files or dependencies exist. Verify the exact provider/configuration and test on device.

## Suggested learning sequence

1. **Trace one button:** understand how Send travels from React through the bridge to the ViewModel.
2. **Understand state:** learn what `TheoUiState`, `StateFlow`, and `window.theoUpdate` do.
3. **Follow one AI request:** read `TheoViewModel.sendMessage` and the relevant path in `TheoAiEngine`; identify where provider selection, prompt construction, response parsing, and errors occur.
4. **Understand storage:** trace one chat message from creation to Room/DAO and back to the UI.
5. **Understand device actions:** follow one low-risk action through the action planner, safety policy, AccessibilityService, and verification.
6. **Only then resume feature work:** make one small change, predict its effects, inspect the diff, build, and test it.

## Learning rule

For each change, be able to answer:
- What problem does this solve?
- Which file owns the behavior?
- What calls this code?
- What state/data enters and leaves it?
- What could break?
- How will we test it?

The point is not to stop using AI. The point is to use it as a tutor and pair programmer rather than as an invisible author.
