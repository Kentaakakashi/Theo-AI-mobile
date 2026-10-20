import { useEffect, useRef, useState } from 'react'
import { ArrowUp, AudioLines, Check, ChevronDown, CircleAlert, Command, Mic, Plus, Settings2, Sparkles, Square, Volume2, WifiOff, X } from 'lucide-react'

type Message = { id: string; text: string; isUser: boolean; modelBadge?: string; timestamp?: number; actionTag?: string | null }
type TheoState = 'IDLE' | 'LISTENING' | 'THINKING' | 'SPEAKING' | 'OFFLINE' | 'ERROR'
declare global { interface Window { TheoBridge?: { sendMessage: (...args: string[]) => void; setInputText: (...args: string[]) => void; orbClick: (...args: string[]) => void; toggleSpeech: (...args: string[]) => void; openSettings: (...args: string[]) => void; executeAction: (...args: string[]) => void; dismissAction: (...args: string[]) => void; selectApp: (...args: string[]) => void; cancelAppSelection: (...args: string[]) => void }; theoUpdate?: (data: Partial<NativeState>) => void } }
type NativeState = { state: TheoState; model: string; input: string; streamingText: string; speechEnabled: boolean; messages: Message[]; errorText?: string; pendingAction?: string | null; pendingActionLabel?: string | null; pendingAppName?: string | null; pendingAppCandidates?: {label:string;packageName:string}[] }

const initial: NativeState = { state: 'IDLE', model: 'GEMINI FAST', input: '', streamingText: '', speechEnabled: true, messages: [], pendingAction: null, pendingAppName: null, pendingAppCandidates: [] }
const states: Record<TheoState, { label: string; hint: string }> = {
  IDLE: { label: 'Ready when you are', hint: 'Ask me anything or give me a task' },
  LISTENING: { label: 'Listening', hint: 'Go ahead, I’m listening' },
  THINKING: { label: 'Thinking', hint: 'Connecting the dots…' },
  SPEAKING: { label: 'Speaking', hint: 'Theo is talking' },
  OFFLINE: { label: 'Offline mode', hint: 'Check your active AI provider' },
  ERROR: { label: 'Something went wrong', hint: 'Try again in a moment' },
}
const suggestions = [
  { title: 'Help me think', text: 'Help me think through an idea' },
  { title: 'Get things done', text: 'Help me plan my day' },
  { title: 'Explain something', text: 'Explain a difficult concept from the basics' },
]

function ParticleOrb({ state, onClick }: { state: TheoState; onClick: () => void }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    const element = canvas.current
    if (!element) return
    const ctx = element.getContext('2d')
    if (!ctx) return
    let frame = 0
    let raf = 0
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const palette: Record<TheoState, [string, string]> = {
      IDLE: ['#9bdcff', '#a78bfa'], LISTENING: ['#e6faff', '#39d9ff'],
      THINKING: ['#a78bfa', '#f472d0'], SPEAKING: ['#38e4ee', '#a5ddff'],
      OFFLINE: ['#fbbf24', '#fb907e'], ERROR: ['#fb7185', '#ff3f69'],
    }
    const particles = Array.from({ length: 380 }, (_, i) => {
      const y = 1 - (i / 379) * 2
      const radius = Math.sqrt(1 - y * y)
      const theta = Math.PI * (3 - Math.sqrt(5)) * i
      return { x: Math.cos(theta) * radius, y, z: Math.sin(theta) * radius, p: i * 2.399, size: .7 + (i % 5) * .18 }
    })
    const draw = () => {
      const rect = element.getBoundingClientRect()
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      const w = Math.max(1, rect.width), h = Math.max(1, rect.height)
      if (element.width !== Math.round(w * dpr) || element.height !== Math.round(h * dpr)) {
        element.width = Math.round(w * dpr); element.height = Math.round(h * dpr)
      }
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, w, h)
      const [c1, c2] = palette[state]
      const t = reduced ? .4 : frame * (state === 'ERROR' ? .024 : state === 'SPEAKING' ? .013 : state === 'THINKING' ? .009 : .0045)
      const cx = w / 2, cy = h / 2
      const r = Math.min(w, h) * (.34 + (state === 'ERROR' ? .018 * Math.sin(t * 2.5) : state === 'LISTENING' ? .018 * Math.sin(t * 1.7) : .009 * Math.sin(t)))
      const glow = ctx.createRadialGradient(cx, cy, 1, cx, cy, r * 1.8)
      glow.addColorStop(0, c1 + '24'); glow.addColorStop(.5, c2 + '10'); glow.addColorStop(1, '#00000000')
      ctx.fillStyle = glow; ctx.beginPath(); ctx.arc(cx, cy, r * 1.8, 0, Math.PI * 2); ctx.fill()
      if (state === 'LISTENING' || state === 'ERROR') {
        ctx.strokeStyle = c1 + (state === 'ERROR' ? '55' : '28'); ctx.lineWidth = 1
        for (let ring = 1; ring <= (state === 'ERROR' ? 3 : 2); ring++) {
          const rr = r * (1.12 + ((t * (state === 'ERROR' ? 1.3 : .6) + ring * .43) % .38))
          ctx.beginPath(); ctx.arc(cx, cy, rr, 0, Math.PI * 2); ctx.stroke()
        }
      }
      for (let pass = 0; pass < 2; pass++) for (const p of particles) {
        const x = p.x * Math.cos(t) - p.z * Math.sin(t)
        const z = p.x * Math.sin(t) + p.z * Math.cos(t)
        const depth = (z + 1) / 2
        if ((pass === 0 && depth > .52) || (pass === 1 && depth <= .52)) continue
        const irregular = state === 'ERROR' ? .035 * Math.sin(p.p + t * 2.8) : 0
        const spread = state === 'OFFLINE' ? .035 : 0
        const px = cx + x * r * (1 + irregular + spread) * (.76 + depth * .34)
        const py = cy + p.y * r * (1 + irregular + spread) * (.76 + depth * .34)
        const alpha = .15 + depth * .76
        ctx.globalAlpha = alpha
        ctx.fillStyle = (p.p % 5 > 2.4 ? c2 : c1)
        ctx.beginPath(); ctx.arc(px, py, p.size * (.5 + depth * .75), 0, Math.PI * 2); ctx.fill()
      }
      ctx.globalAlpha = 1
      if (state === 'SPEAKING' || state === 'LISTENING') {
        const a = t * (state === 'SPEAKING' ? 1.8 : .8)
        const gx = cx + Math.cos(a) * r * 1.13, gy = cy + Math.sin(a) * r * .5
        const g = ctx.createRadialGradient(gx, gy, 0, gx, gy, 12)
        g.addColorStop(0, '#ffffffdd'); g.addColorStop(1, c1 + '00')
        ctx.fillStyle = g; ctx.beginPath(); ctx.arc(gx, gy, 12, 0, Math.PI * 2); ctx.fill()
      }
      frame++
      if (!reduced) raf = requestAnimationFrame(draw)
    }
    draw()
    return () => cancelAnimationFrame(raf)
  }, [state])
  return <button aria-label="Talk to Theo" onClick={onClick} className="relative mx-auto block h-56 w-56 max-w-full rounded-full outline-none active:scale-[.98] transition-transform"><canvas ref={canvas} className="h-full w-full" /><span className="pointer-events-none absolute inset-x-0 bottom-1 text-center text-[10px] tracking-[.28em] text-white/30">T H E O</span></button>
}

function callNative(method: keyof NonNullable<Window['TheoBridge']>, ...args: string[]) {
  try { window.TheoBridge?.[method]?.(...args) } catch { /* native bridge may not be ready during preview */ }
}

export default function App() {
  const [native, setNative] = useState<NativeState>(initial)
  const [draft, setDraft] = useState('')
  const [showStatus, setShowStatus] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    window.theoUpdate = data => {
      setNative(prev => ({ ...prev, ...data }))
      if (typeof data.input === 'string') setDraft(data.input)
    }
    return () => { delete window.theoUpdate }
  }, [])
  useEffect(() => { if (native.messages.length || native.streamingText) listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' }) }, [native.messages, native.streamingText])
  const submit = (text = draft) => {
    const value = text.trim()
    if (!value || native.state === 'THINKING') return
    callNative('sendMessage', value)
    setDraft('')
  }
  const active = states[native.state] ?? states.IDLE
  const hasChat = native.messages.length > 0 || !!native.streamingText
  const stateIcon = native.state === 'ERROR' ? <CircleAlert size={14} /> : native.state === 'OFFLINE' ? <WifiOff size={14} /> : native.state === 'SPEAKING' ? <AudioLines size={14} /> : <span className={native.state === 'THINKING' || native.state === 'LISTENING' ? 'h-1.5 w-1.5 rounded-full bg-cyan-300 animate-pulse' : 'h-1.5 w-1.5 rounded-full bg-emerald-300'} />
  return <main className="safe-top safe-bottom relative flex h-full flex-col px-4 text-white">
    <header className="flex shrink-0 items-center justify-between py-3">
      <button onClick={() => setShowStatus(v => !v)} className="flex min-w-0 items-center gap-3 text-left">
        <span className="glass flex h-10 w-10 items-center justify-center rounded-[15px] text-cyan-100"><Sparkles size={19} /></span>
        <span><span className="block text-[16px] font-semibold tracking-[-.035em]">Theo</span><span className="mt-0.5 flex items-center gap-1.5 text-[10px] tracking-[.12em] text-white/45">{stateIcon}{active.label.toUpperCase()}</span></span>
      </button>
      <div className="flex items-center gap-2">
        <button onClick={() => callNative('toggleSpeech')} className={'glass flex h-10 w-10 items-center justify-center rounded-full '+(native.speechEnabled ? 'text-cyan-100' : 'text-white/40')} aria-label="Toggle voice"><Volume2 size={17}/></button>
        <button onClick={() => callNative('openSettings')} className="glass flex h-10 w-10 items-center justify-center rounded-full text-white/75" aria-label="Settings"><Settings2 size={17}/></button>
      </div>
    </header>
    {showStatus && <button onClick={() => setShowStatus(false)} className="glass absolute left-4 right-4 top-16 z-20 rounded-2xl p-4 text-left"><div className="mb-1 flex items-center justify-between text-sm font-medium">Current status <X size={15}/></div><p className="text-xs text-white/50">{active.hint}</p><p className="mt-2 text-[10px] text-cyan-200/70">{native.model}</p></button>}
    {!hasChat ? <section className="flex min-h-0 flex-1 flex-col items-center justify-center pb-4 text-center">
      <div className="mb-2 text-[10px] font-medium tracking-[.32em] text-cyan-100/45">YOUR AI, YOUR SPACE</div>
      <ParticleOrb state={native.state} onClick={() => callNative('orbClick')} />
      <h1 className="mt-1 text-[27px] font-medium tracking-[-.055em]">What’s on your mind?</h1>
      <p className="mt-2 max-w-[270px] text-[13px] leading-5 text-white/45">{active.hint}</p>
      {native.state === 'ERROR' && <div className="mt-3 flex items-center gap-2 rounded-xl border border-rose-300/20 bg-rose-400/10 px-3 py-2 text-xs text-rose-100"><CircleAlert size={14}/>{native.errorText || 'Something went wrong. Try again.'}</div>}
      <div className="mt-6 grid w-full max-w-md grid-cols-1 gap-2">
        {suggestions.map((s, i) => <button key={s.title} onClick={() => submit(s.text)} className="glass group flex items-center justify-between rounded-2xl px-4 py-3 text-left transition-colors hover:bg-white/[.07]"><span className="flex items-center gap-3"><span className="flex h-8 w-8 items-center justify-center rounded-xl bg-white/[.055] text-cyan-100/75">{i === 0 ? <Sparkles size={15}/> : i === 1 ? <Command size={15}/> : <AudioLines size={15}/>}</span><span><span className="block text-[12px] font-medium">{s.title}</span><span className="mt-0.5 block text-[10px] text-white/40">{s.text}</span></span></span><Plus size={15} className="text-white/35 transition-transform group-hover:rotate-90"/></button>)}
      </div>
    </section> : <section ref={listRef} className="scrollbar-hide min-h-0 flex-1 space-y-5 overflow-y-auto py-5">
      <div className="mb-3 flex items-center gap-2 text-[10px] uppercase tracking-[.22em] text-white/35"><span className="h-px flex-1 bg-white/10"/><span>Conversation</span><span className="h-px flex-1 bg-white/10"/></div>
      {native.messages.map((m, i) => <article key={m.id || i} className={m.isUser ? 'ml-8' : 'mr-3'}>
        {m.isUser ? <div className="ml-auto w-fit max-w-full rounded-[22px] rounded-br-md border border-white/10 bg-white/[.085] px-4 py-3 text-[14px] leading-6">{m.text}</div> : <div className="py-1"><div className="mb-2 flex items-center gap-2 text-[10px] font-medium tracking-wide text-cyan-100/65"><Sparkles size={13}/> THEO <span className="text-white/25">{m.modelBadge || ''}</span></div><div className="whitespace-pre-wrap break-words text-[14px] leading-6 text-white/85">{m.text}</div></div>}
      </article>)}
      {!!native.streamingText && <div className="mr-3 py-1"><div className="mb-2 flex items-center gap-2 text-[10px] text-cyan-100/65"><AudioLines size={13}/> THEO IS REPLYING</div><p className="whitespace-pre-wrap text-[14px] leading-6 text-white/85">{native.streamingText}<span className="ml-1 inline-block h-4 w-1 animate-pulse bg-cyan-200/80 align-middle"/></p></div>}
      {native.state === 'ERROR' && <div className="glass flex items-start gap-3 rounded-2xl border-rose-300/20 p-3 text-sm text-rose-100"><CircleAlert size={17} className="mt-0.5 shrink-0"/><div className="flex-1"><p className="font-medium">That didn’t work</p><p className="mt-1 text-xs text-rose-100/60">{native.errorText || 'Theo hit a problem while processing that request.'}</p></div><button onClick={() => submit(native.messages.filter(m => m.isUser).at(-1)?.text || draft)} className="text-xs underline underline-offset-4">Retry</button></div>}
    </section>}
    {!!native.pendingAction && <div className="absolute inset-0 z-30 flex items-center justify-center bg-black/55 p-5 backdrop-blur-md"><section className="glass w-full max-w-sm rounded-[28px] p-5"><div className="mb-3 flex items-center gap-2 text-cyan-100"><Command size={18}/><h2 className="text-base font-semibold">Review before continuing</h2></div><p className="text-sm leading-6 text-white/65">Theo is asking permission to perform an action. Review the request before it continues.</p><div className="my-4 break-words rounded-2xl border border-white/10 bg-black/20 p-3 font-mono text-xs text-cyan-100/90">{native.pendingActionLabel || native.pendingAction}</div><div className="flex gap-2"><button onClick={() => callNative('dismissAction')} className="flex-1 rounded-full border border-white/10 px-4 py-3 text-sm text-white/65">Cancel</button><button onClick={() => callNative('executeAction', native.pendingAction!)} className="flex-1 rounded-full bg-cyan-100 px-4 py-3 text-sm font-semibold text-slate-950">Confirm and run</button></div></section></div>}
    {!!native.pendingAppName && <div className="absolute inset-0 z-30 flex items-center justify-center bg-black/55 p-5 backdrop-blur-md"><section className="glass w-full max-w-sm rounded-[28px] p-5"><h2 className="text-base font-semibold">Choose an app</h2><p className="my-2 text-sm leading-6 text-white/60">Which installed app did you mean by “{native.pendingAppName}”?</p><div className="space-y-2">{(native.pendingAppCandidates || []).map(app => <button key={app.packageName} onClick={() => callNative('selectApp', app.packageName)} className="glass block w-full rounded-2xl p-3 text-left"><span className="block text-sm font-medium">{app.label}</span><span className="mt-1 block text-[10px] text-white/40">{app.packageName}</span></button>)}</div><button onClick={() => callNative('cancelAppSelection')} className="mt-3 w-full rounded-full px-4 py-3 text-sm text-white/55">Cancel</button></section></div>}
    <footer className="shrink-0 pb-2 pt-3">
      <div className="glass rounded-[26px] p-2">
        <textarea value={draft} onChange={e => { setDraft(e.target.value); callNative('setInputText', e.target.value) }} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit() } }} rows={1} placeholder="Message Theo…" className="max-h-32 min-h-11 w-full resize-none bg-transparent px-3 py-3 text-[14px] leading-5 text-white outline-none placeholder:text-white/30" />
        <div className="flex items-center justify-between px-1 pb-1">
          <div className="flex items-center gap-1"><button onClick={() => callNative('orbClick')} className="flex h-9 items-center gap-2 rounded-full px-3 text-white/50 transition-colors hover:bg-white/[.06]"><Mic size={16}/><span className="text-[11px]">Voice</span></button><span className="hidden items-center gap-1 text-[10px] text-white/25 sm:flex"><ChevronDown size={12}/>{native.model}</span></div>
          <button onClick={() => submit()} disabled={!draft.trim() || native.state === 'THINKING'} aria-label="Send message" className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-cyan-200 to-blue-300 text-slate-950 shadow-[0_2px_22px_rgba(56,189,248,.18)] transition-all disabled:opacity-30">{native.state === 'THINKING' ? <Square size={14}/> : <ArrowUp size={18}/>}</button>
        </div>
      </div>
      <p className="pt-2 text-center text-[9px] tracking-wide text-white/25">Theo can make mistakes. Verify important actions.</p>
    </footer>
  </main>
}
