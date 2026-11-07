# Theo 2.0

**A native Android personal intelligence ecosystem — built to understand context, coordinate specialist agents, and help you get real work done.**

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Version](https://img.shields.io/badge/Version-2.0-2563EB)](#version-20)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-informational)](https://developer.android.com/)

> [!IMPORTANT]
> **Theo 2.0 is the next product and architecture generation, not a claim that every planned capability has shipped.** The current app remains a native Kotlin/Jetpack Compose Android application. Multi-agent orchestration, custom bots, durable cloud tasks, and cloud-computer execution are being developed in staged milestones. See the implementation status below.

---

## The idea

Theo is intended to become a **personal intelligence ecosystem**, not another chatbot and not a copy of an existing agent product.

You talk to Theo. Theo understands the goal, gathers the relevant context, chooses the right specialists, coordinates permitted tools and execution environments, checks the result, and keeps the useful history available for the next task.

The long-term advantage should come from continuity, coordinated specialist reasoning, safe real-world execution, and verifiable outcomes — not from the number of bots or model names in a menu.

## Product principles

1. **One Theo, many specialists.** Theo remains the central assistant. Specialist agents are capabilities it can assemble into temporary teams.
2. **Continuity over isolated chats.** Projects, decisions, task history, and user-approved memories should carry forward with clear controls.
3. **Hybrid execution.** Use the Android device for device-local actions and isolated cloud workers for browser and long-running work.
4. **Autonomy with boundaries.** Agents can plan and prepare work independently; consequential actions remain subject to explicit permission and approval.
5. **Evidence over confidence.** Theo must distinguish a proposed action, an executed action, and a verified result.
6. **Native-first Android.** The mobile application stays Kotlin and Jetpack Compose. No React/WebView replacement is part of this roadmap.
7. **Free-first and portable.** Prefer open standards and optional providers. Do not make an expensive hosted service mandatory without a clear decision and cost review.

## What exists today

The canonical `main` branch now points to the audited native Theo 2.0 baseline. It includes the existing capabilities below; exact availability still depends on provider configuration, Android permissions, model downloads, device support, and the current build.

- **Native Android UI:** Kotlin, Jetpack Compose, a native assistant screen, and native navigation.
- **AI engine:** Gemini integration with streaming responses and model configuration.
- **Local model runtime:** an in-process llama.cpp Android integration for a user-supplied/downloaded GGUF model. The model is not bundled in the APK.
- **Conversation and local data:** Room-backed chat history, memories, and activity history.
- **Speech:** Android text-to-speech integration.
- **Screen-aware automation:** Android Accessibility Service integration, structured action planning, bounded execution, and result verification.
- **Live Screen Sense:** opt-in periodic screen sampling (approximately every five seconds) while another app is foregrounded, using Gemini first and configured Groq as an alternate. It requires accessibility permission and cloud vision configuration; sampled frames are uploaded for analysis and are not saved by Theo.
- **Persistent screen memory:** optional user-selected folder for bounded, app-specific text observations. Theo recalls recent observations for the matching app; raw screenshots are not stored.
- **Floating Theo panel:** live task controls, pause/resume/stop, drag support, and a chat composer connected to Theo's normal conversation pipeline.
- **Theo personality:** a consistent casual companion and tutor voice, with explicit honesty and action-verification rules.
- **Safety checkpoints:** confirmation handling for sensitive or uncertain actions.
- **Diagnostics:** a local diagnostic console and persistent diagnostic entries.
- **Build pipeline:** GitHub Actions builds a debug APK and uploads it as a workflow artifact.

These existing capabilities are the foundation we intend to extend. A successful CI build is not the same as real-device verification.

## The Theo 2.0 system

The target architecture is divided into six cooperating parts:

| System | Responsibility |
|---|---|
| **Native client** | Compose UI, chat, voice, approvals, task dashboard, device connection, local cache |
| **Application domain** | User-facing use cases, task creation, permissions, agent and device contracts |
| **Theo orchestrator** | Task understanding, planning, delegation, coordination, recovery, result synthesis |
| **Agent runtime** | Built-in and custom agent definitions, scoped context, tool permissions, versioning |
| **Execution providers** | Android device actions, cloud browser, APIs, and isolated background workers |
| **Persistence and control plane** | User identity, task state, agent configuration, approvals, memories, artifacts, audit events |

### Specialist teams

Theo will ship with built-in specialists and support user-created bots. Initial built-in roles are planned as:

- **Research:** collect relevant sources and evidence.
- **Analysis:** compare options, test assumptions, and identify gaps.
- **Planning:** break goals into ordered, realistic steps.
- **Coding:** inspect and help modify software projects through explicitly granted tools.
- **Browser:** operate an isolated cloud browser within approved boundaries.
- **Android:** request and coordinate device-local capabilities through Android's permission system.

These are starting roles, not six independent assistants with unrestricted access. Users should be able to create custom agents with their own instructions, model choice, memory scope, and allowed tools.

### Collaboration model

Agents should collaborate through structured tasks and shared artifacts. Theo owns the overall plan, assigns bounded subtasks, tracks status, resolves conflicting findings, and produces the final answer. An agent must not silently rewrite another agent's private memory or broaden its own permissions.

### Hybrid execution

- **Android execution** is for installed apps, accessibility-based screen interaction, notifications, and device capabilities. It requires the phone to be reachable and the relevant Android permissions to be enabled.
- **Cloud execution** is for isolated browser sessions, research, file processing, and durable jobs that should continue when the app is closed.
- **Shared task protocol** connects both environments without pretending either environment can access the other's private resources.

A cloud browser cannot operate the phone's installed apps. A phone-only action cannot be guaranteed to continue when Android terminates the process. Theo must report these limits honestly.

## Roadmap and implementation status

| Milestone | Scope | Status |
|---|---|---|
| **M0 — Architecture and audit** | Repository audit, module boundaries, task/agent contracts, security model, backend decision, acceptance tests | In progress |
| **M1 — Agent Core** | Typed task model, orchestration, agent registry, task lifecycle, deterministic tests | Planned |
| **M2 — Theo Bots** | Built-in specialists, custom bot creation, configuration and tool scopes | Planned |
| **M3 — Collaboration** | Delegation, parallel subtasks, shared artifacts, result synthesis | Planned |
| **M4 — Continuity** | Project context, scoped memory, task history, export and deletion controls | Planned |
| **M5 — Durable tasks** | Queue, worker leases, cancellation, retries, scheduling and notifications | Planned |
| **M6 — Cloud computer** | Isolated browser runtime, session controls, artifacts and human takeover | Planned |
| **M7 — Approval system** | Action previews, approval binding, audit trail and revocation | Planned |
| **M8 — Android integrations** | A consistent device capability interface and permission-aware actions | Planned |
| **M9 — Extensibility** | Tool/plugin registry, custom integrations, provider adapters | Planned |

Milestones are gated by tests and working demonstrations. They are not calendar promises.

## Reliability requirements

- Every task has a durable, explicit state and a traceable owner.
- Every tool declares its capabilities, environment, permissions, and approval requirements.
- Tool inputs are schema-validated before execution.
- Retries are bounded and safe against duplicate side effects.
- Cancellation is propagated to running work where supported.
- Interrupted Android actions are never blindly replayed.
- Cloud jobs use durable state and recoverable worker claims rather than depending on a phone process staying alive.
- Agent context is scoped; secrets are not placed in prompts, ordinary logs, or reusable memory.
- User-visible success requires verifiable evidence.

## Privacy and security

Screen content, browser pages, files, and model responses are untrusted inputs. Accessibility and screenshot-based features can expose private information, so they must remain user-initiated and clearly explained.

The current screen-automation path uses Android Accessibility APIs and may send task context or screenshots to the configured AI provider when required. Running an AI model through a mobile app does not automatically mean the request stays on-device.

Theo 2.0 must preserve these controls and add:

- Per-agent tool allowlists and least-privilege access.
- Explicit approval for consequential external or device actions.
- Approval records bound to the exact action being approved.
- Separate storage and access policies for each user's tasks, agents, memories, and artifacts.
- No service-role or backend secret keys in the Android APK.
- Redaction of credentials and sensitive payloads from logs.
- Clear export, deletion, and provider-data controls.
- Isolated cloud browser sessions with explicit user-visible lifecycle controls.

## Technology direction

Technology choices are intentionally staged. The repository audit and M0 decision record will select the minimum set of dependable components.

| Area | Direction |
|---|---|
| Android client | Kotlin + Jetpack Compose |
| Existing local persistence | Room |
| AI integration | Provider-independent interface around existing Gemini and local Qwen paths |
| Agent orchestration | Theo-owned typed orchestration layer |
| Cloud persistence | Select after backend, security, and cost evaluation |
| Background work | Durable server-side workers for cloud jobs; Android APIs for device-local work |
| Browser automation | Isolated Playwright-based cloud runtime is a candidate |
| CI/CD | GitHub Actions |

GitHub Actions is for build, test, and deployment automation; it is not the always-on runtime for user agent tasks. No new hosted backend or paid service is assumed to be provisioned by this README.

## Build and development

Theo is an Android Gradle project.

| Setting | Value |
|---|---|
| Application ID | `com.aistudio.theoai.qxntp` |
| Version name | `2.0` |
| Version code | `2` |
| Minimum SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| Compile SDK | 36 (minor API level 1) |
| Java compatibility | 11 |

### Build with Android Studio

1. Clone the repository:
   ```bash
   git clone https://github.com/Kentaakakashi/Theo-AI-mobile.git
   cd Theo-AI-mobile
   git checkout main
   ```
2. Open the project in Android Studio and let Gradle sync.
3. Review `.env.example` and configure providers locally as needed.
4. Never commit API keys, tokens, signing keys, or generated credentials.
5. Build and test:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```

### GitHub Actions

Pushes to branches trigger the Android build workflow. `main` is the canonical primary branch for Theo 2.0. A successful run uploads the debug APK as the `theo-debug-apk` artifact. Download it from the run's **Artifacts** section. Debug signing keys are generated for CI builds; they are not suitable as a stable release signing identity.

### Definition of done

A feature is not complete merely because it compiles. Changes must pass relevant automated tests, preserve existing assistant behavior, handle failure and cancellation, respect permissions, and be checked on a real Android device before being called device-verified.

## Project documentation

| Document | Purpose |
|---|---|
| [Theo 2.0 architecture](docs/architecture/THEO_2_ARCHITECTURE.md) | System boundaries, agent model, execution contract, task lifecycle and security |
| [M0 audit checklist](docs/audit/M0_REPOSITORY_AUDIT.md) | Repository inspection work packages and exit criteria |
| [Screen automation](SCREEN_AUTOMATION.md) | Android accessibility behavior, action limits, permissions and privacy |
| [Liquid Glass design](LIQUID_GLASS_DESIGN.md) | Native UI design and rendering constraints |
| [Third-party notices](THIRD_PARTY_NOTICES.md) | Upstream dependencies and licenses |
| [Environment example](.env.example) | Placeholder configuration only |

## Version 2.0

Version 2.0 marks the start of the new product and architecture generation. The version bump does not imply that the multi-agent roadmap is already complete. Features will be exposed as they become implemented, tested, and verified.

---

**Theo is built around one principle: intelligence should create useful outcomes, preserve continuity, and remain under the user's control.**
