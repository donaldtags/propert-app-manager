import { readMigrated } from "./storageKeys";

const STORAGE_KEY = "estatex_moving_checklist";
const LEGACY_KEY = "primenest_moving_checklist";

export interface MovingChecklistState {
  [taskId: string]: boolean;
}

function read(): MovingChecklistState {
  if (typeof window === "undefined") return {};
  try {
    const raw = readMigrated(STORAGE_KEY, LEGACY_KEY);
    return raw ? (JSON.parse(raw) as MovingChecklistState) : {};
  } catch {
    return {};
  }
}

function write(state: MovingChecklistState) {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function getChecklistState(): MovingChecklistState {
  return read();
}

export function toggleChecklistTask(taskId: string): boolean {
  const state = read();
  const next = !state[taskId];
  state[taskId] = next;
  write(state);
  return next;
}
