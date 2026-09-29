import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ReportDownloadService } from './report-download.service';
import type { LabResult, Patient } from '../models';

const PATIENT: Patient = {
  id: 'pat-001',
  mrn: 'HIS/2026/000001',
  pesel: '68031437976',
  firstName: 'Jan',
  lastName: 'Kowalski',
  birthDate: '1968-03-14',
  gender: 'male',
  address: {
    street: 'A',
    buildingNumber: '1',
    postalCode: '00-001',
    city: 'Warszawa',
    country: 'Polska',
  },
  insurance: { status: 'active', nfzBranch: '07', payer: 'NFZ' },
  status: 'admitted',
  flags: [],
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
};

const RESULT: LabResult = {
  id: 'lres-test',
  patientId: 'pat-001',
  testCode: 'MORF',
  testName: 'Morfologia krwi z rozmazem',
  category: 'hematology',
  collectedAt: '2026-01-01T08:00:00.000Z',
  resultedAt: '2026-01-01T10:00:00.000Z',
  status: 'final',
  observations: [
    {
      analyteCode: 'HGB',
      analyteName: 'Hemoglobina',
      value: 13.5,
      unit: 'g/dL',
      referenceRange: { low: 12, high: 16 },
      flag: 'N',
    },
  ],
  performerName: 'Test',
};

describe('ReportDownloadService', () => {
  let service: ReportDownloadService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ReportDownloadService);

    if (!URL.createObjectURL) {
      URL.createObjectURL = vi.fn(() => 'blob:mock');
    }
    if (!URL.revokeObjectURL) {
      URL.revokeObjectURL = vi.fn();
    }
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  });

  it('downloadLabReport triggers an anchor click without throwing', () => {
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    expect(() => service.downloadLabReport(RESULT, PATIENT)).not.toThrow();
    expect(clickSpy).toHaveBeenCalled();
    clickSpy.mockRestore();
  });

  it('downloadCsv builds a CSV blob and triggers a download', () => {
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    expect(() => service.downloadCsv('test.csv', [{ a: 1, b: 'x' }])).not.toThrow();
    expect(clickSpy).toHaveBeenCalled();
    clickSpy.mockRestore();
  });
});
