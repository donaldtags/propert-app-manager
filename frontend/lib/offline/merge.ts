/**
 * Conflict rule for editable records (see docs/estatex-integration-plan.md §3.6):
 * compare what the device started from (base), what it wants (mine) and what the server has now
 * (theirs), field by field:
 *   - server unchanged since base          -> apply mine
 *   - server already equals mine           -> nothing to do
 *   - server changed it to something else  -> SERVER WINS; mine goes to the conflicts tray
 */
export interface FieldConflict<T> {
  field: keyof T & string;
  base: unknown;
  mine: unknown;
  theirs: unknown;
}

export interface MergeResult<T> {
  /** The full record to send: theirs with my non-conflicting changes applied. */
  merged: T;
  /** Fields where my change will actually be applied. */
  applied: (keyof T & string)[];
  conflicts: FieldConflict<T>[];
}

const same = (a: unknown, b: unknown) => (a ?? null) === (b ?? null);

export function threeWayMerge<T extends Record<string, unknown>>(base: T, mine: T, theirs: T): MergeResult<T> {
  const merged = { ...theirs };
  const applied: (keyof T & string)[] = [];
  const conflicts: FieldConflict<T>[] = [];
  for (const field of Object.keys(mine) as (keyof T & string)[]) {
    if (same(mine[field], base[field])) continue; // I didn't change it
    if (same(theirs[field], mine[field])) continue; // already as I want
    if (same(theirs[field], base[field])) {
      merged[field] = mine[field];
      applied.push(field);
    } else {
      conflicts.push({ field, base: base[field], mine: mine[field], theirs: theirs[field] });
    }
  }
  return { merged, applied, conflicts };
}
