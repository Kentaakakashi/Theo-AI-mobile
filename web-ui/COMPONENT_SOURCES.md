# Component sources and notices

Theo adapts upstream UI work for its own React + Tailwind interface. The native Android bridge, AI provider, and automation logic remain separate from these visual components.

## StarKnightt LiquidGlass

- Source: https://github.com/StarKnightt/liquid-glass
- Local component: `src/components/ui/liquid-glass.tsx`
- Used for the touch-reactive refractive background lens on Theo's empty state.
- License: MIT.

Copyright (c) 2026 Prasenjit (StarKnightt)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## 21st.dev Agent Elements — TextShimmer

- Source: https://github.com/21st-dev/agent-elements/blob/main/lib/agent-ui/components/text-shimmer.tsx
- Local component: `src/components/ui/text-shimmer.tsx`
- Adapted for Theo's own namespaced CSS classes and dark-glass theme.
- License: MIT.

Copyright (c) 2026 21st.dev

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## 21st.dev AI Chat collection — input interaction pattern

- Reference: https://21st.dev/@kokonutd/components/ai-input-with-suggestions
- Creator: Kokonut Baffier / Kokonut UI (MIT).
- Local component: `src/components/ui/ai-prompt-input.tsx`
- The mode chips and adaptive prompt handling are a Theo-specific reimplementation; the community component's source code and demo dependencies are not imported.
