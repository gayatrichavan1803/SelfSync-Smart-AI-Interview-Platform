import { initializeApp, type FirebaseApp } from 'firebase/app'
import {
  getAuth,
  GoogleAuthProvider,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signInWithPopup,
  sendPasswordResetEmail,
  updateProfile,
  type Auth,
  type User as FirebaseUser,
} from 'firebase/auth'

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? '',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN ?? '',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID ?? '',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET ?? '',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '',
  appId: import.meta.env.VITE_FIREBASE_APP_ID ?? '',
}

export function isFirebaseConfigured() {
  return Boolean(firebaseConfig.apiKey && firebaseConfig.projectId && firebaseConfig.appId)
}

let app: FirebaseApp | null = null
let auth: Auth | null = null

export function getFirebaseAuth(): Auth {
  if (!isFirebaseConfigured()) {
    throw new Error('Firebase is not configured. Add VITE_FIREBASE_* values to frontend/.env')
  }
  if (!app) {
    app = initializeApp(firebaseConfig)
    auth = getAuth(app)
  }
  return auth!
}

export async function firebaseRegister(email: string, password: string, fullName: string) {
  const a = getFirebaseAuth()
  const cred = await createUserWithEmailAndPassword(a, email, password)
  if (fullName) {
    await updateProfile(cred.user, { displayName: fullName })
  }
  return cred.user
}

export async function firebaseLogin(email: string, password: string) {
  const a = getFirebaseAuth()
  const cred = await signInWithEmailAndPassword(a, email, password)
  return cred.user
}

export async function firebaseGoogleLogin() {
  const a = getFirebaseAuth()
  const provider = new GoogleAuthProvider()
  provider.setCustomParameters({ prompt: 'select_account' })
  const cred = await signInWithPopup(a, provider)
  return cred.user
}

export async function firebaseForgotPassword(email: string) {
  const a = getFirebaseAuth()
  await sendPasswordResetEmail(a, email)
}

export async function firebaseIdToken(user: FirebaseUser) {
  return user.getIdToken(true)
}
