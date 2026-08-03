import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedLayout } from './ProtectedLayout'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { HomePage } from './pages/HomePage'
import { InterviewPage } from './pages/InterviewPage'
import { LearningPage } from './pages/LearningPage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { ResultsPage } from './pages/ResultsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/app" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/app" element={<ProtectedLayout />}>
        <Route index element={<HomePage />} />
        <Route path="interview/:id" element={<InterviewPage />} />
        <Route path="results/:id" element={<ResultsPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
        <Route path="learning" element={<LearningPage />} />
        <Route path="profile" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes>
  )
}
