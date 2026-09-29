import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { VitalsEntryForm } from './vitals-entry-form';

describe('VitalsEntryForm', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }, MessageService],
    });
  });

  it('shows a live anomaly hint as the user types an out-of-range value', async () => {
    const fixture = TestBed.createComponent(VitalsEntryForm);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    fixture.componentInstance['form'].controls.spo2.setValue(85);
    await fixture.whenStable();

    const anomaly = fixture.componentInstance['fieldAnomaly']('spo2');
    expect(anomaly?.severity).toBe('critical');
  });

  it('does not flag a physically-maximal but normal value (SpO2 = 100) as an anomaly', async () => {
    const fixture = TestBed.createComponent(VitalsEntryForm);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    fixture.componentInstance['form'].controls.spo2.setValue(100);
    await fixture.whenStable();

    expect(fixture.componentInstance['fieldAnomaly']('spo2')).toBeUndefined();
  });

  it('emits saved with the persisted reading and anomalies on submit', async () => {
    const fixture = TestBed.createComponent(VitalsEntryForm);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    let emitted: unknown;
    fixture.componentInstance.saved.subscribe((v) => (emitted = v));

    fixture.componentInstance['form'].controls.heartRate.setValue(150);
    fixture.componentInstance['submit']();
    await fixture.whenStable();

    expect(emitted).toBeTruthy();
    const result = emitted as { saved: { patientId: string }; anomalies: unknown[] };
    expect(result.saved.patientId).toBe('pat-001');
    expect(result.anomalies.length).toBeGreaterThan(0);
  });

  it('blocks submit when no reading is entered', async () => {
    const fixture = TestBed.createComponent(VitalsEntryForm);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();

    let emitted = false;
    fixture.componentInstance.saved.subscribe(() => (emitted = true));

    fixture.componentInstance['submit']();
    await fixture.whenStable();

    expect(emitted).toBe(false);
  });
});
