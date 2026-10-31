// La pestaña se abre antes de pedir el PDF, en el mismo clic, para que el navegador no la bloquee
export async function openPdf(loadBlob) {
  const tab = window.open('', '_blank')
  try {
    const blob = await loadBlob()
    const url = URL.createObjectURL(blob)
    if (tab) {
      tab.location.href = url
    } else {
      window.location.assign(url)
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (error) {
    tab?.close()
    throw error
  }
}

// Abre el diálogo de impresión del PDF sin salir de la página: lo carga en un marco oculto y lo imprime desde ahí
export async function printPdf(loadBlob) {
  const blob = await loadBlob()
  const url = URL.createObjectURL(blob)
  const frame = document.createElement('iframe')
  frame.className = 'print-frame'
  frame.setAttribute('aria-hidden', 'true')
  frame.src = url
  // El marco se quita después, porque cerrarlo antes cancela la impresión en algunos navegadores
  const cleanUp = () => setTimeout(() => {
    frame.remove()
    URL.revokeObjectURL(url)
  }, 60_000)
  frame.onload = () => {
    try {
      frame.contentWindow.focus()
      frame.contentWindow.print()
    } catch {
      // Si el navegador no deja imprimir desde el marco, se muestra el PDF en una pestaña para imprimirlo desde allí
      window.open(url, '_blank')
    }
    cleanUp()
  }
  document.body.append(frame)
}

export function downloadFile(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
