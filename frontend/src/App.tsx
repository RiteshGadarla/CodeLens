import { Navigate, Route, Routes } from 'react-router-dom'
import { ProjectLayout } from '@/components/ProjectLayout'
import AssistantPage from '@/pages/AssistantPage'
import EntityPage from '@/pages/EntityPage'
import GraphPage from '@/pages/GraphPage'
import ImpactPage from '@/pages/ImpactPage'
import MetricsPage from '@/pages/MetricsPage'
import OverviewPage from '@/pages/OverviewPage'
import ProjectsPage from '@/pages/ProjectsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<ProjectsPage />} />
      <Route path="/projects/:projectId" element={<ProjectLayout />}>
        <Route index element={<OverviewPage />} />
        <Route path="graph" element={<GraphPage />} />
        <Route path="metrics" element={<MetricsPage />} />
        <Route path="assistant" element={<AssistantPage />} />
        <Route path="entities/:entityId" element={<EntityPage />} />
        <Route path="impact/:entityId" element={<ImpactPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
