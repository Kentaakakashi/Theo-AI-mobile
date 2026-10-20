import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

declare global { interface Window { __theoShowFailure?: (label: string, detail: string) => void } }

class TheoStartupBoundary extends React.Component<React.PropsWithChildren, { error: string | null }> {
  state = { error: null as string | null }
  static getDerivedStateFromError(error: unknown) {
    return { error: error instanceof Error ? error.message : String(error) }
  }
  componentDidCatch(error: Error) {
    console.error('[Theo] UI render failed', error)
  }
  render() {
    if (this.state.error) {
      return <main style={{height:'100%',display:'flex',flexDirection:'column',alignItems:'center',justifyContent:'center',padding:28,boxSizing:'border-box',background:'#080a12',color:'#f4f6ff',fontFamily:'system-ui',textAlign:'center'}}>
        <div style={{width:52,height:52,borderRadius:'50%',border:'1px solid #fb7185',boxShadow:'0 0 36px #fb718544',marginBottom:22}} />
        <div style={{fontSize:20,fontWeight:650,letterSpacing:'.2em'}}>THEO</div>
        <div style={{marginTop:12,fontSize:14,color:'#fda4af'}}>The interface hit an error</div>
        <pre style={{maxWidth:'100%',whiteSpace:'pre-wrap',overflowWrap:'anywhere',fontSize:12,lineHeight:1.6,color:'#cbd5e1',textAlign:'left'}}>{this.state.error}</pre>
      </main>
    }
    return this.props.children
  }
}

const root = document.getElementById('root')
if (root) {
  createRoot(root).render(
    <React.StrictMode><TheoStartupBoundary><App /></TheoStartupBoundary></React.StrictMode>,
  )
} else {
  window.__theoShowFailure?.('Startup error', 'Missing #root element')
}
