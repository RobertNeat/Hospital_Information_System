/** Base path of the REST API; proxied to the backend by the dev server and by nginx. */
export const API_BASE_URL = '/api/v1';

export const AUTH_LOGIN_URL = `${API_BASE_URL}/auth/login`;
export const AUTH_REGISTER_URL = `${API_BASE_URL}/auth/register`;
export const AUTH_LOGOUT_URL = `${API_BASE_URL}/auth/logout`;
export const AUTH_ME_URL = `${API_BASE_URL}/auth/me`;
