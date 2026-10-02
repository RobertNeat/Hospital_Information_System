import { mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { build, type Mocks } from './build';
import { HEADER, renderFile } from './sql';
import { T0 } from './time';
import { MOCK_NAMESPACE, uuidv5 } from './uuid';

const here = dirname(fileURLToPath(import.meta.url));
const BACKEND = join(here, '../../../his_backend');
const CHANGELOG_DIR = join(BACKEND, 'src/main/resources/db/changelog');
const MANIFEST_FILE = join(BACKEND, 'src/test/resources/db/mock-manifest.json');

/**
 * Loads the mocks AFTER the clock is pinned: daysAgo/hoursAgo/isoAt call `new Date()` at import time.
 * Static imports would be hoisted above `vi.setSystemTime`, hence `await import`.
 */
async function loadMocks(): Promise<Mocks> {
  const dir = '../../src/app/mock-data';
  const [
    staff,
    wards,
    patients,
    episodes,
    encounters,
    notes,
    diagnoses,
    allergies,
    contraindications,
    treatments,
    labCatalog,
    labOrders,
    labResults,
    imagingCatalog,
    imagingOrders,
    imagingResults,
    slots,
    drugs,
    prescriptions,
    vitals,
    threads,
    messages,
    alerts,
    tasks,
    handoff,
    thresholds,
  ] = await Promise.all([
    import(`${dir}/staff.mock`),
    import(`${dir}/wards.mock`),
    import(`${dir}/patients.mock`),
    import(`${dir}/episodes.mock`),
    import(`${dir}/encounters.mock`),
    import(`${dir}/clinical-notes.mock`),
    import(`${dir}/diagnoses.mock`),
    import(`${dir}/allergies.mock`),
    import(`${dir}/contraindications.mock`),
    import(`${dir}/treatments.mock`),
    import(`${dir}/lab-catalog.mock`),
    import(`${dir}/lab-orders.mock`),
    import(`${dir}/lab-results.mock`),
    import(`${dir}/imaging-catalog.mock`),
    import(`${dir}/imaging-orders.mock`),
    import(`${dir}/imaging-results.mock`),
    import(`${dir}/schedule-slots.mock`),
    import(`${dir}/drugs.mock`),
    import(`${dir}/prescriptions.mock`),
    import(`${dir}/vitals.mock`),
    import(`${dir}/message-threads.mock`),
    import(`${dir}/messages.mock`),
    import(`${dir}/alerts.mock`),
    import(`${dir}/tasks.mock`),
    import(`${dir}/handoff-notes.mock`),
    import(`${dir}/vital-thresholds.mock`),
  ]);
  return {
    STAFF: staff.STAFF,
    WARDS: wards.WARDS,
    PATIENTS: patients.PATIENTS,
    EPISODES: episodes.EPISODES,
    ENCOUNTERS: encounters.ENCOUNTERS,
    CLINICAL_NOTES: notes.CLINICAL_NOTES,
    DIAGNOSES: diagnoses.DIAGNOSES,
    ALLERGIES: allergies.ALLERGIES,
    CONTRAINDICATIONS: contraindications.CONTRAINDICATIONS,
    TREATMENTS: treatments.TREATMENTS,
    LAB_CATALOG: labCatalog.LAB_CATALOG,
    LAB_PANELS: labCatalog.LAB_PANELS,
    LAB_ORDERS: labOrders.LAB_ORDERS,
    LAB_RESULTS: labResults.LAB_RESULTS,
    IMAGING_CATALOG: imagingCatalog.IMAGING_CATALOG,
    IMAGING_ORDERS: imagingOrders.IMAGING_ORDERS,
    IMAGING_RESULTS: imagingResults.IMAGING_RESULTS,
    generateSlots: slots.generateSlots,
    DRUGS: drugs.DRUGS,
    PRESCRIPTIONS: prescriptions.PRESCRIPTIONS,
    VITALS: vitals.VITALS,
    VITAL_THRESHOLDS: thresholds.VITAL_THRESHOLDS,
    MESSAGE_THREADS: threads.MESSAGE_THREADS,
    MESSAGES: messages.MESSAGES,
    ALERTS: alerts.ALERTS,
    TASKS: tasks.TASKS,
    HANDOFF_NOTES: handoff.HANDOFF_NOTES,
  };
}

/** Removes previously generated files (recognised by the header) so renamed/dropped ones do not linger. */
function cleanGenerated(dir: string): void {
  mkdirSync(dir, { recursive: true });
  for (const name of readdirSync(dir)) {
    if (!name.endsWith('.sql')) continue;
    const path = join(dir, name);
    if (readFileSync(path, 'utf8').split('\n')[1] === HEADER) rmSync(path);
  }
}

describe('export-mocks', () => {
  beforeAll(() => {
    vi.setSystemTime(T0);
  });
  afterAll(() => {
    vi.useRealTimers();
  });

  it('runs in Europe/Warsaw (daylight saving time aware)', () => {
    expect(new Date(2026, 0, 15).getTimezoneOffset()).toBe(-60);
    expect(new Date(2026, 6, 15).getTimezoneOffset()).toBe(-120);
    expect(new Date().toISOString()).toBe(T0.toISOString());
  });

  it('uuidv5 matches the RFC 4122 reference vector', () => {
    const dns = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';
    expect(uuidv5('python.org', dns)).toBe('886313e1-3b8a-5372-9b90-0c9aee199e5d');
    expect(uuidv5('pat-001')).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    expect(MOCK_NAMESPACE).toBe('5d1f0c3a-7b2e-4f6a-9c84-2e0a6b1d3f57');
  });

  it('generates Liquibase changelogs and the manifest from the frontend mocks', async () => {
    const mocks = await loadMocks();
    const { files, manifest } = build(mocks);

    for (const sub of ['reference', 'mock']) cleanGenerated(join(CHANGELOG_DIR, sub));
    for (const file of files) {
      const content = renderFile(file);
      expect(content.startsWith('--liquibase formatted sql\n')).toBe(true);
      expect(content).not.toContain('\r');
      writeFileSync(join(CHANGELOG_DIR, file.path), content, 'utf8');
    }

    const sorted = (o: Record<string, number>) =>
      Object.fromEntries(Object.entries(o).sort(([a], [b]) => a.localeCompare(b)));
    const json = JSON.stringify(
      {
        _comment: 'GENERATED by apps/his_frontend/scripts/export-mocks, do not edit',
        reference: sorted(manifest.reference),
        mock: sorted(manifest.mock),
      },
      null,
      2,
    );
    mkdirSync(dirname(MANIFEST_FILE), { recursive: true });
    writeFileSync(MANIFEST_FILE, `${json}\n`, 'utf8');

    expect(manifest.reference['vital_threshold']).toBe(6);
    expect(files.length).toBeGreaterThan(10);
  });
});
