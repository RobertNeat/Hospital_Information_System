import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { EhrService } from './ehr.service';

describe('EhrService', () => {
  let service: EhrService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    service = TestBed.inject(EhrService);
  });

  it('getDiagnoses filters by patientId', async () => {
    const result = await firstValueFrom(service.getDiagnoses('pat-001'));
    expect(result.every((d) => d.patientId === 'pat-001')).toBe(true);
    expect(result.length).toBeGreaterThan(0);
  });

  it('getAllergies filters by patientId', async () => {
    const result = await firstValueFrom(service.getAllergies('pat-001'));
    expect(result.some((a) => a.substance === 'Penicylina')).toBe(true);
  });

  it('addNote persists a new clinical note with a generated id and createdAt', async () => {
    const before = await firstValueFrom(service.getNotes('pat-003'));
    const note = await firstValueFrom(
      service.addNote({
        patientId: 'pat-003',
        authorId: 'stf-001',
        category: 'progress',
        title: 'Test note',
        content: 'Treść notatki testowej o wystarczającej długości.',
      }),
    );
    expect(note.id).toMatch(/^note-\d+$/);
    expect(note.createdAt).toBeTruthy();

    const after = await firstValueFrom(service.getNotes('pat-003'));
    expect(after.length).toBe(before.length + 1);
  });

  it('getIcd10Dictionary returns ~40 ICD-10 codes', async () => {
    const dict = await firstValueFrom(service.getIcd10Dictionary());
    expect(dict.length).toBeGreaterThanOrEqual(30);
    expect(dict.every((c) => c.system === 'ICD-10')).toBe(true);
  });

  it('getSummary combines diagnoses, encounters, allergies and active medications', async () => {
    const summary = await firstValueFrom(service.getSummary('pat-001'));
    expect(summary.recentDiagnoses.length).toBeGreaterThan(0);
    expect(summary.allergies.length).toBeGreaterThan(0);
    expect(summary.chronicConditions.every((d) => d.type === 'chronic')).toBe(true);
  });
});
