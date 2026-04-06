/**
 * Reusable stat card for the dashboard.
 * @param {string} icon  - Emoji or short text
 * @param {string} label - Stat label
 * @param {string|number} value - Stat value
 * @param {string} color - 'green' | 'blue' | 'gold'
 */
export function StatCard (icon, label, value, color = 'green') {
  return `
    <div class="stat-card">
      <div class="stat-icon ${color}">${icon}</div>
      <div>
        <div class="stat-label">${label}</div>
        <div class="stat-value">${value}</div>
      </div>
    </div>
  `
}

export function StatCardSkeleton () {
  return `<div class="skeleton skeleton-stat"></div>`
}
