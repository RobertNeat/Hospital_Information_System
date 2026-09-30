import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ImagingReportView } from './imaging-report-view';
import type { ImagingResult } from '../../models';

describe('ImagingReportView', () => {
  const result: ImagingResult = {
    id: 'img-001',
    patientId: 'pat-001',
    modality: 'CT',
    examName: 'TK głowy bez kontrastu',
    bodyRegion: 'Głowa',
    performedAt: '2026-01-10T09:00:00.000Z',
    reportedAt: '2026-01-10T12:00:00.000Z',
    radiologistName: 'lek. Anna Nowak',
    technique: 'Badanie TK bez kontrastu.',
    findings: 'Bez zmian ogniskowych.',
    conclusion: 'Obraz prawidłowy.',
    status: 'final',
    imageCount: 120,
    critical: false,
  };

  it('renders the Technika / Opis / Wnioski sections', async () => {
    const fixture = TestBed.createComponent(ImagingReportView);
    fixture.componentRef.setInput('result', result);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Technika');
    expect(text).toContain('Opis');
    expect(text).toContain('Wnioski');
    expect(text).toContain('lek. Anna Nowak');
    expect(text).not.toContain('DICOM');
  });
});
