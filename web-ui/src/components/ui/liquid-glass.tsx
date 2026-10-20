"use client"

import { useEffect, useRef } from "react"

/**
 * LiquidGlass — a cursor-following glass lens that refracts, magnifies and
 * distorts the content beneath it. Written from scratch in raw WebGL2.
 * No three.js, no external shader libraries, zero runtime dependencies.
 */

const VERT = `#version 300 es
void main() {
  // Fullscreen triangle from gl_VertexID — no buffers needed.
  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}`

const FRAG = `#version 300 es
precision highp float;
precision highp int;

uniform sampler2D uTex;     // page content (text) rendered to a texture
uniform vec2  uRes;         // drawing buffer size in device px
uniform float uTime;        // seconds
uniform float uDpr;
uniform vec4  uLens;        // xy: lens center (device px), z: radius, w: press scale
uniform vec4  uMotion;      // xy: velocity direction, z: stretch, w: wobble amplitude
uniform float uMag;         // magnification factor inside the lens
uniform float uRefract;     // refraction strength 0..1
uniform float uFrost;       // frosted blur 0..1
uniform float uBgLum;       // background luminance, picks accent blend mode
uniform int   uAccentCount;
uniform vec3  uAccents[4];
uniform int   uRippleCount;
uniform vec4  uRipples[8];  // xy: origin (device px), z: age s, w: amplitude

out vec4 outColor;

float hash21(vec2 p) {
  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// Click ripples: a travelling wave packet that displaces the sampling position.
vec2 rippleOffset(vec2 frag) {
  vec2 off = vec2(0.0);
  for (int i = 0; i < 8; i++) {
    if (i >= uRippleCount) break;
    vec4 rp = uRipples[i];
    vec2 d = frag - rp.xy;
    float dist = max(length(d), 1.0);
    float front = rp.z * 520.0 * uDpr;            // wavefront distance
    float x = (dist - front) / (90.0 * uDpr);     // position inside the packet
    float wave = sin(x * 6.2831) * exp(-x * x * 3.0);
    float atten = exp(-rp.z * 2.2) * exp(-dist / (900.0 * uDpr));
    off += (d / dist) * wave * atten * rp.w * 14.0 * uDpr;
  }
  return off;
}

// Soft pastel blobs drifting behind the content. Multiplied over light
// backgrounds (keeps dark text crisp), screened over dark ones.
vec3 applyAccents(vec3 c, vec2 uv) {
  for (int i = 0; i < 4; i++) {
    if (i >= uAccentCount) break;
    float fi = float(i);
    float t = uTime * 0.11 + fi * 1.7;
    vec2 center = vec2(
      0.5 + 0.38 * sin(t * 0.9 + fi * 2.4),
      0.5 + 0.34 * cos(t * 0.7 + fi * 1.3)
    );
    vec2 d = (uv - center) * vec2(uRes.x / uRes.y, 1.0);
    float a = smoothstep(0.55, 0.0, length(d));
    if (uBgLum > 0.5) {
      c *= mix(vec3(1.0), uAccents[i], a * 0.55);
    } else {
      c = 1.0 - (1.0 - c) * (1.0 - uAccents[i] * a * 0.4);
    }
  }
  return c;
}

vec3 bgAt(vec2 frag, vec2 roff) {
  vec2 uv = clamp((frag + roff) / uRes, 0.0, 1.0);
  return applyAccents(texture(uTex, uv).rgb, uv);
}

void main() {
  vec2 frag = gl_FragCoord.xy;
  vec2 roff = rippleOffset(frag);

  vec2 lensC = uLens.xy;
  float R = uLens.z * uLens.w;

  // Lens-local coordinates, squashed along the motion direction so the
  // droplet elongates when dragged fast.
  vec2 q = frag - lensC;
  vec2 dir = uMotion.xy;
  float stretch = uMotion.z;
  if (stretch > 0.001) {
    vec2 tangent = vec2(dir.y, -dir.x);
    float a = dot(q, dir) / (1.0 + stretch);
    float b = dot(q, tangent) * (1.0 + stretch * 0.5);
    q = dir * a + tangent * b;
  }

  // Organic wobble: low-order sines around the rim, amplitude driven by
  // motion (rings for a moment after the lens stops).
  float ang = atan(q.y, q.x);
  float wob = uMotion.w * (
      0.030 * sin(ang * 3.0 + uTime * 2.1)
    + 0.022 * sin(ang * 5.0 - uTime * 3.3)
    + 0.014 * sin(ang * 7.0 + uTime * 4.7)
  ) + 0.006 * sin(ang * 2.0 + uTime * 0.8);

  float r = length(q);
  float Rw = R * (1.0 + wob);
  float d = r - Rw;
  float aa = 1.5 * uDpr;
  float inside = smoothstep(aa, -aa, d);

  // Soft drop shadow under the lens.
  vec2 shq = frag - (lensC - vec2(0.0, R * 0.22));
  float shadow = smoothstep(R * 1.35, R * 0.55, length(shq)) * 0.10 * (1.0 - inside);

  vec3 col = bgAt(frag, roff) * (1.0 - shadow);

  if (inside > 0.0) {
    // Treat the lens as a glass dome; derive a 3D normal from the SDF.
    float r01 = clamp(r / Rw, 0.0, 1.0);
    float h = sqrt(max(1.0 - r01 * r01, 0.0));
    vec3 N = normalize(vec3(q / Rw, h * 1.15));

    // Magnify toward the center, then bend rays outward near the rim.
    vec2 mfrag = lensC + (frag - lensC) / uMag;
    float bend = 1.0 - h;
    vec2 refr = -N.xy * bend * uRefract * R * 0.9;
    float ca = 0.16 * uRefract; // chromatic aberration spread

    float frostR = uFrost * (0.25 + 0.75 * bend) * 10.0 * uDpr;
    vec2 taps[5] = vec2[5](
      vec2(0.0, 0.0), vec2(1.0, 0.3), vec2(-0.6, 0.9),
      vec2(-0.9, -0.5), vec2(0.5, -0.9)
    );

    vec3 acc = vec3(0.0);
    for (int i = 0; i < 5; i++) {
      vec2 o = taps[i] * frostR;
      acc.r += bgAt(mfrag + refr * (1.0 + ca) + o, roff).r;
      acc.g += bgAt(mfrag + refr + o, roff).g;
      acc.b += bgAt(mfrag + refr * (1.0 - ca) + o, roff).b;
    }
    vec3 glass = acc / 5.0;

    // Glass makes colors pop slightly.
    float lum = dot(glass, vec3(0.299, 0.587, 0.114));
    glass = mix(vec3(lum), glass, 1.18);

    // Lighting: a key light from the top-left.
    vec3 L = normalize(vec3(-0.35, 0.55, 0.75));
    vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
    float ndh = max(dot(N, H), 0.0);
    float spec = pow(ndh, 90.0) * 0.9 + pow(ndh, 18.0) * 0.18;
    float fres = pow(1.0 - max(N.z, 0.0), 2.5);
    float innerShade = smoothstep(0.2, 1.0, dot(normalize(N.xy + 1e-5), normalize(vec2(0.4, -0.8)))) * fres * 0.10;

    glass *= 1.0 - innerShade;
    glass += spec + fres * 0.16;
    glass += smoothstep(aa * 2.5, 0.0, abs(d)) * 0.25;            // bright rim line
    glass += (hash21(frag + uTime) - 0.5) * uFrost * 0.05;        // frost grain

    col = mix(col, glass, inside);
  }

  outColor = vec4(col, 1.0);
}`

export interface LiquidGlassProps {
  /** Main heading lines. Each string renders on its own line. */
  text?: string[]
  /** Small uppercase label above the heading. */
  subText?: string
  /** Caption below the heading. */
  tagline?: string
  /** Canvas background color. Default "#fafafa". */
  backgroundColor?: string
  /** Text color for all type elements. Default "#1d1d1f". */
  textColor?: string
  /** Pastel blobs drifting behind the content. Pass [] to disable. */
  accentColors?: string[]
  /** Lens radius in CSS px. Default 120. */
  lensRadius?: number
  /** Magnification inside the lens. Default 1.12. */
  magnification?: number
  /** Refraction strength 0..1. Default 0.55. */
  refraction?: number
  /** Frosted blur 0..1. Default 0.18. */
  frost?: number
  className?: string
}

const DEFAULT_ACCENTS = ["#bfd7ff", "#ffd6e8", "#ffe9c7", "#c9f2e4"]

function hexToRgb(hex: string): [number, number, number] {
  let h = hex.replace("#", "")
  if (h.length === 3) h = h.split("").map((c) => c + c).join("")
  const n = parseInt(h, 16)
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255]
}

function compileProgram(gl: WebGL2RenderingContext): WebGLProgram | null {
  const make = (type: number, src: string) => {
    const s = gl.createShader(type)
    if (!s) return null
    gl.shaderSource(s, src)
    gl.compileShader(s)
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
      console.error("LiquidGlass shader error:", gl.getShaderInfoLog(s))
      gl.deleteShader(s)
      return null
    }
    return s
  }
  const vs = make(gl.VERTEX_SHADER, VERT)
  const fs = make(gl.FRAGMENT_SHADER, FRAG)
  if (!vs || !fs) return null
  const prog = gl.createProgram()
  if (!prog) return null
  gl.attachShader(prog, vs)
  gl.attachShader(prog, fs)
  gl.linkProgram(prog)
  gl.deleteShader(vs)
  gl.deleteShader(fs)
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
    console.error("LiquidGlass link error:", gl.getProgramInfoLog(prog))
    gl.deleteProgram(prog)
    return null
  }
  return prog
}

export function LiquidGlass({
  text,
  subText,
  tagline,
  backgroundColor = "#fafafa",
  textColor = "#1d1d1f",
  accentColors = DEFAULT_ACCENTS,
  lensRadius = 120,
  magnification = 1.12,
  refraction = 0.55,
  frost = 0.18,
  className,
}: LiquidGlassProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const accentsKey = accentColors.join(",")

  useEffect(() => {
    const container = containerRef.current
    const canvas = canvasRef.current
    if (!container || !canvas) return

    const accents = accentColors.slice(0, 4).map(hexToRgb)
    const bgRgb = hexToRgb(backgroundColor)
    const bgLum = 0.299 * bgRgb[0] + 0.587 * bgRgb[1] + 0.114 * bgRgb[2]
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches

    // ---- Render the typography into an offscreen canvas (the "page") ----
    const textCanvas = document.createElement("canvas")
    const fontStack = `-apple-system, "SF Pro Display", "Segoe UI", "Helvetica Neue", Arial, sans-serif`

    const paintText = (w: number, h: number, dpr: number) => {
      textCanvas.width = Math.max(1, Math.round(w * dpr))
      textCanvas.height = Math.max(1, Math.round(h * dpr))
      const ctx = textCanvas.getContext("2d")
      if (!ctx) return
      ctx.scale(dpr, dpr)

      ctx.fillStyle = backgroundColor
      ctx.fillRect(0, 0, w, h)

      // Soft ambient light so the glass has something to reflect (light themes only).
      if (bgLum > 0.5) {
        ctx.globalCompositeOperation = "multiply"
        const glow1 = ctx.createRadialGradient(w * 0.5, h * 0.08, 0, w * 0.5, h * 0.08, w * 0.6)
        glow1.addColorStop(0, "rgba(222, 228, 242, 0.55)")
        glow1.addColorStop(1, "rgba(250, 250, 250, 1)")
        ctx.fillStyle = glow1
        ctx.fillRect(0, 0, w, h)
        const glow2 = ctx.createRadialGradient(w * 0.5, h * 0.95, 0, w * 0.5, h * 0.95, w * 0.5)
        glow2.addColorStop(0, "rgba(243, 234, 225, 0.4)")
        glow2.addColorStop(1, "rgba(250, 250, 250, 1)")
        ctx.fillStyle = glow2
        ctx.fillRect(0, 0, w, h)
        ctx.globalCompositeOperation = "source-over"
      }

      if (subText) {
        ctx.fillStyle = textColor
        ctx.globalAlpha = 0.35
        const subSize = Math.max(11, w * 0.009)
        ctx.font = `600 ${subSize}px ${fontStack}`
        ctx.textAlign = "center"
        ctx.letterSpacing = "0.25em"
        ctx.fillText(subText.toUpperCase(), w / 2, h / 2 - w * 0.095)
      }

      const fontSize = Math.min(w * 0.13, h * 0.19)
      ctx.globalAlpha = 1
      ctx.letterSpacing = "-0.04em"
      ctx.font = `700 ${fontSize}px ${fontStack}`
      ctx.textAlign = "center"
      ctx.textBaseline = "middle"

      const lineHeight = fontSize * 1.08
      const lines = text ?? []
      const startY = h / 2 - (lines.length * lineHeight) / 2 + lineHeight / 2
      lines.forEach((line, i) => {
        ctx.fillStyle = textColor
        ctx.fillText(line, w / 2, startY + i * lineHeight)
      })

      const dividerY = startY + lines.length * lineHeight + w * 0.018
      if (lines.length > 0 && tagline) {
        ctx.globalAlpha = 0.12
        ctx.fillRect(w / 2 - 30, dividerY, 60, 0.5)
      }
      if (tagline) {
        ctx.globalAlpha = 0.3
        ctx.letterSpacing = "0.02em"
        const tagSize = Math.max(11, w * 0.01)
        ctx.font = `400 ${tagSize}px ${fontStack}`
        ctx.fillText(tagline, w / 2, dividerY + w * 0.025)
      }
      ctx.globalAlpha = 1
    }

    // ---- WebGL2 setup ----
    const gl = canvas.getContext("webgl2", {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      powerPreference: "high-performance",
    })

    // Graceful fallback: show the static typography if WebGL2 is unavailable.
    if (!gl) {
      const rect = container.getBoundingClientRect()
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      paintText(rect.width, rect.height, dpr)
      canvas.width = textCanvas.width
      canvas.height = textCanvas.height
      canvas.getContext("2d")?.drawImage(textCanvas, 0, 0)
      return
    }

    const program = compileProgram(gl)
    if (!program) return
    gl.useProgram(program)

    const loc = (name: string) => gl.getUniformLocation(program, name)
    const uTex = loc("uTex")
    const uRes = loc("uRes")
    const uTime = loc("uTime")
    const uDpr = loc("uDpr")
    const uLens = loc("uLens")
    const uMotion = loc("uMotion")
    const uMag = loc("uMag")
    const uRefract = loc("uRefract")
    const uFrost = loc("uFrost")
    const uBgLum = loc("uBgLum")
    const uAccentCount = loc("uAccentCount")
    const uAccents = loc("uAccents[0]")
    const uRippleCount = loc("uRippleCount")
    const uRipples = loc("uRipples[0]")

    const texture = gl.createTexture()
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, texture)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
    gl.uniform1i(uTex, 0)

    // Static uniforms
    gl.uniform1f(uMag, magnification)
    gl.uniform1f(uRefract, refraction)
    gl.uniform1f(uFrost, frost)
    gl.uniform1f(uBgLum, bgLum)
    gl.uniform1i(uAccentCount, accents.length)
    const accentData = new Float32Array(12)
    accents.forEach((c, i) => accentData.set(c, i * 3))
    gl.uniform3fv(uAccents, accentData)

    let dpr = 1
    const uploadTexture = () => {
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true)
      gl.bindTexture(gl.TEXTURE_2D, texture)
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, textCanvas)
      gl.generateMipmap(gl.TEXTURE_2D)
    }

    const resize = () => {
      const rect = container.getBoundingClientRect()
      dpr = Math.min(window.devicePixelRatio || 1, 2)
      canvas.width = Math.max(1, Math.round(rect.width * dpr))
      canvas.height = Math.max(1, Math.round(rect.height * dpr))
      gl.viewport(0, 0, canvas.width, canvas.height)
      paintText(rect.width, rect.height, dpr)
      uploadTexture()
    }
    resize()

    let resizeTimer = 0
    const ro = new ResizeObserver(() => {
      window.clearTimeout(resizeTimer)
      resizeTimer = window.setTimeout(resize, 150)
    })
    ro.observe(container)

    // ---- Simulation state (device px, origin bottom-left like gl_FragCoord) ----
    let px = canvas.width / 2
    let py = canvas.height / 2
    let vx = 0
    let vy = 0
    let tx = px
    let ty = py
    let dirX = 0
    let dirY = 1
    let stretch = 0
    let wobble = 0
    let wobbleVel = 0
    let pressT0 = -10
    let lastMove = -10
    let pointerActive = false
    let idleT = Math.random() * 100
    let lastAutoRipple = 0
    const ripples: { x: number; y: number; t0: number; amp: number }[] = []
    const rippleData = new Float32Array(8 * 4)

    const toGl = (e: PointerEvent): [number, number] => {
      const rect = canvas.getBoundingClientRect()
      const sx = canvas.width / Math.max(rect.width, 1)
      const sy = canvas.height / Math.max(rect.height, 1)
      return [(e.clientX - rect.left) * sx, (rect.bottom - e.clientY) * sy]
    }

    let now = performance.now() / 1000
    const t0 = now

    const onMove = (e: PointerEvent) => {
      const [x, y] = toGl(e)
      tx = x
      ty = y
      pointerActive = true
      lastMove = performance.now() / 1000
    }
    const onDown = (e: PointerEvent) => {
      const [x, y] = toGl(e)
      tx = x
      ty = y
      pointerActive = true
      lastMove = performance.now() / 1000
      pressT0 = lastMove
      ripples.push({ x, y, t0: lastMove, amp: 1 })
      if (ripples.length > 8) ripples.shift()
    }
    const onLeave = () => {
      pointerActive = false
    }

    canvas.addEventListener("pointermove", onMove)
    canvas.addEventListener("pointerdown", onDown)
    canvas.addEventListener("pointerleave", onLeave)

    // ---- Frame loop ----
    let raf = 0
    let prev = now
    const frame = () => {
      now = performance.now() / 1000
      const dt = Math.min(Math.max(now - prev, 0.001), 0.05)
      prev = now
      const time = now - t0

      // Idle: drift on a slow Lissajous path and emit gentle ripples so the
      // scene stays alive without input (great for screen recordings).
      const idle = !pointerActive || now - lastMove > 2.6
      if (idle && !reducedMotion) {
        idleT += dt
        tx = canvas.width * (0.5 + 0.24 * Math.sin(idleT * 0.43))
        ty = canvas.height * (0.52 + 0.16 * Math.sin(idleT * 0.61 + 1.3))
        if (now - lastAutoRipple > 4.5) {
          lastAutoRipple = now
          ripples.push({ x: px, y: py, t0: now, amp: 0.5 })
          if (ripples.length > 8) ripples.shift()
        }
      }

      // Spring-follow the target: slightly underdamped for a liquid lag.
      vx += (tx - px) * 130 * dt
      vy += (ty - py) * 130 * dt
      const damp = Math.exp(-14 * dt)
      vx *= damp
      vy *= damp
      px += vx * dt
      py += vy * dt

      const speed = Math.hypot(vx, vy)
      if (speed > 1) {
        dirX = vx / speed
        dirY = vy / speed
      }
      const stretchTarget = Math.min(speed / 4500, 0.42)
      stretch += (stretchTarget - stretch) * (1 - Math.exp(-12 * dt))

      // Underdamped wobble amplitude: rings after the lens stops moving.
      const wobbleTarget = reducedMotion ? 0 : Math.min(speed / 2200, 1)
      wobbleVel += (wobbleTarget - wobble) * 40 * dt
      wobbleVel *= Math.exp(-4.2 * dt)
      wobble += wobbleVel * dt
      wobble = Math.max(0, Math.min(wobble, 1.2))

      // Press bounce on click.
      const pressAge = now - pressT0
      const press =
        pressAge < 1.2
          ? 1 - 0.16 * Math.exp(-pressAge * 9) * Math.cos(pressAge * 16)
          : 1

      // Prune and pack ripples.
      for (let i = ripples.length - 1; i >= 0; i--) {
        if (now - ripples[i].t0 > 3) ripples.splice(i, 1)
      }
      ripples.forEach((rp, i) => {
        rippleData[i * 4] = rp.x
        rippleData[i * 4 + 1] = rp.y
        rippleData[i * 4 + 2] = now - rp.t0
        rippleData[i * 4 + 3] = rp.amp
      })

      gl.uniform2f(uRes, canvas.width, canvas.height)
      gl.uniform1f(uTime, time)
      gl.uniform1f(uDpr, dpr)
      gl.uniform4f(uLens, px, py, lensRadius * dpr, press)
      gl.uniform4f(uMotion, dirX, dirY, stretch, wobble)
      gl.uniform1i(uRippleCount, ripples.length)
      gl.uniform4fv(uRipples, rippleData)

      gl.drawArrays(gl.TRIANGLES, 0, 3)
      raf = requestAnimationFrame(frame)
    }
    raf = requestAnimationFrame(frame)

    return () => {
      cancelAnimationFrame(raf)
      window.clearTimeout(resizeTimer)
      ro.disconnect()
      canvas.removeEventListener("pointermove", onMove)
      canvas.removeEventListener("pointerdown", onDown)
      canvas.removeEventListener("pointerleave", onLeave)
      gl.deleteTexture(texture)
      gl.deleteProgram(program)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    text?.join("\n"),
    subText,
    tagline,
    backgroundColor,
    textColor,
    accentsKey,
    lensRadius,
    magnification,
    refraction,
    frost,
  ])

  return (
    <div
      ref={containerRef}
      className={"relative h-full w-full overflow-hidden " + (className ?? "")}
    >
      <canvas
        ref={canvasRef}
        className="absolute inset-0 h-full w-full touch-none select-none"
        aria-hidden
      />
    </div>
  )
}
