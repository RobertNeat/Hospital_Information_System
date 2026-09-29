import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { PatientContextService } from './patient-context.service';
import { PatientService } from './patient.service';

describe('PatientContextService', () => {
  let ctx: PatientContextService;
  let patientService: PatientService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    ctx = TestBed.inject(PatientContextService);
    patientService = TestBed.inject(PatientService);
  });

  it('has no patient initially when sessionStorage is empty', () => {
    expect(ctx.hasPatient()).toBe(false);
    expect(ctx.patientId()).toBeNull();
  });

  it('setPatient updates patient, patientId, hasPatient and recentPatients', async () => {
    const patient = await firstValueFrom(patientService.getPatientById('pat-001'));
    ctx.setPatient(patient);

    expect(ctx.hasPatient()).toBe(true);
    expect(ctx.patientId()).toBe('pat-001');
    expect(ctx.patient()?.lastName).toBe('Kowalski');
    expect(ctx.recentPatients().some((p) => p.id === 'pat-001')).toBe(true);
  });

  it('setPatient writes the id to sessionStorage', async () => {
    const patient = await firstValueFrom(patientService.getPatientById('pat-002'));
    ctx.setPatient(patient);
    expect(sessionStorage.getItem('his.currentPatientId')).toBe('pat-002');
  });

  it('recentPatients keeps at most 5 and moves re-selected patients to the front', async () => {
    const ids = ['pat-001', 'pat-002', 'pat-003', 'pat-004', 'pat-005', 'pat-006'];
    for (const id of ids) {
      const p = await firstValueFrom(patientService.getPatientById(id));
      ctx.setPatient(p);
    }
    expect(ctx.recentPatients()).toHaveLength(5);
    expect(ctx.recentPatients()[0].id).toBe('pat-006');
  });

  it('clear resets the context and sessionStorage', async () => {
    const patient = await firstValueFrom(patientService.getPatientById('pat-001'));
    ctx.setPatient(patient);
    ctx.clear();
    expect(ctx.hasPatient()).toBe(false);
    expect(sessionStorage.getItem('his.currentPatientId')).toBeNull();
  });

  it('refresh reloads the current patient from PatientService', async () => {
    const patient = await firstValueFrom(patientService.getPatientById('pat-001'));
    ctx.setPatient(patient);
    await firstValueFrom(patientService.updatePatient('pat-001', { phone: '+48 999 999 999' }));

    const refreshed = await firstValueFrom(ctx.refresh());
    expect(refreshed.phone).toBe('+48 999 999 999');
    expect(ctx.patient()?.phone).toBe('+48 999 999 999');
  });
});
