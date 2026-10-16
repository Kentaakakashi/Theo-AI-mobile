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
