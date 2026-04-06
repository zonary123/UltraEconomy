import { renderRoute } from './router.js'

/* SPA navigation — intercept [data-link] clicks */
document.addEventListener('click', e => {
  const link = e.target.closest('[data-link]')
  if (!link) return
  e.preventDefault()
  const url = link.getAttribute('href')
  if (url && url !== window.location.pathname) {
    history.pushState(null, null, url)
    renderRoute()
  }
})

window.addEventListener('popstate', renderRoute)

/* Initial render */
renderRoute()
