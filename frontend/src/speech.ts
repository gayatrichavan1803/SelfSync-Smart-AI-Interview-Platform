/** Browser text-to-speech for reading interview questions aloud. */

export type SpeakOptions = {
  rate?: number
  pitch?: number
  volume?: number
  lang?: string
}

function pickVoice(): SpeechSynthesisVoice | null {
  if (typeof window === 'undefined' || !window.speechSynthesis) return null
  const voices = window.speechSynthesis.getVoices()
  if (!voices.length) return null
  const preferred = voices.find(
    (v) =>
      /en(-|_)?(US|GB|IN|AU)?/i.test(v.lang) &&
      /female|zira|samantha|google us english|microsoft/i.test(v.name),
  )
  return preferred || voices.find((v) => v.lang.toLowerCase().startsWith('en')) || voices[0]
}

export function stopSpeaking() {
  if (typeof window === 'undefined' || !window.speechSynthesis) return
  window.speechSynthesis.cancel()
}

export function isSpeechSupported() {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

export function speakText(
  text: string,
  options: SpeakOptions = {},
): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!isSpeechSupported() || !text.trim()) {
      resolve()
      return
    }

    stopSpeaking()

    const utter = new SpeechSynthesisUtterance(text.trim())
    utter.rate = options.rate ?? 0.95
    utter.pitch = options.pitch ?? 1
    utter.volume = options.volume ?? 1
    utter.lang = options.lang ?? 'en-US'

    const voice = pickVoice()
    if (voice) utter.voice = voice

    utter.onend = () => resolve()
    utter.onerror = (e) => {
      // "interrupted" / "canceled" is normal when navigating questions
      if (e.error === 'interrupted' || e.error === 'canceled') {
        resolve()
        return
      }
      reject(new Error(e.error || 'Speech failed'))
    }

    // Chrome often needs voices loaded asynchronously
    const start = () => window.speechSynthesis.speak(utter)
    if (window.speechSynthesis.getVoices().length === 0) {
      window.speechSynthesis.addEventListener('voiceschanged', start, { once: true })
      // Fallback if event never fires
      setTimeout(start, 250)
    } else {
      start()
    }
  })
}
