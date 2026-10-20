# Theo UI component research and integration ledger

This is the durable source list for Theo's visual redesign. Theo is a native Android app built with Kotlin and Jetpack Compose. **Prefer integrating the real upstream component when it targets Android/Compose. Do not silently recreate a React component and claim the result is the original.** If a web-only component cannot run natively, record that limitation and decide whether an actual WebView surface is appropriate before substituting anything.

Implementation work goes to `main` only. Visual polish must preserve existing assistant, settings, memory/history, diagnostics, accessibility automation, task verification and safety behaviour.

## Native Android / Jetpack Compose candidates

1. [BuildItCode/LiquidGlass](https://github.com/BuildItCode/LiquidGlass) — native Compose + classic Views liquid glass, AGSL refraction, edge highlights and layered backdrop capture. The previously attempted artifact returned HTTP 401 in CI; do not add it again until dependency coordinates resolve in a clean build.
2. [Abdullajon1881/LiquidGlass](https://github.com/Abdullajon1881/LiquidGlass) — native Compose / Views / React Native with SDF refraction, dispersion and shape merging. README says Maven Central release is not yet published; would require a local-source build, so evaluate exact source/build cost first.
3. [shayann07/liquidglass](https://github.com/shayann07/liquidglass) — cross-platform liquid-glass implementations; previously noted compile-SDK compatibility risk.
4. [chrisbanes/haze](https://github.com/chrisbanes/haze) — Compose blur/backdrop effects. Alternative renderer, not to be layered blindly over existing glass engine.
5. [Deiivid/Glassmorphism-Compose](https://github.com/Deiivid/Glassmorphism-Compose) — Compose glassmorphism candidate.
6. [neilyich/glassmorphism-compose](https://github.com/neilyich/glassmorphism-compose) — Compose glassmorphism candidate.
7. [Alims-Repo/FlowTab-CMP](https://github.com/Alims-Repo/FlowTab-CMP) — published animated Compose Multiplatform navigation component with optional Haze integration; compare against the existing optical dock before considering replacement.
8. [jeziellago/compose-markdown](https://github.com/jeziellago/compose-markdown) — upstream `MarkdownText` component. Integrated in this first UI pass for assistant replies at version 0.7.3.
9. [thelacspace/lucide-compose](https://github.com/thelacspace/lucide-compose) — native Compose Lucide icon set candidate for a consistent line-icon system.
10. [afiksoco/compose-uikit](https://github.com/afiksoco/compose-uikit) — loading buttons, progress indicators and visual UI helpers to assess.
11. [timoseyfarth/compose-shimmer-skeleton](https://github.com/timoseyfarth/compose-shimmer-skeleton) — skeleton/shimmer loading effects candidate.
12. [ebinjoy999/compose-skeleton-shimmer](https://github.com/ebinjoy999/compose-skeleton-shimmer) — alternative skeleton/shimmer candidate.
13. [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) — existing active runtime dependency through `io.github.kyant0:backdrop:1.0.0`; the current material helpers already call its official backdrop/lens APIs.
14. [amunozdev/voiceorbs](https://github.com/amunozdev/voiceorbs) — orb visual reference. Verify platform before integration; React/CSS source is not a native Compose component.

## Web / React component libraries and exact component references

These are mostly React/Tailwind/Motion components. Use them as actual web components if Theo intentionally has a WebView surface and the license/platform make sense; do not describe them as directly installable in a Kotlin Compose screen.

15. [React Bits](https://reactbits.dev/) — animated components, backgrounds, effects and text animations. [Source repository](https://github.com/DavidHDev/react-bits).
16. [Aceternity UI](https://ui.aceternity.com/) — React/Tailwind/Motion components and blocks. [Explore catalogue](https://ui.aceternity.com/explore).
17. [Magic UI](https://magicui.design/) — animated component and effect catalogue.
18. [Chamaac](https://chamaac.com/) — shader-focused UI component/effect source to evaluate.
19. [Aceternity Card Spotlight](https://ui.aceternity.com/components/card-spotlight) — specific spotlight-card component.
20. [Aceternity Spotlight New](https://ui.aceternity.com/components/spotlight-new) — specific spotlight background component.
21. [Aceternity Floating Dock](https://ui.aceternity.com/components/floating-dock) — specific dock component; compare with the existing native liquid dock.
22. [shadcn/ui](https://ui.shadcn.com/) — web UI primitives, dialogs, tabs, inputs and component patterns.
23. [shadcn AI Elements](https://ui.shadcn.com/) — AI/chat-oriented web components to evaluate in their published registry.
24. [React Bits text animations](https://reactbits.dev/) — actual source catalogue for text effects; reference entry retained separately because it was listed as a specific design target.
25. [shadcn Registry Directory](https://ui.shadcn.com/docs/directory) — community registry directory for more web components.

## Current decisions

- Keep the existing Kyant Backdrop renderer as the primary native glass engine while source-level compatibility and device performance are assessed. Avoid adding multiple overlapping live backdrop engines without a clear win.
- The first integration uses the upstream Compose Markdown dependency directly rather than manually parsing Markdown in Compose.
- The background now uses a restrained midnight-blue / cool-violet atmosphere; the glass material stays largely neutral so it refracts the scene instead of painting opaque colour over it.
- Next source audit: compare the real liquid-glass components and their licenses/Android API support, investigate Lucide Compose for consistent icons, then polish header, composer, navigation, Settings, Memory, Activity, Diagnostics and the native automation overlay. Do not change automation logic as part of visual-only work.
- CI success is only a compile/test signal. Device rendering, touch behaviour, accessibility and scrolling still need checks on the user's phone.
