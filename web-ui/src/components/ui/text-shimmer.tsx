import { memo, type CSSProperties, type ElementType, type ReactNode } from 'react'

export type TextShimmerProps = {
  children: ReactNode
  as?: ElementType
  className?: string
  duration?: number
  spread?: number
  delay?: number
}

// Adapted from 21st.dev / 21st-dev/agent-elements TextShimmer (MIT).
// The class and animation tokens are namespaced so it can live inside Theo's UI.
function TextShimmerComponent({
  children,
  as: Component = 'span',
  className = '',
  duration = 2,
  spread = 100,
  delay = 0,
}: TextShimmerProps) {
  const style = {
    '--theo-shimmer-duration': `${duration}s`,
    '--theo-shimmer-spread': `${spread}px`,
    animationDelay: delay > 0 ? `${delay}s` : undefined,
  } as CSSProperties

  return <Component className={`theo-text-shimmer ${className}`.trim()} style={style}>{children}</Component>
}

export const TextShimmer = memo(TextShimmerComponent)
