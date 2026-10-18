# Theo Liquid Glass UI experiment

Branch: `experiment/theo-liquid-glass`  
Base development line: `feature/screen-aware-automation`

## Design intent

Theo is moving away from a boxed sci-fi dashboard and toward a spacious, atmospheric assistant UI:
- A layered ink/navy background with quiet blue and violet light.
- Fewer containers; content should float on the background wherever a card is not needed.
- One shared material language: translucent surfaces, soft optical edge lighting, modest radius families, restrained color and readable text.
- Floating live-glass navigation, plus a glass composer/header where the effect adds real value.
- Responsive hero sizing and adaptive chat widths; no fixed oversized orb or bubble width that assumes one phone size.
- Short, consistent gaps and 18–20dp screen gutters across the main screens.
- Cleaner sans-serif typography. Monospace is reserved for diagnostics and literal technical data.

## Open-source engines and references

- **Kyant0/AndroidLiquidGlass / Backdrop** — Apache-2.0, already used as the shared Compose glass engine for selected high-value surfaces. The API provides backdrop sampling, blur, lens/refraction and highlights.
- **BuildItCode/LiquidGlass** — MIT, evaluated for capture-backed dock rendering but removed from the active dependency list after its JitPack artifact returned HTTP 401 in CI. No source from the repository is copied.
- **PrismalAGSL** — studied for AGSL rendering and graceful fallback ideas. It is not added as a third runtime renderer because Theo already has two glass engines and another would increase compatibility and performance risk.
- **amunozdev/voiceorbs** — MIT visual reference for the orb direction; Theo's orb is a native Compose implementation, not bundled React/CSS.
- **Abdullajon1881/LiquidGlass** — studied for material/refraction and interactive gel ideas.
- **shayann07/liquidglass** — Apache-2.0 visual/technical reference; its published version was not adopted because of SDK compatibility concerns recorded in third-party notices.

Source projects:
- https://github.com/Kyant0/AndroidLiquidGlass
- https://github.com/BuildItCode/LiquidGlass
- https://github.com/styropyr0/PrismalAGSL
- https://github.com/amunozdev/voiceorbs
- https://github.com/Abdullajon1881/LiquidGlass
- https://github.com/shayann07/liquidglass

## Rendering/performance choices

- The dock, header and composer use Kyant Backdrop's optical tier. The dock uses a stronger tint/refraction recipe and an animated selected island; expensive optical surfaces are restricted to navigation/header/composer.
- Repeating chat bubbles, memory rows, settings rows and action chips use a lightweight translucent material tier. Do not enable expensive live lensing for every item in a long scrolling list.
- Material alpha is tuned centrally so all screens remain more transparent and less boxed in.
- Content reserves space for the floating dock in one place. Secondary screens should not add a second navigation-bar inset.
- Keep the ambient scene's backdrop definition and canvas colors in sync.
- Preserve the existing orb, memory/history/settings, AI clients, Qwen runtime, database, accessibility service, task verification, action confirmations and diagnostics behavior.

## Validation requirements

CI must build the exact new experiment-branch commit before the APK is offered. Build success is not device visual verification. On the user's phone, inspect: header/composer refraction, selected dock island alignment, nav/gesture insets, screen-width variations, long chats, keyboard-open behavior and scroll performance. Visual tuning should follow actual screenshots rather than assumptions.
