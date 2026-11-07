# Theo 2.0 — M0 Repository Audit Checklist

**Status:** Preliminary inspection complete; detailed audit remains open.  
**Repository:** `Kentaakakashi/Theo-AI-mobile`  
**Working branch:** `feature/liquid-glass-ui`  
**Native baseline:** `33526b640479c7e62a30000358e05b4491e2ef13`

This checklist is the execution record for M0. Do not mark an item complete based only on a successful APK build.

## 1. Preliminary inventory

The restored baseline was confirmed to contain no React `web-ui` directory and no WebView chat screen. Home routes to the native `CoreAssistantScreen`.

### Existing source areas

| Area | Files / responsibilities | Audit status |
|---|---|---|
| App composition | `MainActivity.kt`, theme and navigation | Located; call graph review pending |
| UI | Core assistant, memory, activity, diagnostics, settings, Compose components | Located; interaction review pending |
| State | `TheoViewModel.kt` | Located; detailed responsibility map pending |
| AI | `TheoAiEngine.kt`, Gemini and local Qwen paths | Located; provider contract review pending |
| Device actions | `DeviceActionHandler.kt`, action parser and safety policy | Located; permission/action matrix pending |
| Screen automation | Accessibility service, planner, verifier, foreground service | Located; lifecycle/threat review pending |
| Speech | `TheoSpeechManager.kt` | Located; lifecycle review pending |
| Persistence | Room entities, DAO, database | Located; migration/data ownership review pending |
| Diagnostics | `TheoDiagnosticLogger.kt` and diagnostics UI | Located; redaction review pending |
| Build | Gradle version catalog, Android Gradle file, GitHub Actions | Located; dependency and release-signing review pending |
| Tests | Parser, verifier, Gemini configuration, automation tests | Located; coverage map pending |

## 2. Preliminary findings to verify

- `TheoViewModel` is a large coordinator touching AI, Room, speech, screen automation, device actions, and UI state. Measure responsibilities and dependencies before extracting use cases.
- The Room database is currently version 1 with schema export disabled. Plan explicit schema export and migrations before adding task/agent entities.
- Initial database seed data includes an obsolete Termux bridge memory even though the current Qwen runtime is in-process. Verify seed behavior and existing user-data impact before changing it.
- There is no dedicated agent registry, durable task queue, custom-agent store, cloud execution provider, or collaboration protocol in the restored baseline.
- Existing screen automation deliberately resets an interrupted task rather than replaying actions. Preserve this behavior; cloud task recovery requires a separate design.
- The Android manifest includes broad permissions. Review each one against current features and Android policy; remove only after confirming no existing functionality depends on it.
- Gemini and local Qwen are not interchangeable in capability or privacy. Audit their supported structured-output, streaming, cancellation, and context behavior before defining a provider abstraction.
- Debug CI signing keys are generated per run. Do not represent CI debug APKs as stable update-compatible release builds.

## 3. Work packages

### M0.1 — Repository and dependency map

- [ ] Map every screen and its ViewModel entry point.
- [ ] Map ViewModel method → service/repository → persistence/provider call paths.
- [ ] Identify duplicate, unreachable, or obsolete source files.
- [ ] Parse the Gradle version catalog and direct/transitive dependencies.
- [ ] Record Android SDK, AGP, Kotlin, Compose, KSP, Java, and Gradle compatibility.
- [ ] Record licenses for dependencies proposed for reuse.
- [ ] Produce a dependency diagram and feature ownership table.

### M0.2 — AI provider audit

- [ ] Trace Gemini request construction, streaming, retries, timeouts, and error mapping.
- [ ] Trace local Qwen download, integrity verification, loading, inference, memory use, and cleanup.
- [ ] Document provider/model configuration persistence.
- [ ] Audit API key storage, build-time injection, log redaction, and APK exposure.
- [ ] Document prompt/context composition and token limits.
- [ ] Identify provider capability differences for structured output and tool proposals.
- [ ] Define provider interface candidates without implementing a migration yet.

### M0.3 — Android/device execution audit

- [ ] Inventory manifest permissions and service declarations.
- [ ] Map accessibility service startup, foreground-service lifecycle, pause/resume, and stop.
- [ ] Document supported action schemas, validation, step limits, and failure limits.
- [ ] Map sensitive-action confirmation and its current state ownership.
- [ ] Review screenshot capture, data retention, and provider upload paths.
- [ ] Review notification access and app visibility behavior.
- [ ] Identify which operations require the device to remain reachable.
- [ ] Produce a capability/permission/approval matrix.

### M0.4 — Persistence and continuity audit

- [ ] Export and review the current Room schema.
- [ ] Identify all database reads/writes and ownership boundaries.
- [ ] Design a non-destructive migration path.
- [ ] Identify seeded data that should be retired without deleting user-created data.
- [ ] Define local-only, cloud-persisted, and cached data categories.
- [ ] Define memory scopes: user, project, agent, and task.
- [ ] Define export, deletion, retention, and offline behavior.

### M0.5 — Reliability and test audit

- [ ] Run all current unit tests on the restored baseline.
- [ ] Map each test to a production behavior and failure mode.
- [ ] Identify missing tests for cancellation, retry, timeout, and process interruption.
- [ ] Add a regression checklist for chat, memory, speech, settings, logs, and accessibility.
- [ ] Verify artifact upload and APK metadata in CI.
- [ ] Capture a device test checklist for the target Android phone.
- [ ] Establish a rollback point for each future migration slice.

### M0.6 — Backend and cloud execution decision

- [ ] Compare backend candidates for authentication, SQL/data isolation, storage, jobs, and cost.
- [ ] Compare India/APAC data-region options and latency.
- [ ] Evaluate free-tier ceilings and inactivity/sleep behavior.
- [ ] Define how workers claim jobs, heartbeat, recover, cancel, and avoid duplicate side effects.
- [ ] Evaluate Playwright browser isolation, cookie/session cleanup, file transfer, and human takeover.
- [ ] Decide whether a separate Theo backend project is required.
- [ ] Do not repurpose the existing Ryu project as Theo's backend by default.
- [ ] Record the selected option and reasons in an architecture decision record.

### M0.7 — Agent and tool contracts

- [ ] Define agent definition and version model.
- [ ] Define task and task-step schemas.
- [ ] Define agent-run and tool-call lifecycle.
- [ ] Define structured agent handoff and artifact references.
- [ ] Define tool capability declarations and allowlists.
- [ ] Define immutable approval binding to exact action payloads.
- [ ] Define per-task budgets and concurrency limits.
- [ ] Define evidence and result-verification contracts.

### M0.8 — Differentiation and product validation

- [ ] For each major capability, document the market baseline.
- [ ] Identify a specific user problem and product limitation.
- [ ] Describe Theo's distinct implementation.
- [ ] Define a measurable improvement or user-visible advantage.
- [ ] Write an acceptance test that demonstrates the difference.
- [ ] Reject features included only for superficial parity.

## 4. Regression contract

The following existing behaviors are protected during M0 and M1:

- Native Compose UI; no React/WebView replacement.
- Existing Gemini conversation and streaming behavior.
- Existing local Qwen runtime and model integrity checks.
- Local chat, memories, and activity history.
- Speech state and cleanup.
- Screen-aware automation and accessibility permissions.
- Explicit confirmation for sensitive actions.
- Interrupted Android actions are not automatically replayed.
- Diagnostics remain available without logging secrets or private payloads.

## 5. M0 completion gate

M0 can be closed only when:

- [ ] The repository map is reviewed.
- [ ] Current test baseline is recorded.
- [ ] Existing feature regression contract is agreed.
- [ ] Provider and device capability matrix is documented.
- [ ] Data ownership and migration strategy is documented.
- [ ] Backend and cloud worker decision is recorded.
- [ ] Agent/task/tool/approval contracts are versioned.
- [ ] Threat model and privacy data-flow map are reviewed.
- [ ] M1 scope is divided into small, testable, reversible changes.

**Rule:** Do not create a large empty module structure, add a cloud backend, introduce a browser runtime, or refactor the entire ViewModel before the relevant M0 decision and acceptance tests exist.
