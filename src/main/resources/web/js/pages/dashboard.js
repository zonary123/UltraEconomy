import { Navbar } from '../components/navbar.js'
import { StatCard, StatCardSkeleton } from '../components/infoCard.js'
import { Card as PlayerCard, PlayerCardSkeleton } from '../components/playerCard.js'
import { apiGet } from '../api.js'

export function DashboardPage () {
  return `
    ${Navbar()}
    <div class="container page">
      <!-- Stats -->
      <div id="stats" class="stats-grid mb-3">
        ${StatCardSkeleton()}${StatCardSkeleton()}${StatCardSkeleton()}
      </div>

      <!-- Search -->
      <div class="mb-3">
        <div class="input-wrap" style="max-width:480px">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>
          <input id="searchInput" class="input-field" type="text" placeholder="Search player by name…">
        </div>
      </div>

      <!-- Recent Players -->
      <div class="section-header">
        <h2 class="section-title">Recent Players</h2>
        <a href="/players" data-link class="btn btn-ghost btn-sm">View all →</a>
      </div>
      <div id="recentPlayers" class="players-grid">
        ${Array(6).fill(PlayerCardSkeleton()).join('')}
      </div>
    </div>
  `
}

DashboardPage.afterRender = async function () {
  /* Load stats */
  const statsEl = document.getElementById('stats')
  try {
    const data = await apiGet('/api/stats')
    statsEl.innerHTML = [
      StatCard('👥', 'Online Players', `${data.onlinePlayers} / ${data.maxPlayers}`, 'green'),
      StatCard('💰', 'Currencies', data.currencies.length, 'gold'),
      StatCard('🟢', 'Server Status', 'Online', 'blue')
    ].join('')
  } catch {
    statsEl.innerHTML = [
      StatCard('👥', 'Online Players', '—', 'green'),
      StatCard('💰', 'Currencies', '—', 'gold'),
      StatCard('🔴', 'Server Status', 'Offline', 'blue')
    ].join('')
  }

  /* Load recent players */
  const grid = document.getElementById('recentPlayers')
  try {
    const players = await apiGet('/api/players?page=1')
    if (!Array.isArray(players) || !players.length) {
      grid.innerHTML = '<div class="state-box"><p>No players found</p></div>'
      return
    }
    grid.innerHTML = players.slice(0, 12).map(p => PlayerCard(p)).join('')
  } catch {
    grid.innerHTML = '<div class="state-box"><p>Error loading players</p></div>'
  }

  /* Search */
  const input = document.getElementById('searchInput')
  if (input) {
    input.addEventListener('keydown', async e => {
      if (e.key !== 'Enter') return
      const name = input.value.trim()
      if (!name) return
      input.disabled = true
      try {
        const player = await apiGet(`/api/player/${encodeURIComponent(name)}`)
        if (player && player.playerUUID) {
          history.pushState(null, null, `/player/${player.playerUUID}`)
          const { renderRoute } = await import('../router.js')
          renderRoute()
        }
      } catch {
        input.style.borderColor = 'var(--danger)'
        setTimeout(() => { input.style.borderColor = ''; input.disabled = false }, 1500)
      }
      input.disabled = false
    })
  }
}