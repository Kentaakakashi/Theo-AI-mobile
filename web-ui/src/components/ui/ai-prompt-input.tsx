import { useEffect, useRef, useState } from 'react'
import { ArrowUp, AudioLines, BookOpen, Code2, LoaderCircle, MessageCircle, Route } from 'lucide-react'

type PromptMode = 'ASK' | 'PLAN' | 'LEARN' | 'CODE'

type Props = {
  value: string
  model: string
  busy: boolean
  speechEnabled: boolean
  onChange: (value: string) => void
  onSend: (value: string) => void
  onVoice: () => void
}

// Composer interaction patterns adapted for Theo's native bridge from the
// 21st.dev AI Chat collection's "AI Input with Suggestions" by Kokonut Baffier.
// Reference: https://21st.dev/@kokonutd/components/ai-input-with-suggestions
// This component is reimplemented for Theo instead of importing its demo dependencies.
const modes: { id: PromptMode; label: string; icon: typeof MessageCircle; hint: string }[] = [
  { id: 'ASK', label: 'Ask', icon: MessageCircle, hint: 'Natural conversation' },
  { id: 'PLAN', label: 'Plan', icon: Route, hint: 'A practical step-by-step plan' },
  { id: 'LEARN', label: 'Learn', icon: BookOpen, hint: 'Start from absolute basics' },
  { id: 'CODE', label: 'Code', icon: Code2, hint: 'Help with a coding task' },
]

function preparePrompt(mode: PromptMode, input: string) {
  if (mode === 'PLAN') return 'Make me a practical step-by-step plan for this request. Include priorities, likely obstacles, and the best first action.\n\n' + input
  if (mode === 'LEARN') return 'Teach me this from absolute zero in simple language. Build up the concepts step by step, use examples, and finish by checking my understanding.\n\n' + input
  if (mode === 'CODE') return 'Help me solve this coding task step by step. Give complete, runnable code where appropriate and explain exactly where it belongs.\n\n' + input
  return input
}

export function AIPromptInput({ value, model, busy, speechEnabled, onChange, onSend, onVoice }: Props) {
  const [mode, setMode] = useState<PromptMode>('ASK')
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const selectedMode = modes.find(item => item.id === mode) ?? modes[0]

  useEffect(() => {
    const textarea = textareaRef.current
    if (!textarea) return
    textarea.style.height = '0px'
    textarea.style.height = Math.min(textarea.scrollHeight, 128) + 'px'
  }, [value])

  const submit = () => {
    const text = value.trim()
    if (!text || busy) return
    onSend(preparePrompt(mode, text))
    setMode('ASK')
  }

  return <footer className="shrink-0 pb-2 pt-3">
    <div className="composer-shell glass rounded-[27px] p-2">
      <div className="mb-2 flex items-center gap-1 overflow-x-auto px-1 pt-1 scrollbar-hide" role="group" aria-label="Prompt style">
        {modes.map(item => {
          const Icon = item.icon
          const selected = mode === item.id
          return <button key={item.id} type="button" aria-pressed={selected} title={item.hint} onClick={() => setMode(item.id)} className={'prompt-mode flex shrink-0 items-center gap-1.5 rounded-full border px-3 py-1.5 text-[11px] transition-all duration-200 ' + (selected ? 'border-cyan-200/30 bg-cyan-100/[.12] text-cyan-50 shadow-[inset_0_1px_0_rgba(255,255,255,.08)]' : 'border-transparent text-white/45 hover:border-white/10 hover:bg-white/[.04] hover:text-white/75')}>
            <Icon size={13} strokeWidth={1.8} />
            {item.label}
          </button>
        })}
        <span className="ml-auto hidden shrink-0 pr-2 text-[10px] text-white/25 sm:block">{selectedMode.hint}</span>
      </div>
      <textarea ref={textareaRef} value={value} onChange={e => onChange(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit() } }} rows={1} aria-label="Message Theo" placeholder={mode === 'LEARN' ? 'What should we learn together?' : mode === 'PLAN' ? 'What are we planning?' : mode === 'CODE' ? 'Describe the thing to build…' : 'Message Theo…'} className="max-h-32 min-h-11 w-full resize-none bg-transparent px-3 py-3 text-[14px] leading-5 text-white outline-none placeholder:text-white/30" />
      <div className="flex items-center justify-between px-1 pb-1">
        <div className="flex min-w-0 items-center gap-1">
          <button type="button" onClick={onVoice} className={'flex h-9 items-center gap-2 rounded-full px-3 transition-colors hover:bg-white/[.06] ' + (speechEnabled ? 'text-cyan-100/75' : 'text-white/35')} aria-label="Activate voice">
            <AudioLines size={15} /><span className="text-[11px]">Voice</span>
          </button>
          <span className="h-4 w-px bg-white/10" />
          <span className="flex max-w-[130px] items-center gap-1.5 truncate rounded-full px-2 text-[10px] text-white/35"><span className="h-1.5 w-1.5 shrink-0 rounded-full bg-cyan-200/70 shadow-[0_0_8px_rgba(103,232,249,.35)]" />{model || 'Theo AI'}</span>
        </div>
        <button type="button" onClick={submit} disabled={!value.trim() || busy} aria-label={busy ? 'Theo is thinking' : 'Send message'} className="send-button flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-cyan-100 via-cyan-200 to-blue-300 text-slate-950 shadow-[0_3px_24px_rgba(56,189,248,.2)] transition-all duration-200 hover:scale-[1.04] hover:shadow-[0_3px_30px_rgba(56,189,248,.3)] active:scale-95 disabled:cursor-not-allowed disabled:opacity-35 disabled:hover:scale-100">
          {busy ? <LoaderCircle size={16} className="animate-spin" /> : <ArrowUp size={19} strokeWidth={2.3} />}
        </button>
      </div>
    </div>
    <p className="pt-2 text-center text-[9px] tracking-wide text-white/25">Theo can make mistakes. Verify important actions.</p>
  </footer>
}
