import { useEffect, useRef, useState } from 'react'
import { ArrowUp, AudioLines, ChevronDown, CircleAlert, Command, Mic, Plus, Settings2, Sparkles, Square, Volume2, WifiOff, X } from 'lucide-react'
import { ThinkingOrb } from 'thinking-orbs'
import { LiquidGlass } from './components/ui/liquid-glass'

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
  const orbState = state === 'LISTENING' ? 'listening' : state === 'THINKING' ? 'working' : state === 'SPEAKING' ? 'composing' : state === 'OFFLINE' ? 'connecting' : 'breathing'
  const error = state === 'ERROR'
  const speed = state === 'THINKING' ? 1.35 : state === 'SPEAKING' ? 1.2 : state === 'OFFLINE' ? 0.65 : 1
  return <button aria-label={error ? 'Theo error state' : 'Talk to Theo'} onClick={onClick} className="relative mx-auto flex h-56 w-56 max-w-full items-center justify-center rounded-full outline-none transition-transform active:scale-[.98]">
    <span className={`pointer-events-none absolute inset-2 rounded-full blur-2xl ${error ? 'bg-rose-500/15' : state === 'OFFLINE' ? 'bg-amber-300/10' : 'bg-cyan-300/[.08]'}`} />
    <span className={`pointer-events-none absolute inset-[18%] rounded-full border ${error ? 'border-rose-300/30 animate-pulse motion-reduce:animate-none' : state === 'LISTENING' ? 'border-cyan-200/20 animate-pulse motion-reduce:animate-none' : 'border-white/[.07]'}`} />
    <span className={`pointer-events-none absolute inset-[25%] rounded-full border ${error ? 'border-rose-300/20' : 'border-white/[.05]'}`} />
    <ThinkingOrb state={orbState} size={64} theme="dark" speed={speed} aria-label={states[state].label} className="h-16 w-16 scale-[2.45]" />
    {error && <span className="pointer-events-none absolute right-[18%] top-[19%] flex h-7 w-7 items-center justify-center rounded-full border border-rose-200/30 bg-[#260d19]/90 text-rose-200 shadow-[0_0_25px_rgba(251,63,105,.28)]"><CircleAlert size={15}/></span>}
    <span className="pointer-events-none absolute inset-x-0 bottom-1 text-center text-[10px] tracking-[.28em] text-white/30">T H E O</span>
  </button>
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
  const orbState = native.state === 'LISTENING' ? 'listening' : native.state === 'THINKING' ? 'working' : native.state === 'SPEAKING' ? 'composing' : native.state === 'OFFLINE' ? 'connecting' : 'breathing'
  const stateIcon = native.state === 'ERROR' ? <CircleAlert size={14} className="text-rose-300" /> : native.state === 'OFFLINE' ? <WifiOff size={14} /> : <ThinkingOrb state={orbState} size={64} theme="dark" speed={native.state === 'THINKING' ? 1.35 : 1} aria-label={active.label} className="h-5 w-5 shrink-0" />
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
    {!hasChat ? <section className="relative isolate flex min-h-0 flex-1 flex-col items-center justify-center overflow-hidden rounded-[30px] pb-4 text-center">
      <div className="absolute inset-0 opacity-80" aria-hidden="true"><LiquidGlass text={[]} backgroundColor="#080a12" textColor="#ffffff" accentColors={native.state === "ERROR" ? ["#ff5876", "#7d183b", "#ffd1da"] : native.state === "OFFLINE" ? ["#f5b942", "#6b4b1e", "#d6a96a"] : native.state === "LISTENING" ? ["#5ce8ff", "#4387ff", "#c4f7ff"] : native.state === "THINKING" ? ["#7b7cff", "#39d8ff", "#bda7ff"] : native.state === "SPEAKING" ? ["#54f0d0", "#59b6ff", "#c7fff2"] : ["#3a9cff", "#7356d9", "#76e6ef"]} lensRadius={88} magnification={1.12} refraction={0.62} frost={0.12} /></div>
      <div className="relative z-10 flex min-h-0 w-full flex-col items-center justify-center px-1">
      <div className="mb-2 text-[10px] font-medium tracking-[.32em] text-cyan-100/45">YOUR AI, YOUR SPACE</div>
      <ParticleOrb state={native.state} onClick={() => callNative('orbClick')} />
      <h1 className="mt-1 text-[27px] font-medium tracking-[-.055em]">What’s on your mind?</h1>
      <p className="mt-2 max-w-[270px] text-[13px] leading-5 text-white/45">{active.hint}</p>
      {native.state === 'ERROR' && <div className="mt-3 flex items-center gap-2 rounded-xl border border-rose-300/20 bg-rose-400/10 px-3 py-2 text-xs text-rose-100"><CircleAlert size={14}/>{native.errorText || 'Something went wrong. Try again.'}</div>}
      <div className="mt-6 grid w-full max-w-md grid-cols-1 gap-2">
        {suggestions.map((s, i) => <button key={s.title} onClick={() => submit(s.text)} className="glass group flex items-center justify-between rounded-2xl px-4 py-3 text-left transition-colors hover:bg-white/[.07]"><span className="flex items-center gap-3"><span className="flex h-8 w-8 items-center justify-center rounded-xl bg-white/[.055] text-cyan-100/75">{i === 0 ? <Sparkles size={15}/> : i === 1 ? <Command size={15}/> : <AudioLines size={15}/>}</span><span><span className="block text-[12px] font-medium">{s.title}</span><span className="mt-0.5 block text-[10px] text-white/40">{s.text}</span></span></span><Plus size={15} className="text-white/35 transition-transform group-hover:rotate-90"/></button>)}
      </div>
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
