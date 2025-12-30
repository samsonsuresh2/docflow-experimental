import api from './api';
import type { ModuleCode } from '../types/modules';

export type SessionContext = {
  userId: string;
  activeRole: string | null;
  allowedModules: ModuleCode[];
};

let cachedSession: SessionContext | null = null;
let inflight: Promise<SessionContext> | null = null;

export async function getSessionContext(options?: { forceRefresh?: boolean }): Promise<SessionContext> {
  if (options?.forceRefresh) {
    cachedSession = null;
    inflight = null;
  }
  if (cachedSession) {
    return cachedSession;
  }
  if (!inflight) {
    inflight = api
      .get<SessionContext>('/auth/me')
      .then((response) => {
        const allowedModules = (response.data.allowedModules ?? [])
          .map((code) => (code ?? '').trim().toUpperCase())
          .filter(Boolean) as ModuleCode[];
        const normalized: SessionContext = {
          userId: response.data.userId,
          activeRole: response.data.activeRole,
          allowedModules,
        };
        cachedSession = normalized;
        return normalized;
      })
      .finally(() => {
        inflight = null;
      });
  }
  return inflight;
}

export function clearSessionContextCache() {
  cachedSession = null;
  inflight = null;
}
