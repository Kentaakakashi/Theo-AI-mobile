# Theo 2.0 — Architecture Specification

**Status:** M0 working specification  
**Client baseline:** Native Kotlin + Jetpack Compose  
**Product direction:** Personal intelligence ecosystem; not a clone of another agent product

This document describes the intended architecture. It deliberately separates existing repository capabilities from proposed systems that have not been implemented yet.

## 1. Product invariants

1. Theo is the single user-facing assistant and orchestration authority.
2. Specialist agents are scoped workers that Theo can assemble into temporary teams.
3. The Android application remains native Kotlin/Jetpack Compose. No React/WebView rewrite is planned.
4. Android and cloud are distinct execution environments joined by a typed task protocol.
5. Model output is untrusted input. Application policy and typed validators decide whether a tool request is accepted.
6. Consequential actions require an explicit, current approval bound to the exact action.
7. A result is successful only when the relevant execution evidence supports that conclusion.
8. New cloud infrastructure is optional until backend, cost, data residency, security, and operations have been reviewed.

## 2. Logical architecture

```text
┌─────────────────────────────────────────────────────────────┐
│                    Native Android Client                    │
│ Compose UI · Chat · Voice · Tasks · Bots · Approvals         │
└──────────────────────────────┬──────────────────────────────┘
                               │ typed API / local use cases
┌──────────────────────────────▼──────────────────────────────┐
│                  Application / Domain Layer                 │
│ Use cases · Task contracts · Agent contracts · Permissions   │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Theo Orchestration Core                  │
│ Plan · Route · Delegate · Coordinate · Verify · Synthesize   │
└────────────────┬───────────────────────────────┬────────────┘
                 │                               │
┌────────────────▼────────────────┐  ┌───────────▼────────────┐
│        Agent Runtime             │  │      Policy Engine     │
│ Built-in + custom agents         │  │ Tool scopes · approval │
│ Versioned config · scoped state  │  │ limits · validation    │
└────────────────┬────────────────┘  └───────────┬────────────┘
                 │                               │
┌────────────────▼───────────────────────────────▼────────────┐
│                   Execution Provider Layer                  │
│ Android device · Cloud browser · API tools · Workers         │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│               Persistence / Observability                   │
│ Room cache · Cloud task store · Artifacts · Audit events     │
└─────────────────────────────────────────────────────────────┘
```

The diagram is logical, not a required Gradle module layout. Physical module boundaries will be introduced only after the M0 dependency and ownership audit.

## 3. Agent contract

An agent is a versioned configuration and execution policy, not merely a prompt string.

Proposed fields:

- `agentId`: stable opaque identifier.
- `ownerId`: user or system owner.
- `name` and `description`.
- `role`: specialist capability classification.
- `instructionsVersion`: immutable version reference.
- `modelProfile`: provider and model preference, with safe fallback policy.
- `toolAllowlist`: explicit tool identifiers.
- `memoryScope`: private, project, or task-scoped references.
- `limits`: token, duration, concurrency, and tool-call budgets.
- `enabled` and lifecycle metadata.

Built-in agents are versioned by Theo. Custom agents are user-owned. Neither type receives permissions merely because its instructions request them.

### Initial specialists

Research, Analysis, Planning, Coding, Browser, and Android are proposed initial roles. They should be implemented only when each role has a meaningful capability boundary and useful acceptance tests.

## 4. Task model and lifecycle

A task is the durable unit of user intent. It can contain ordered or parallel steps, agent runs, tool calls, approvals, and artifacts.

Proposed task states:

`draft` → `queued` → `planning` → `running` → `succeeded`

Alternative terminal or waiting states:

- `awaiting_approval`
- `paused`
- `failed`
- `cancelled`
- `expired`

Retrying is a controlled transition with a bounded attempt count, not an unbounded loop. A task must carry an idempotency key for side-effecting operations where the provider supports it.

Each task step records:
- input and output schema version;
- assigned agent and execution environment;
- start/end times and attempt number;
- state transitions and sanitized error category;
- related approval identifiers;
- evidence references and artifact references.

A user cancellation must be propagated to the orchestrator, agent run, and active tool whenever supported. If cancellation cannot stop an external operation already in progress, Theo must report that accurately.

## 5. Agent collaboration protocol

Agents exchange typed task envelopes rather than free-form hidden chat alone.

A proposed envelope contains:
- `taskId`, `stepId`, `parentStepId`;
- `senderAgentId`, `recipientAgentId`;
- `goal`, `constraints`, `inputArtifactRefs`;
- `expectedOutputSchema`, `deadline`, `budget`.

Agent output contains:
- structured result;
- evidence and source references where relevant;
- assumptions and unresolved questions;
- confidence explanation where useful;
- proposed next actions (never auto-authorized tool execution).

Theo owns task completion and final synthesis. Agents cannot silently expand task scope, mutate another agent's private memory, or grant themselves new tools.

## 6. Tool and execution contract

Every tool declares:
- unique tool ID and schema version;
- execution environment: Android, cloud, or provider API;
- input and output schemas;
- required capabilities and data access;
- side-effect classification;
- approval policy;
- timeout, retry, and idempotency behavior;
- evidence returned after execution.

### Android provider

The current native Accessibility Service and device-action path are the starting point. A future adapter should expose a narrow interface to approved app/device capabilities. It must not expose arbitrary shell access or silently bypass Android permission prompts.

Device-local work requires the device to be reachable and relevant permissions enabled. A cloud worker must not claim to have performed a device-local action.

### Cloud provider

A cloud execution service is a candidate for durable research, file processing, and isolated browser sessions. It should use:
- isolated per-task browser contexts;
- bounded session lifetime and resource budgets;
- explicit user-visible browser activity;
- controlled file exchange;
- cancellation and cleanup;
- no reuse of another user's cookies, storage, or session state.

Playwright is a candidate browser automation engine, not a final backend decision.

## 7. Approval and policy model

Policy evaluation happens in application-controlled code before tool execution.

An approval record should bind to:
- user and task;
- exact tool identifier;
- canonical hash of the exact validated action payload;
- destination/target when applicable;
- summary shown to the user;
- issue time and expiry;
- approval or denial decision.

Any material payload change invalidates the previous approval. Approvals are not reusable across tasks or similar-looking actions.

Read-only research can generally proceed under its scoped allowlist. Sending, publishing, purchasing, deleting, sharing, changing important device settings, or other consequential actions require a fresh explicit confirmation.

## 8. Persistence boundaries

### Android local store

Room remains the local persistence layer for current chat, memory, and activity. It can also provide an offline cache for task and agent metadata. Schema changes require exported schemas and explicit migrations; do not clear or destructively recreate the user's database.

### Cloud store

Cloud persistence is required for durable multi-device tasks and cloud workers, but the provider is not selected by this document. M0 must compare authentication, PostgreSQL/data isolation, storage, background worker integration, costs, quotas, data region, export/deletion, and phone-only deployment feasibility.

The connected Supabase account currently exposes a project named Ryu Project. It is not to be repurposed as Theo's production backend without a separate explicit architecture and data-isolation decision.

### Memory scopes

- user profile memory;
- project memory;
- agent-private memory;
- task-local context.

Memory must have a source, owner, scope, timestamp, and deletion path. Credentials and secrets are excluded. Cross-agent sharing happens through explicit task artifacts, not implicit unrestricted memory access.

## 9. AI provider abstraction

The current Gemini integration and local Qwen runtime should sit behind a provider-neutral boundary over time.

The boundary should distinguish:
- plain text generation;
- structured output;
- tool proposal generation;
- streaming;
- cancellation;
- provider/model capability metadata;
- error and rate-limit classification.

Do not assume every provider supports the same structured output or tool-calling semantics. Parse and validate all model output locally before it reaches a tool adapter.

Provider selection and fallback must not silently change privacy expectations or route private screen content to a different service.

## 10. Observability and operations

A task event stream should record state transitions, agent assignment, tool lifecycle, approvals, retries, and terminal outcomes.

Logs must be structured and sanitized. Never write API keys, authorization headers, passwords, full private message bodies, raw screenshots, or browser cookies into ordinary diagnostic logs.

Operational controls:
- per-user and per-task quotas;
- maximum agent concurrency;
- maximum tool calls and wall-clock duration;
- bounded retries with backoff;
- worker leases/heartbeats;
- stuck-task recovery;
- cleanup for expired browser sessions and artifacts;
- visible stop/cancel controls.

GitHub Actions remains the build/test/deploy pipeline. It is not the always-on task worker.

## 11. Proposed physical module direction

The following is a target boundary map, not a request to create empty modules immediately:

- `app`: Android composition root, Compose UI, Android lifecycle.
- `core:domain`: task, agent, approval, and tool contracts.
- `core:orchestration`: planning and delegation state machine.
- `core:agents`: built-in and custom agent runtime.
- `core:policy`: permission evaluation and approval binding.
- `data:local`: Room entities, DAOs, migrations, repositories.
- `data:remote`: authenticated backend API and cloud synchronization.
- `platform:android`: Accessibility Service, notifications, device capability adapters.
- `backend`: separately deployed API, worker, and isolated browser runtime when selected.

M0 should first identify dependency cycles and the smallest safe extraction path from the current app module.

## 12. M0 exit criteria

M0 is complete when the repository audit has documented:

1. Every important screen, ViewModel, service, database entity, and AI provider path.
2. Existing features and regression tests that must remain unchanged.
3. Dependency and build constraints.
4. Local-data schema and migration requirements.
5. Agent, task, tool, approval, and event contracts.
6. Android/cloud execution boundaries.
7. Backend comparison and cost/security decision.
8. A threat model and data-flow inventory.
9. M1 implementation slices with acceptance tests and rollback points.

No large-scale refactor, new backend provisioning, or broad dependency upgrade should happen before these decisions are reviewed.
