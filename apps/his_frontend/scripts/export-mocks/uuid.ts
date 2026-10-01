import { createHash } from 'node:crypto';

/**
 * Fixed UUIDv5 namespace of all mock identifiers (`pat-001`, `stf-001`, ...). NEVER change it:
 * every generated primary key (and therefore every Liquibase checksum) is derived from it.
 */
export const MOCK_NAMESPACE = '5d1f0c3a-7b2e-4f6a-9c84-2e0a6b1d3f57';

/** RFC 4122 / RFC 9562 UUIDv5 (SHA-1, name-based), implemented on node:crypto. */
export function uuidv5(name: string, namespace: string = MOCK_NAMESPACE): string {
  const ns = Buffer.from(namespace.replace(/-/g, ''), 'hex');
  const hash = createHash('sha1').update(ns).update(name, 'utf8').digest();
  const b = Buffer.from(hash.subarray(0, 16));
  b[6] = (b[6] & 0x0f) | 0x50;
  b[8] = (b[8] & 0x3f) | 0x80;
  const hex = b.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Registry of generated identifiers: detects duplicate mock ids and dangling references
 * (a FK pointing to an entity that was never defined) while the SQL is being produced.
 */
export class IdRegistry {
  private readonly byKey = new Map<string, string>();
  private readonly used = new Map<string, string>();

  define(table: string, mockId: string): string {
    const key = `${table}:${mockId}`;
    if (this.byKey.has(key)) throw new Error(`Duplicate mock id ${key}`);
    const id = uuidv5(mockId);
    const clash = this.used.get(id);
    if (clash) throw new Error(`UUID clash between ${key} and ${clash}`);
    this.byKey.set(key, id);
    this.used.set(id, key);
    return id;
  }

  ref(table: string, mockId: string): string {
    const id = this.byKey.get(`${table}:${mockId}`);
    if (!id) throw new Error(`Dangling reference to ${table}:${mockId}`);
    return id;
  }

  has(table: string, mockId: string): boolean {
    return this.byKey.has(`${table}:${mockId}`);
  }
}
