import { Navbar } from '../components/navbar.js'
import { apiGet } from '../api.js'
import { Card as PlayerCard, PlayerCardSkeleton } from '../components/playerCard.js'

const PAGE_SIZE = 50
let currentPage = 1
let playersData = []

export function PlayersPage () {
  return `
    ${Navbar()}
    <div class="container page">
      <!-- Search -->
      <div class="mb-2">
        <div class="input-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>
          <input id="searchInput" class="input-field" type="text" placeholder="Search player by name and press Enter…">
        </div>
      </div>

      <!-- Players grid -->
      <div id="players" class="players-grid">
        ${Array(8).fill(PlayerCardSkeleton()).join('')}
      </div>

      <!-- Pagination -->
      <div id="pagination" class="pagination"></div>
    </div>
  `
}

PlayersPage.afterRender = function () {
  currentPage = 1
  loadPlayers(currentPage)

  const input = document.getElementById('searchInput')
  if (input) {
    input.addEventListener('keydown', handleSearch)
    input.focus()
  }
}

async function loadPlayers (page) {
  const container = document.getElementById('players')
  if (!container) return

  try {
    const data = await apiGet(`/api/players?page=${page}`)

    if (!Array.isArray(data)) {
      container.innerHTML = '<div class="state-box"><p>Invalid response</p></div>'
      return
    }

    playersData = data
    renderPlayers()
    renderPagination()
  } catch (err) {
    container.innerHTML = '<div class="state-box"><p>Error loading players</p></div>'
    console.error(err)
  }
}

function renderPlayers () {
  const container = document.getElementById('players')
  if (!container) return

  if (!playersData.length) {
    container.innerHTML = '<div class="state-box"><p>No players found</p></div>'
    return
  }

  container.innerHTML = playersData.map(p => PlayerCard(p)).join('')
}

function renderPagination () {
  const el = document.getElementById('pagination')
  if (!el) return

  const hasPrev = currentPage > 1
  const hasNext = playersData.length >= PAGE_SIZE

  el.innerHTML = `
    <button id="prevBtn" class="btn btn-ghost btn-sm" ${hasPrev ? '' : 'disabled'}>← Previous</button>
    <span class="page-info">Page ${currentPage}</span>
    <button id="nextBtn" class="btn btn-ghost btn-sm" ${hasNext ? '' : 'disabled'}>Next →</button>
  `

  const prev = document.getElementById('prevBtn')
  const next = document.getElementById('nextBtn')
  if (prev) prev.addEventListener('click', () => { if (currentPage > 1) { currentPage--; loadPlayers(currentPage) } })
  if (next) next.addEventListener('click', () => { if (hasNext) { currentPage++; loadPlayers(currentPage) } })
}

async function handleSearch (e) {
  if (e.key !== 'Enter') return
  const name = e.target.value.trim()
  if (!name) return

  e.target.disabled = true
  try {
    const player = await apiGet(`/api/player/${encodeURIComponent(name)}`)
    if (player && player.playerUUID) {
      history.pushState(null, null, `/player/${player.playerUUID}`)
      const { renderRoute } = await import('../router.js')
      renderRoute()
    }
  } catch {
    e.target.classList.add('error')
    setTimeout(() => { e.target.classList.remove('error') }, 1500)
  }
  e.target.disabled = false
}
