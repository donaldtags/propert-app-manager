const STORAGE_KEY = "homestead_moving_checklist";

export interface MovingChecklistState {
  [taskId: string]: boolean;
}

function read(): MovingChecklistState {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
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
