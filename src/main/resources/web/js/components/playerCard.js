const fmt = new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 })

const BADGE_COLORS = ['badge-emerald', 'badge-gold', 'badge-blue', 'badge-gray']

/**
 * Player card showing head, name, and balance badges.
 */
export function Card (playerData) {
  const balances = playerData.balances || {}
  const entries = Object.entries(balances)
  const badges = entries.slice(0, 3).map(([currency, amount], i) =>
    `<span class="badge ${BADGE_COLORS[i] || 'badge-gray'}">${currency} ${fmt.format(amount)}</span>`
  ).join('')

  return `
    <a href="/player/${playerData.playerUUID}" data-link class="player-card">
      <img
        src="https://minotar.net/helm/${encodeURIComponent(playerData.playerName)}/112.png"
        alt="${playerData.playerName}"
        class="player-avatar"
        loading="lazy"
        width="56" height="56"
      />
      <div class="player-info">
        <div class="player-name">${playerData.playerName}</div>
        <div class="player-balances">${badges || '<span class="badge badge-gray">No balances</span>'}</div>
      </div>
    </a>
  `
}

export function PlayerCardSkeleton () {
  return `<div class="skeleton skeleton-card"></div>`
}
