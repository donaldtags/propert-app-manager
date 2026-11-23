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
import { isTwoFactorChallenge, type TwoFactorChallenge, type TwoFactorMethod, type User } from "./types";
import { ApiError, auth } from "./api";
import { getDeviceId } from "./device";

/** What the password step produced: signed in, or a code is needed first. */
export type LoginResult = { user: User; challenge?: undefined } | { user?: undefined; challenge: TwoFactorChallenge };

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
}

interface AuthContextValue extends AuthState {
  login: (identifier: string, password: string) => Promise<LoginResult>;
  /** Second sign-in step for accounts with two-step sign-in on. */
  completeTwoFactor: (challengeToken: string, code: string, method: TwoFactorMethod) => Promise<User>;
  register: (data: {
    fullName: string;
    email?: string;
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

  // The token lives in localStorage, which only exists after hydration, so restoring the session is
  // exactly what an effect is for.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    const stored = readMigrated(TOKEN_KEY, LEGACY_TOKEN_KEY);
    if (stored) {
      loadUser(stored);
    } else {
      setState((s) => ({ ...s, loading: false }));
    }
  }, [loadUser]);
  /* eslint-enable react-hooks/set-state-in-effect */

  const signedIn = useCallback((token: string, user: User) => {
    localStorage.setItem(TOKEN_KEY, token);
    cacheUser(user);
    setState({ user, token, loading: false });
  }, []);

  const login = useCallback(async (identifier: string, password: string): Promise<LoginResult> => {
    const response = await auth.login({ identifier, password, deviceId: getDeviceId() });
    if (isTwoFactorChallenge(response)) return { challenge: response };
    signedIn(response.token, response.user);
    return { user: response.user };
  }, [signedIn]);

  const completeTwoFactor = useCallback(async (challengeToken: string, code: string, method: TwoFactorMethod) => {
    const { token, user } = await auth.verifyTwoFactor({ challengeToken, code, method, deviceId: getDeviceId() });
    signedIn(token, user);
    return user;
  }, [signedIn]);

  const register = useCallback(
    async (data: {
      fullName: string;
      email?: string;
      phone?: string;
      password: string;
      country?: string;
      roles?: string[];
    }) => {
      const { token, user } = await auth.register({ ...data, deviceId: getDeviceId() });
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
      value={{ ...state, login, completeTwoFactor, register, logout, refresh, setUser }}
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
