# Third-party notices

## MobileAgent-Android

Repository: https://github.com/GiggleWang/MobileAgent-Android  
License: MIT License  
Use: The Android accessibility-service lifecycle and one-step-at-a-time screen automation approach were used as technical references. Theo's implementation is adapted to its existing Kotlin/Compose architecture, safety policy, and already-configured Gemini/Qwen clients.

Copyright (c) 2026 Yixiao Wang

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


## llama-android / llama.cpp

Repository: https://github.com/ffmpegkit-maintained/llama-android  
Artifact: `dev.ffmpegkit-maintained:llama-android:0.1.1`  
License: MIT License  
Use: Provides the in-process native llama.cpp Android runtime used to load and run the downloaded GGUF model. The library's upstream third-party notices apply to its bundled native dependencies.

## Qwen3-1.7B Q4_K_M model

Source: https://huggingface.co/unsloth/Qwen3-1.7B-GGUF  
File: `Qwen3-1.7B-Q4_K_M.gguf`  
Model license: Apache License 2.0 (Qwen3; see the upstream model and Qwen3 license metadata).  
Use: Downloaded on first user request into Theo's private app storage; model weights are not bundled in the APK. Theo validates the GGUF file signature and expected SHA-256 before loading it.


## Liquid-glass UI design references

Repository: https://github.com/shayann07/liquidglass  
License: Apache License 2.0  
Use: Studied as a primary reference for optical refraction, shape, and restrained edge lighting. The published `0.1.0` artifact was evaluated but not retained as a dependency because it requires compile SDK 37 and the current Android SDK runner could not install that platform. No source code from this repository is included.

## Liquid-glass component and interaction references

- https://github.com/Kyant0/AndroidLiquidGlass — Apache-2.0. Runtime dependency: `io.github.kyant0:backdrop:1.0.0`, used for true backdrop sampling, blur/lens refraction and optical highlights on the floating navigation surface. The upstream LICENSE and notices apply.
- https://github.com/amunozdev/voiceorbs — MIT. The native `TheoOrb` is an independent Jetpack Compose reimplementation inspired by the Glass Orb's visual direction and assistant-state behavior; its React/CSS source is not bundled.
- https://github.com/Abdullajon1881/LiquidGlass — studied for optical material and interaction ideas.
- https://github.com/styropyr0/PrismalAGSL — studied for shader rendering ideas.
- https://github.com/talhaluxury/JARVISAssistant — studied for assistant-home layout ideas.

The additional repositories above are design/technical references only; no code from them is included by this update.

## BuildItCode/LiquidGlass (evaluated reference; not a dependency)

Repository: https://github.com/BuildItCode/LiquidGlass  
License: MIT License  
Use: Evaluated for its live backdrop capture, AGSL refraction, dispersion and edge-lighting approach. The `com.github.BuildItCode:glass:0.8.0` artifact returned HTTP 401 from JitPack in GitHub Actions, so it was removed from Theo's active dependencies. No source was copied. Theo's dock now uses the already-present Kyant Backdrop engine.



## PrismalAGSL (evaluated reference)

Repository: https://github.com/styropyr0/PrismalAGSL  
Use: Studied for Compose-first AGSL refraction, highlight treatment and API fallback strategy. No PrismalAGSL source or binary dependency is included in Theo by this UI change; no license header is required for code because none is copied from that project.

## Current shared material use

- Kyant0/AndroidLiquidGlass remains Apache-2.0 and is used as a runtime dependency for selected optical glass surfaces.
- BuildItCode/LiquidGlass remains MIT and is used as a runtime dependency for the floating navigation dock.
- Theo uses these public APIs with Theo-specific geometry, spacing and tint settings. No source file from either upstream engine is copied into Theo by this UI change.
