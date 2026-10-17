# Theo Liquid Glass UI experiment

Branch: `experiment/theo-liquid-glass`  
Base: `feature/screen-aware-automation`

## Visual direction

- Near-black navy ground with cool blue / violet light; no scanline texture or heavy neon outlines.
- Reuse the VoiceOrbs Glass Orb silhouette language: deep glass body, soft cyan-blue aura, glossy rim, travelling highlight and flowing inner ribbon. The implementation is native Compose so Theo remains a native app.
- Use shayann07/liquidglass `0.1.0` as the live shader-backed Compose material for floating chrome; older Android versions use its fallback styling.
- Use Kyant0/AndroidLiquidGlass as a primary study for restrained refraction, legible tinted surfaces and optical edge behavior, while keeping a thin local compatibility surface system for the many existing screen components.
- Use other suggested projects only as visual / technical references. No external assistant app or automation engine is substituted for Theo's own.

## Screens touched

- Home: greeting hierarchy, center orb and assistant-state caption.
- Navigation: floating rounded liquid-glass material, calmer labels and spacing.
- Header: human-readable assistant identity and state.
- Composer: cleaner quick-action chips and a direct “Ask Theo anything...” prompt.
- Chat: translucent semantic colors and restrained rim borders.
- Memory, activity, diagnostics and settings inherit the new surface palette and no-scanline treatment.

## Engineering constraints

- Automation, accessibility service, AI providers, local Qwen runtime and persistence are intentionally untouched.
- Keep shader use restrained on a 6 GB phone. Avoid applying high-cost blur to every row or chat bubble.
- The orb changes motion by assistant state. True microphone-amplitude and TTS-amplitude driving still needs a signal from the audio pipeline; state animation alone is not amplitude-reactive.
- Validate compile and unit tests in GitHub Actions, then inspect it on the actual device before claiming the visual result is final.

## Reference projects

- https://github.com/Kyant0/AndroidLiquidGlass — Apache-2.0.
- https://github.com/shayann07/liquidglass — Apache-2.0.
- https://github.com/amunozdev/voiceorbs — MIT.
- https://github.com/Abdullajon1881/LiquidGlass
- https://github.com/styropyr0/PrismalAGSL
- https://github.com/talhaluxury/JARVISAssistant

See `THIRD_PARTY_NOTICES.md` for attribution and dependency details.
