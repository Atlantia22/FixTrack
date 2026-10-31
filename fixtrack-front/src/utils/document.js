// Formato del número de documento por tipo; mismas reglas que DocumentType en el backend
export const DOCUMENT_RULES = {
  CC: { numeric: true, min: 6, max: 10, message: 'La cédula de ciudadanía debe tener de 6 a 10 números.' },
  TI: { numeric: true, min: 10, max: 11, message: 'La tarjeta de identidad debe tener 10 u 11 números.' },
  CE: { numeric: true, min: 6, max: 12, message: 'La cédula de extranjería debe tener de 6 a 12 números.' },
  PASSPORT: { numeric: false, min: 6, max: 15, message: 'El pasaporte debe tener de 6 a 15 letras o números.' },
  PPT: { numeric: true, min: 6, max: 12, message: 'El PPT debe tener de 6 a 12 números.' },
}

// Deja solo lo que el tipo admite (números, o letras y números en el pasaporte) y recorta al máximo
export function cleanDocumentNumber(documentType, value) {
  const rule = DOCUMENT_RULES[documentType]
  const cleaned = rule.numeric ? value.replace(/\D/g, '') : value.toUpperCase().replace(/[^A-Z0-9]/g, '')
  return cleaned.slice(0, rule.max)
}

export function documentNumberError(documentType, value) {
  const rule = DOCUMENT_RULES[documentType]
  if (!value) return 'Escriba el número de documento.'
  if (value.length < rule.min || value.length > rule.max) return rule.message
  return null
}

export function documentNumberHint(documentType) {
  const rule = DOCUMENT_RULES[documentType]
  const range = rule.min === rule.max - 1 ? `${rule.min} u ${rule.max}` : `de ${rule.min} a ${rule.max}`
  return rule.numeric ? `Solo números, ${range}.` : `Letras y números, ${range}.`
}
