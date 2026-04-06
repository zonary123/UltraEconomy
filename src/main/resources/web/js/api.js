const BASE = ''
const MAX_RETRIES = 3

export async function apiGet (path) {
  let lastErr = null
  for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
    const res = await fetch(`${BASE}${path}`)

    if (res.ok) return res.json()

    // Rate limited — wait and retry
    if (res.status === 429) {
      const retryAfter = parseInt(res.headers.get('Retry-After') || '2', 10)
      lastErr = new Error(`Rate limited. Retry after ${retryAfter}s`)
      await sleep(retryAfter * 1000)
      continue
    }

    // Banned — don't retry
    if (res.status === 403) {
      const body = await res.json().catch(() => ({}))
      throw new Error(body.error || 'Access denied')
    }

    // Other errors — don't retry
    const msg = await res.text().catch(() => res.statusText)
    throw new Error(`API ${res.status}: ${msg}`)
  }

  throw lastErr || new Error('Max retries exceeded')
}

/** Dynamically load an external script (cached after first load). */
export function loadScript (src) {
  return new Promise((resolve, reject) => {
    if (document.querySelector(`script[src="${src}"]`)) { resolve(); return }
    const s = document.createElement('script')
    s.src = src
    s.onload = resolve
    s.onerror = () => reject(new Error(`Failed to load ${src}`))
    document.head.appendChild(s)
  })
}

function sleep (ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

