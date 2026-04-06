import { Navbar } from '../components/navbar.js'
import { apiGet, loadScript } from '../api.js'

const PAGE_SIZE = 50
const CHART_JS_URL = 'https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js'

const money = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const dateFmt = new Intl.DateTimeFormat('en-US', { month: '2-digit', day: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' })

let allTransactions = []
let selectedCurrency = null
let selectedDays = 7
let activeTypeFilter = 'ALL'
let currentPage = 1
let chartInstance = null

const TYPE_META = {
  DEPOSIT:  { label: 'Deposit',  dotClass: 'type-dot-deposit',  pillClass: 'pill-success', sign: '+', textClass: 'text-success' },
  WITHDRAW: { label: 'Withdraw', dotClass: 'type-dot-withdraw', pillClass: 'pill-danger',  sign: '-', textClass: 'text-danger' },
  SET:      { label: 'Set',      dotClass: 'type-dot-set',      pillClass: 'pill-warning', sign: '',  textClass: 'text-warning' },
  TRANSFER: { label: 'Transfer', dotClass: 'type-dot-transfer', pillClass: 'pill-blue',    sign: '→', textClass: 'text-accent' }
}

/* ======================== Page Shell ======================== */

export async function PlayerPage () {
  return `
    ${Navbar()}
    <div class="container page">
      <div id="playerContent">
        <div class="detail-grid">
          <div class="card skeleton" style="height:340px"></div>
          <div class="card skeleton" style="height:380px"></div>
        </div>
      </div>
    </div>
  `
}

/* ======================== After Render ======================== */

PlayerPage.afterRender = async function ({ uuid }) {
  const container = document.getElementById('playerContent')
  if (!container) return

  try {
    const [player, transactions] = await Promise.all([
      apiGet(`/api/player/${uuid}`),
      apiGet(`/api/transactions/player/${uuid}?limit=500`)
    ])

    if (!player) {
      container.innerHTML = '<div class="state-box"><p>Player not found</p></div>'
      return
    }

    allTransactions = transactions || []

    /* Balance rows */
    const balanceRows = Object.entries(player.balances || {})
      .map(([cur, amt]) => `
        <tr>
          <td class="font-mono text-accent">${cur}</td>
          <td class="text-right text-gold font-mono">${money.format(amt)}</td>
        </tr>
      `).join('')

    container.innerHTML = `
      <!-- Top: Profile + Chart -->
      <div class="detail-grid mb-3">
        <!-- Profile Card -->
        <div class="card profile-card">
          <img src="https://minotar.net/helm/${encodeURIComponent(player.playerName)}/192.png"
               alt="${player.playerName}" class="player-avatar" width="96" height="96"/>
          <h2 class="profile-name">${player.playerName}</h2>
          <div class="table-wrap">
            <table>
              <thead><tr><th>Currency</th><th class="text-right">Balance</th></tr></thead>
              <tbody>${balanceRows || '<tr><td colspan="2" class="text-muted text-center">No balances</td></tr>'}</tbody>
            </table>
          </div>
        </div>

        <!-- Chart Card -->
        <div class="card chart-card">
          <div class="section-header">
            <h3 class="section-title" style="margin:0">Money Flow</h3>
            <div class="flex gap-1">
              <select id="currencySelect" class="select"></select>
              <select id="daysSelect" class="select">
                <option value="1">Today</option>
                <option value="3">3 days</option>
                <option value="7" selected>7 days</option>
                <option value="30">30 days</option>
                <option value="90">90 days</option>
              </select>
            </div>
          </div>
          <div id="summaryStats" class="summary-grid mb-2"></div>
          <div class="chart-container">
            <canvas id="moneyChart"></canvas>
          </div>
        </div>
      </div>

      <!-- Transactions -->
      <div class="card">
        <div class="filter-bar">
          <h3 class="section-title" style="margin:0">Transactions</h3>
          <div id="typePills" class="filter-pills"></div>
        </div>
        <div class="table-wrap">
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th class="text-center">Type</th>
                  <th>Currency</th>
                  <th class="text-right">Amount</th>
                </tr>
              </thead>
              <tbody id="txBody"></tbody>
            </table>
          </div>
        </div>
        <div id="txPagination" class="pagination"></div>
      </div>
    `

    initSelectors()
    renderTypePills()
    renderSummary()
    renderTransactions()
    await initChart()
  } catch (err) {
    console.error(err)
    container.innerHTML = '<div class="state-box"><p>Error loading player data</p></div>'
  }

  /* Cleanup for router */
  return () => {
    if (chartInstance) { chartInstance.destroy(); chartInstance = null }
  }
}

/* ======================== Selectors ======================== */

function initSelectors () {
  const curSelect = document.getElementById('currencySelect')
  const daysSelect = document.getElementById('daysSelect')

  const currencies = [...new Set(allTransactions.map(t => t.currency))]
  selectedCurrency = currencies[0] || ''
  curSelect.innerHTML = currencies.map(c => `<option value="${c}">${c}</option>`).join('')
  curSelect.value = selectedCurrency
  daysSelect.value = selectedDays

  const onChange = () => {
    selectedCurrency = curSelect.value
    selectedDays = Number.parseInt(daysSelect.value, 10)
    currentPage = 1
    renderSummary()
    renderTransactions()
    renderChart()
  }

  curSelect.addEventListener('change', onChange)
  daysSelect.addEventListener('change', onChange)
}

/* ======================== Type Filter Pills ======================== */

function renderTypePills () {
  const el = document.getElementById('typePills')
  if (!el) return

  const types = ['ALL', ...Object.keys(TYPE_META)]
  el.innerHTML = types.map(t => {
    const meta = TYPE_META[t]
    const pillClass = meta ? meta.pillClass : ''
    const active = t === activeTypeFilter ? ' active' : ''
    return `<button class="pill ${pillClass}${active}" data-type="${t}">${meta ? meta.label : 'All'}</button>`
  }).join('')

  el.addEventListener('click', (e) => {
    const btn = e.target.closest('.pill')
    if (!btn) return
    activeTypeFilter = btn.dataset.type
    currentPage = 1
    el.querySelectorAll('.pill').forEach(p => p.classList.remove('active'))
    btn.classList.add('active')
    renderTransactions()
  })
}

/* ======================== Filter helpers ======================== */

function getCurrencyFiltered () {
  const cutoff = new Date()
  cutoff.setDate(cutoff.getDate() - selectedDays + 1)
  cutoff.setHours(0, 0, 0, 0)
  return allTransactions.filter(tx =>
    tx.currency === selectedCurrency && new Date(tx.timestamp) >= cutoff
  )
}

function getFiltered () {
  const list = getCurrencyFiltered()
  if (activeTypeFilter === 'ALL') return list
  return list.filter(tx => tx.type === activeTypeFilter)
}

/* ======================== Summary Stats ======================== */

function renderSummary () {
  const el = document.getElementById('summaryStats')
  if (!el) return

  const filtered = getCurrencyFiltered()
  let totalDeposits = 0
  let totalWithdrawals = 0

  for (const tx of filtered) {
    if (!tx.processed) continue
    if (tx.type === 'DEPOSIT') totalDeposits += tx.amount
    else if (tx.type === 'WITHDRAW') totalWithdrawals += tx.amount
  }

  const net = totalDeposits - totalWithdrawals
  const netClass = net >= 0 ? 'text-success' : 'text-danger'
  const netSign = net >= 0 ? '+' : ''

  el.innerHTML = `
    <div class="summary-item">
      <div class="summary-label">Deposited</div>
      <div class="summary-value text-success">+${money.format(totalDeposits)}</div>
    </div>
    <div class="summary-item">
      <div class="summary-label">Withdrawn</div>
      <div class="summary-value text-danger">-${money.format(totalWithdrawals)}</div>
    </div>
    <div class="summary-item">
      <div class="summary-label">Net Change</div>
      <div class="summary-value ${netClass}">${netSign}${money.format(net)}</div>
    </div>
  `
}

/* ======================== Transactions Table ======================== */

function renderTransactions () {
  const tbody = document.getElementById('txBody')
  const pagEl = document.getElementById('txPagination')
  if (!tbody || !pagEl) return

  const filtered = getFiltered()
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  if (currentPage > totalPages) currentPage = totalPages
  const start = (currentPage - 1) * PAGE_SIZE
  const page = filtered.slice(start, start + PAGE_SIZE)

  if (!page.length) {
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted" style="padding:2rem">No transactions found</td></tr>'
    pagEl.innerHTML = ''
    return
  }

  tbody.innerHTML = page.map(tx => {
    const meta = TYPE_META[tx.type] || { label: tx.type, dotClass: '', sign: '', textClass: 'text-muted' }
    return `
      <tr>
        <td class="text-muted">${dateFmt.format(new Date(tx.timestamp))}</td>
        <td class="text-center">
          <span class="type-dot ${meta.dotClass}"></span>
          <span class="${meta.textClass}" style="font-weight:600">${meta.label}</span>
        </td>
        <td>${tx.currency}</td>
        <td class="text-right font-mono ${meta.textClass}">${meta.sign}${money.format(tx.amount)}</td>
      </tr>
    `
  }).join('')

  pagEl.innerHTML = `
    <button id="txPrev" class="btn btn-ghost btn-sm" ${currentPage <= 1 ? 'disabled' : ''}>←</button>
    <span class="page-info">${currentPage} / ${totalPages}</span>
    <button id="txNext" class="btn btn-ghost btn-sm" ${currentPage >= totalPages ? 'disabled' : ''}>→</button>
  `

  document.getElementById('txPrev')?.addEventListener('click', () => { currentPage--; renderTransactions() })
  document.getElementById('txNext')?.addEventListener('click', () => { currentPage++; renderTransactions() })
}

/* ======================== Chart (lazy-loaded) ======================== */

async function initChart () {
  try {
    await loadScript(CHART_JS_URL)
    await waitForLayout()
    renderChart()
  } catch {
    const wrapper = document.querySelector('.chart-container')
    if (wrapper) wrapper.innerHTML = '<div class="chart-empty">Chart unavailable</div>'
  }
}

function waitForLayout () {
  return new Promise(resolve => {
    requestAnimationFrame(() => {
      requestAnimationFrame(resolve)
    })
  })
}

function renderChart () {
  const ctx = document.getElementById('moneyChart')
  if (!ctx || typeof Chart === 'undefined') return
  if (chartInstance) chartInstance.destroy()

  const filtered = getCurrencyFiltered()
  const depositMap = {}
  const withdrawMap = {}

  for (const tx of filtered) {
    if (!tx.processed) continue
    const key = new Date(tx.timestamp).toISOString().slice(0, 13) + ':00'
    if (tx.type === 'DEPOSIT') {
      depositMap[key] = (depositMap[key] || 0) + tx.amount
    } else if (tx.type === 'WITHDRAW') {
      withdrawMap[key] = (withdrawMap[key] || 0) + tx.amount
    }
  }

  const allKeys = [...new Set([...Object.keys(depositMap), ...Object.keys(withdrawMap)])].sort()

  if (!allKeys.length) {
    const wrapper = ctx.parentElement
    if (wrapper) {
      ctx.style.display = 'none'
      if (!wrapper.querySelector('.chart-empty')) {
        wrapper.insertAdjacentHTML('beforeend', '<div class="chart-empty">No chart data for this period</div>')
      }
    }
    return
  }

  ctx.style.display = ''
  const emptyMsg = ctx.parentElement?.querySelector('.chart-empty')
  if (emptyMsg) emptyMsg.remove()

  const labels = allKeys.map(k => dateFmt.format(new Date(k)))
  const depositData = allKeys.map(k => depositMap[k] || 0)
  const withdrawData = allKeys.map(k => withdrawMap[k] || 0)
  const compact = allKeys.length > 50

  chartInstance = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: `Deposits (${selectedCurrency})`,
          data: depositData,
          borderColor: '#22c55e',
          backgroundColor: 'rgba(34,197,94,0.08)',
          tension: 0.35,
          fill: true,
          pointRadius: compact ? 0 : 3,
          pointHoverRadius: 5,
          borderWidth: 2
        },
        {
          label: `Withdrawals (${selectedCurrency})`,
          data: withdrawData,
          borderColor: '#ef4444',
          backgroundColor: 'rgba(239,68,68,0.08)',
          tension: 0.35,
          fill: true,
          pointRadius: compact ? 0 : 3,
          pointHoverRadius: 5,
          borderWidth: 2
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          align: 'end',
          labels: {
            color: '#94a3b8',
            usePointStyle: true,
            pointStyle: 'circle',
            boxWidth: 8,
            padding: 16,
            font: { size: 12, weight: '500' }
          }
        },
        tooltip: {
          backgroundColor: '#1a1d2e',
          borderColor: 'rgba(255,255,255,0.06)',
          borderWidth: 1,
          titleColor: '#e2e8f0',
          bodyColor: '#94a3b8',
          padding: 10,
          callbacks: {
            label: item => `${item.dataset.label}: ${money.format(item.parsed.y)}`
          }
        }
      },
      scales: {
        x: {
          ticks: { color: '#64748b', font: { size: 11 }, maxRotation: 45, maxTicksLimit: 12 },
          grid: { color: 'rgba(255,255,255,0.03)' }
        },
        y: {
          beginAtZero: true,
          ticks: { color: '#64748b', font: { size: 11 }, callback: v => money.format(v) },
          grid: { color: 'rgba(255,255,255,0.03)' }
        }
      }
    }
  })
}
