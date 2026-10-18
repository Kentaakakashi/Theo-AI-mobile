# Theo

**Theo is a native Android AI assistant designed to help you understand your screen, interact with apps, and carry out multi-step tasks—with an atmospheric Liquid Glass interface and configurable AI engines.**

> **Branch:** `experiment/theo-liquid-glass`  
> **Status:** Experimental development branch. Features described as planned below are roadmap goals, not claims that they are already implemented.

Theo is being developed as a personal, phone-first assistant. The project combines a Kotlin/Jetpack Compose Android app with AI planning, local app data, and Android's Accessibility Service APIs. The goal is to make Theo useful beyond answering questions: it should be able to inspect context, plan actions, execute them carefully, and verify what actually happened.

## Contents

- [Highlights](#highlights)
- [How Theo works](#how-theo-works)
- [Screen-aware automation](#screen-aware-automation)
- [AI engines and local inference](#ai-engines-and-local-inference)
- [Memory and history](#memory-and-history)
- [Liquid Glass interface](#liquid-glass-interface)
- [Privacy and safety](#privacy-and-safety)
- [Roadmap](#roadmap)
- [Development and build](#development-and-build)
- [Project documentation](#project-documentation)
- [Credits and third-party projects](#credits-and-third-party-projects)

## Highlights

The current codebase and its documentation describe the following capabilities and design choices:

- **Native Android app:** Kotlin and Jetpack Compose, with Android-native UI and system integrations.
- **AI planning:** Gemini and a configured local Qwen endpoint are documented options for the screen-action planner.
- **Local model runtime:** The project includes a llama.cpp-based Android runtime dependency for direct on-device GGUF inference on `arm64-v8a`. Actual model availability, memory use and device performance depend on the model and device configuration.
- **Screen-aware automation:** Uses Android Accessibility Service APIs to inspect accessible UI elements and perform a limited set of structured actions.
- **Screen understanding:** On Android 11 and newer, the documented automation path can use a one-shot accessibility screenshot when needed.
- **Semantic-first interactions:** Prefers accessible controls over fixed coordinates. A cautious coordinate fallback is documented for screens that expose no actionable accessibility nodes.
- **Task safeguards:** Limited action steps, failure limits, clarification when uncertain, and confirmations for potentially sensitive actions.
- **App launching:** Resolves requested apps against installed launchable apps and asks the user to choose when a match is ambiguous.
- **Music-request flow:** Documented behavior can open a provider search and continue into the screen-action loop; playback must not be claimed without visible evidence.
- **Local app data:** Room is included in the Android dependencies; the project describes existing memory/history and database functionality. The exact capabilities of the current memory experience should be checked against the implementation.
- **Liquid Glass experiment:** Uses a shared Compose glass engine for selected surfaces, while keeping repeated list items lighter for performance.
- **Diagnostics:** The project documents a LOGS tab for concrete screenshot failures where available.

This is an experimental branch. The repository's code and the documentation linked below are the source of truth for what works in a particular build.

## How Theo works

At a high level, the screen-aware task flow is:

1. **Understand the request.** Theo interprets the user's instruction and determines whether it requires ordinary conversation, app launching, or screen-aware actions.
2. **Inspect the current state.** For a screen task, Theo takes a fresh accessibility-tree snapshot. An explicit screen-description request uses the screen-inspection path.
3. **Plan one structured step.** The configured planner proposes a supported action.
4. **Validate and execute.** Theo accepts only known action types, such as clicking an accessible node, typing into an editable node, scrolling, going back, waiting, finishing, or asking for clarification.
5. **Inspect again.** The accessibility tree is read again after each step so the next action can be based on the updated UI.
6. **Stop safely when needed.** Unclear actions, unsupported output, repeated failures, or sensitive actions can stop execution or request user input.
7. **Verify the outcome.** Dispatching a tap or opening an app is not proof that the requested task succeeded. Theo should rely on visible evidence before claiming completion.

The documented screen-action planner is limited to **12 planned steps per task**. Two consecutive failed accessibility actions stop the run. These limits help avoid uncontrolled loops.

## Screen-aware automation

Theo uses Android's native `AccessibilityService` API to inspect the active app's accessibility tree and perform semantic interactions.

### Enable it

1. Open Theo and go to **Settings**.
2. Find **Screen-aware Automation** and tap **Enable**.
3. Android opens its Accessibility settings.
4. Manually enable **Theo Screen Automation**.
5. Return to Theo and retry the request.

Android requires the user to enable this service; Theo cannot grant the permission silently.

### Supported behavior documented by the project

- Resolve installed apps by launchable app name. If the match is ambiguous, show an app chooser rather than opening an arbitrary app.
- Respond to requests such as “What is on my screen?”, “Look at my screen”, or “Describe the current screen” using screen inspection rather than guessing from chat context.
- Prefer accessibility nodes when actionable controls are available.
- Use a one-shot screenshot on Android 11+ when visual understanding is needed and the accessibility tree does not expose actionable controls.
- Accept validated actions: click a node, type into an editable node, scroll, go back, wait, finish, or ask the user to clarify.
- Briefly outline a semantic click target in cyan before activating it.
- Use a cautious normalized-coordinate tap fallback only when actionable accessibility nodes are unavailable and the intended control can be identified.
- Ask for confirmation before potentially sensitive actions such as sending, deleting, purchasing, sharing, or submitting.
- Stop rather than guess when model output is malformed or the intended control cannot be identified.

### Limitations

- Android 10 and earlier do not support the documented native screenshot path.
- Secure or DRM-protected windows may block screenshots or appear black.
- Accessibility behavior varies between apps. Some apps expose few useful controls, and coordinate fallback is less reliable than semantic actions.
- Android may interrupt tasks due to app lifecycle, permission, battery, or system restrictions.
- A model response saying “done” is not independent evidence that an action succeeded.

See [SCREEN_AUTOMATION.md](SCREEN_AUTOMATION.md) for the detailed behavior and enablement instructions.

## AI engines and local inference

Theo's current project configuration documents **Gemini** and a **configured local Qwen endpoint** as options for the screen-action planner. The Gradle dependencies also include a llama.cpp-based Android runtime intended for on-device GGUF inference on `arm64-v8a`.

These are different concepts: a local inference runtime does not automatically mean a model is bundled, downloaded, configured, or fast enough for every device. Check the app's actual configuration and model setup before assuming a particular local model is ready to use.

### Cost and connectivity

- Local inference can avoid sending prompts to a hosted model, but requires a compatible model file and sufficient device resources.
- Cloud AI providers may have quotas, rate limits, network requirements, or costs depending on the account and configuration.
- The repository does not promise that every AI feature works offline.
- No future feature in the roadmap should require a paid service as its only implementation path.

## Memory and history

The project documents existing memory/history functionality and includes Room database dependencies. The goal is for Theo to retain useful user-approved context between conversations rather than behave as if every conversation starts from zero.

**Important:** The memory redesign below is a planned improvement, not a claim that all of these controls already exist in the current build.

### Planned memory improvements

- Upgrade the existing Memory page in the dock instead of creating a separate, disconnected memory system.
- Add a Settings entry that opens the same memory screen and underlying records.
- Let the user create, search, edit, categorize, pin, disable, export, import, and delete memories.
- Support explicit instructions such as “remember this”, “forget that”, “that's wrong”, and “what do you remember about me?”
- Use corrections to update outdated facts rather than create contradictory duplicates.
- Mark suggested or uncertain memories for review instead of silently treating guesses as facts.
- Retrieve only relevant, enabled memories for a conversation or task, rather than injecting the entire memory database into every prompt.
- Provide controls for automatic memory, review-before-saving, and clearing all memories.
- Keep secrets such as passwords and API tokens out of memory by default.

The intended first implementation is local-first: reuse the current database if appropriate, or use Room/SQLite with local full-text search. A hosted vector database should not be required. Memory should remain editable and useful even when no AI provider is available; AI-based extraction can be optional.

## Liquid Glass interface

The `experiment/theo-liquid-glass` branch is a UI experiment based on `feature/screen-aware-automation`.

The design direction is a spacious, atmospheric assistant interface rather than a dense, boxed dashboard:

- Ink/navy backgrounds with restrained blue and violet lighting.
- Translucent surfaces, subtle optical edge lighting and consistent corner radii.
- Floating glass navigation, with a glass header and composer where the effect adds value.
- Responsive hero and chat sizing rather than fixed dimensions designed for one phone.
- Consistent screen gutters and typography; monospace is reserved for diagnostics and literal technical data.
- Expensive refraction effects restricted to high-value surfaces such as the dock, header and composer.
- Lighter translucent materials for repeated chat bubbles, memory rows, settings rows and action chips to reduce rendering cost.

The active shared glass engine documented by the project is [Kyant0/AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass). Other projects were evaluated as references; see [LIQUID_GLASS_DESIGN.md](LIQUID_GLASS_DESIGN.md).

**Build success is not visual verification.** The UI still needs to be checked on a real device for navigation insets, keyboard behavior, long chats, different screen widths, refraction and scrolling performance.

## Privacy and safety

Screen automation is powerful and may expose private content, so its behavior should remain explicit and conservative.

The current screen-automation documentation states that:

- Theo does not record accessibility events or store screen text as a background activity.
- Passwords and current editable field values are excluded from the accessibility tree.
- Screenshots used for analysis are held in memory and cleared after analysis; Theo does not save them through this path.
- During a user-requested screen task or inspection, visible labels are sent to the configured planner.
- If cloud vision is configured, the screenshot may be sent to Groq or Gemini for that request.
- Sensitive-looking actions require confirmation.
- Uncertain or unsupported actions should stop or ask for clarification instead of being guessed.

Do not assume that all processing is local just because Theo runs on Android. The configured provider determines whether a request or screenshot leaves the device. Review [SCREEN_AUTOMATION.md](SCREEN_AUTOMATION.md) and the app's actual settings before using screen inspection with sensitive content.

### Planned privacy improvements

- A privacy dashboard showing which data is local and which is sent to configured AI providers.
- Separate controls for memory, chat history, screen analysis and optional diagnostics.
- Export and deletion controls for local data.
- No mandatory paid analytics or cloud-sync service.
- Clear disclosure before screen content is sent to a cloud provider.

## Roadmap

The items below are **planned improvements** and should not be read as features already available.

### Phase 1 — Reliable personal memory

- Improve the existing dock Memory page and add a Settings entry to the same page.
- Add reliable local persistence, search, categories, edit/delete, export/import and duplicate correction.
- Retrieve relevant memories into future conversations and tasks.
- Add user-controlled saving, review and forget controls.

### Phase 2 — More reliable task execution

- Show task status and a step-by-step timeline.
- Improve bounded retries, re-inspection and recovery after failed actions.
- Add clear cancellation and useful failure explanations.
- Verify results before claiming success.

### Phase 3 — Reusable skills and task queue

- Save successful workflows as named, editable skills.
- Add parameters, trigger phrases, dry-run previews and per-step logs.
- Persist long-task status and add cancellation/retry controls.
- Use Android notifications and background APIs only where supported by Android's lifecycle and permission rules.

### Phase 4 — Integrations and knowledge vault

- Improve app matching and app-specific workflows.
- Add a local searchable vault for user-selected notes, snippets, links and project documentation.
- Explore Discord and BitChord integrations using supported APIs, intents, media sessions, or verified accessibility actions.
- Do not claim direct app control until the actual integration has been tested.

### Phase 5 — Voice and proactive assistance

- Add optional Android-native speech input and text-to-speech.
- Let users customize Theo's conversational style.
- Add opt-in routines, reminders and user-approved suggestions.
- Add a privacy dashboard and final polish.

### Future feature design principles

- Prefer local storage and open-source components.
- Avoid paid APIs, hosted databases and subscriptions as mandatory dependencies.
- Reuse Theo's existing planner, action types, database, navigation and verification systems.
- Keep consequential actions behind explicit confirmation.
- Preserve the existing UI and functionality while extending it.
- Document which features work offline and which require a configured AI provider.

## Development and build

Theo is an Android project using Kotlin and Jetpack Compose.

### Project configuration

- **Application ID:** `com.aistudio.theoai.qxntp`
- **Minimum SDK:** 24 (Android 7.0)
- **Target SDK:** 36
- **Compile SDK:** 36, minor API level 1
- **Java compatibility:** 11
- **Primary UI framework:** Jetpack Compose
- **Local database dependency:** Room
- **AI/network dependencies:** include provider integrations, Retrofit/OkHttp and related libraries; check the current Gradle files for the exact configuration.
- **Local inference dependency:** llama.cpp-based Android runtime for `arm64-v8a`.

### Build locally

1. Clone the repository and check out `experiment/theo-liquid-glass`.
2. Open the project in a compatible version of Android Studio with the Android SDK required by the Gradle configuration.
3. Review `.env.example` and the project's provider configuration. Create a local `.env` only if required by the current setup. Never commit real API keys or secrets.
4. Allow Gradle to sync and resolve dependencies.
5. Build and run the debug variant using Android Studio, or run the Gradle wrapper task `assembleDebug` from the project root.
6. Install on a test device and manually enable the Accessibility Service if testing screen automation.
7. Verify the actual task behavior and UI on-device.

A release build may require signing configuration. Never publish signing keys or credentials.

### CI and device testing

- Build the exact commit being tested.
- Review the build logs and test results.
- Verify screen automation, sensitive-action confirmation and failure handling.
- Check the Liquid Glass UI on the target phone, including keyboard-open behavior, navigation insets, long chats and scrolling.
- Do not describe a build as visually verified merely because CI passes.

## Project documentation

- [Screen-aware automation](SCREEN_AUTOMATION.md) — setup, action types, limits, privacy and known limitations.
- [Liquid Glass design](LIQUID_GLASS_DESIGN.md) — design intent, rendering choices and device-validation checklist.
- [Third-party notices](THIRD_PARTY_NOTICES.md) — third-party projects and license notices.
- [Example environment configuration](.env.example) — configuration template; do not put real secrets in Git.

## Credits and third-party projects

Theo builds on Android and open-source tooling. The project documents the use of Kyant0's Backdrop/AndroidLiquidGlass engine for selected Compose surfaces and notes that its screen-automation architecture was informed by MobileAgent-Android (MIT), adapted to Theo's existing Kotlin/Compose app and configured AI clients.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the repository's complete notices and license information. Check each upstream project's current license before copying or redistributing code.

## Project status

Theo is actively evolving. The screen-aware automation and Liquid Glass work described in this branch are documented design/implementation areas; the memory overhaul, reusable skills, richer task queue, knowledge vault, privacy dashboard, and other roadmap items are future goals until implemented and tested.

Contributions and experiments should preserve existing behavior, document new permissions and data flows, and include tests or reproducible device-verification steps where possible.
