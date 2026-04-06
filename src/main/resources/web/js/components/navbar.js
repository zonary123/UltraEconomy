const ICON_DIAMOND = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3h12l4 6-10 13L2 9z"/><path d="M2 9h20"/><path d="M12 22 6 9"/><path d="m12 22 6-13"/></svg>`

export function Navbar () {
  const path = window.location.pathname
  const link = (href, label) => {
    const active = href === '/' ? path === '/' : path.startsWith(href)
    return `<a href="${href}" data-link class="nav-link${active ? ' active' : ''}">${label}</a>`
  }

  return `
    <header class="nav">
      <div class="nav-inner">
        <a href="/" data-link class="nav-brand">${ICON_DIAMOND} UltraEconomy</a>
        <nav class="nav-links">
          ${link('/', 'Dashboard')}
          ${link('/players', 'Players')}
        </nav>
        <a href="https://github.com/zonary123" target="_blank" rel="noopener" class="nav-icon" title="GitHub">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.3 3.44 9.8 8.2 11.39.6.11.82-.26.82-.58v-2.03c-3.34.73-4.04-1.61-4.04-1.61-.55-1.39-1.34-1.76-1.34-1.76-1.09-.74.08-.73.08-.73 1.2.09 1.84 1.24 1.84 1.24 1.07 1.83 2.8 1.3 3.49 1 .11-.78.42-1.3.76-1.6-2.67-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.13-.3-.54-1.52.12-3.18 0 0 1-.32 3.3 1.23a11.5 11.5 0 016.02 0c2.28-1.55 3.29-1.23 3.29-1.23.66 1.66.25 2.88.12 3.18.77.84 1.24 1.91 1.24 3.22 0 4.61-2.81 5.63-5.48 5.92.43.37.81 1.1.81 2.22v3.29c0 .32.22.7.82.58A12.01 12.01 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>
        </a>
      </div>
    </header>
  `
}
