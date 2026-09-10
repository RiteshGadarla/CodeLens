import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { ProjectLayout } from '@/components/ProjectLayout'
import { Spinner } from '@/components/ui'
import { GuestOnly, RequireAuth } from '@/lib/auth'

// route-level code splitting
const LandingPage = lazy(() => import('@/pages/LandingPage'))
const SignInPage = lazy(() => import('@/pages/AuthPages').then((m) => ({ default: m.SignInPage })))
const SignUpPage = lazy(() => import('@/pages/AuthPages').then((m) => ({ default: m.SignUpPage })))
const DashboardPage = lazy(() => import('@/pages/DashboardPage'))
const OverviewPage = lazy(() => import('@/pages/OverviewPage'))
const GraphPage = lazy(() => import('@/pages/GraphPage'))
const MetricsPage = lazy(() => import('@/pages/MetricsPage'))
const LyraPage = lazy(() => import('@/pages/LyraPage'))
const EntityPage = lazy(() => import('@/pages/EntityPage'))
const ImpactPage = lazy(() => import('@/pages/ImpactPage'))

export default function App() {
  return (
    <Suspense fallback={<Spinner />}>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/signin" element={<GuestOnly><SignInPage /></GuestOnly>} />
        <Route path="/signup" element={<GuestOnly><SignUpPage /></GuestOnly>} />
        <Route element={<RequireAuth />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/projects/:projectId" element={<ProjectLayout />}>
            <Route index element={<OverviewPage />} />
            <Route path="graph" element={<GraphPage />} />
            <Route path="metrics" element={<MetricsPage />} />
            <Route path="lyra" element={<LyraPage />} />
            <Route path="entities/:entityId" element={<EntityPage />} />
            <Route path="impact/:entityId" element={<ImpactPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
