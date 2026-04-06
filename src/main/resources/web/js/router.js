import { DashboardPage } from './pages/dashboard.js'
import { PlayersPage } from './pages/players.js'
import { PlayerPage } from './pages/player.js'

const routes = [
  { path: '/', view: DashboardPage },
  { path: '/players', view: PlayersPage },
  { path: '/player/:uuid', view: PlayerPage }
]

function pathToRegex (path) {
  return new RegExp(
    '^' + path.replace(/\//g, '\\/').replace(/:\w+/g, '([^\\/]+)') + '$'
  )
}

function getParams (match) {
  const values = match.result.slice(1)
  const keys = Array.from(match.route.path.matchAll(/:(\w+)/g)).map(r => r[1])
  return Object.fromEntries(keys.map((key, i) => [keys[i], values[i]]))
}

let currentCleanup = null

export async function renderRoute () {
  /* Run cleanup from previous page (destroy charts, remove listeners, etc.) */
  if (currentCleanup) { currentCleanup(); currentCleanup = null }

  const path = window.location.pathname
  const match = routes
    .map(route => ({ route, result: path.match(pathToRegex(route.path)) }))
    .find(m => m.result)

  const view = match ? match.route.view : DashboardPage
  const params = match ? getParams(match) : {}

  const app = document.getElementById('app')
  app.innerHTML = await view(params)
  window.scrollTo(0, 0)

  if (view.afterRender) {
    const cleanup = await view.afterRender(params)
    if (typeof cleanup === 'function') currentCleanup = cleanup
  }
}
