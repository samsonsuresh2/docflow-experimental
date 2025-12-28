import client from './api';

export async function fetchAllowedModules(): Promise<string[]> {
  const response = await client.get<{ modules: string[] }>('/me/modules');
  return response.data.modules ?? [];
}
