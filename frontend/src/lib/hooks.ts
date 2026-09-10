import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'

export const useProjectId = () => Number(useParams().projectId)

export function useDebounced<T>(value: T, ms = 180) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}
