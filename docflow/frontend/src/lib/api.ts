import axios from 'axios';
import { loadUser } from './user';

const client = axios.create({
  baseURL: '/api',
});

client.interceptors.request.use((config) => {
  const user = loadUser();
  if (user) {
    config.headers = config.headers ?? {};
    config.headers['X-USER-ID'] = user.id;
  }
  return config;
});

export default client;
