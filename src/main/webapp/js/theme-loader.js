// We wait till the DOM is loaded to make sure theme manager has loaded
document.addEventListener('DOMContentLoaded', () => {
  const head = document.getElementsByTagName('head')[0]
  const resUrl = head.dataset.resurl

  function setTheme() {
    const themeElement = document.getElementById('prism-theme-loader')
    let themeCss = themeElement.dataset.selectedTheme || "prism.css"

    // if default theme is in use then use theme manager API to get light / dark version
    if (themeCss === 'prism.css' && window.getThemeManagerProperty) {
      const themeName = window.getThemeManagerProperty('prism-api', 'theme')

      if (themeName) {
        themeCss = `prism-${themeName}.css`
      }
    }

    const activeTheme = document.getElementById('prism-active-theme')
    if (activeTheme) {
      activeTheme.remove()
    }

    head.insertAdjacentHTML(
      'beforeend',
      `<link id="prism-active-theme" rel="stylesheet" href="${resUrl}/plugin/prism-api/css/prism/${themeCss}" />`);
  }

  if (window.getThemeManagerProperty && window.isSystemRespectingTheme) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', event => {
      setTheme()
    });
  }

  setTheme()
})
