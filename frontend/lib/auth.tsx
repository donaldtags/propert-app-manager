"use client";

import { readMigrated } from "./storageKeys";
import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  type ReactNode,
} from "react";
import type { User } from "./types";
import { ApiError, auth } from "./api";

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
}

interface AuthContextValue extends AuthState {
  login: (identifier: string, password: string) => Promise<User>;
  register: (data: {
    fullName: string;
    email: string;
    phone?: string;
    password: string;
    country?: string;
    roles?: string[];
  }) => Promise<User>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  setUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const TOKEN_KEY = "estatex_token";
/** Where the token lived when the app was called PrimeNest; moved on first read so nobody is logged out. */
const LEGACY_TOKEN_KEY = "pn_token";
/** Last known user, so the app still opens when the API can't be reached (offline, restarting). */
const USER_KEY = "ex_user";

function readCachedUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as User) : null;
  } catch {
    return null;
  }
}

function cacheUser(user: User | null) {
  try {
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
    else localStorage.removeItem(USER_KEY);
  } catch {
    // storage unavailable: the app just won't open offline
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    token: null,
    loading: true,
  });

  const loadUser = useCallback(async (token: string) => {
    try {
      const user = await auth.me(token);
      cacheUser(user);
      setState({ user, token, loading: false });
    } catch (e) {
      const rejected = e instanceof ApiError && (e.status === 401 || e.status === 403);
      const cached = readCachedUser();
      if (!rejected && cached) {
        // Server unreachable or erroring: keep the session and work from the cached profile.
        setState({ user: cached, token, loading: false });
        return;
      }
      localStorage.removeItem(TOKEN_KEY);
      cacheUser(null);
      setState({ user: null, token: null, loading: false });
    }
  }, []);

  useEffect(() => {
    const stored = readMigrated(TOKEN_KEY, LEGACY_TOKEN_KEY);
    if (stored) {
      loadUser(stored);
    } else {
      setState((s) => ({ ...s, loading: false }));
    }
  }, [loadUser]);

  const login = useCallback(async (identifier: string, password: string) => {
    const { token, user } = await auth.login({ identifier, password });
    localStorage.setItem(TOKEN_KEY, token);
    cacheUser(user);
    setState({ user, token, loading: false });
    return user;
  }, []);

  const register = useCallback(
    async (data: {
      fullName: string;
      email: string;
      phone?: string;
      password: string;
      country?: string;
      roles?: string[];
    }) => {
      const { token, user } = await auth.register(data);
      localStorage.setItem(TOKEN_KEY, token);
      cacheUser(user);
      setState({ user, token, loading: false });
      return user;
    },
    []
  );

  const logout = useCallback(async () => {
    const stored = readMigrated(TOKEN_KEY, LEGACY_TOKEN_KEY);
    if (stored) {
      try {
        await auth.logout(stored);
      } catch {}
    }
    localStorage.removeItem(TOKEN_KEY);
    cacheUser(null);
    setState({ user: null, token: null, loading: false });
  }, []);

  const refresh = useCallback(async () => {
    const stored = readMigrated(TOKEN_KEY, LEGACY_TOKEN_KEY);
    if (stored) await loadUser(stored);
  }, [loadUser]);

  const setUser = useCallback((user: User) => {
    cacheUser(user);
    setState((s) => ({ ...s, user }));
  }, []);

  return (
    <AuthContext.Provider
      value={{ ...state, login, register, logout, refresh, setUser }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
