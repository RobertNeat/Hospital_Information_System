import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { DiagnosisDialog } from './diagnosis-dialog';

describe('DiagnosisDialog', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ehrServiceStub] });
  });

  function createFixture() {
    const fixture = TestBed.createComponent(DiagnosisDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('patientId', 'pat-001');
    fixture.componentRef.setInput('diagnoses', []);
    return fixture;
  }

  it('does not emit save without a selected SNOMED code', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;

    let saved = false;
    component.save.subscribe(() => (saved = true));

    component['onSave']();

    expect(saved).toBe(false);
    expect(component['form'].controls.code.invalid).toBe(true);
  });

  it('emits save with the SNOMED coding from the selected suggestion and no date when unset', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;

    const option = component['diagnosisOptions']()[0];
    expect(option).toBeTruthy();

    let emitted: unknown;
    component.save.subscribe((event) => (emitted = event));

    component['form'].controls.code.setValue(option.value);
    component['form'].controls.type.setValue('primary');
    component['form'].controls.status.setValue('active');
    component['onSave']();

    expect(emitted).toEqual({
      draft: {
        patientId: 'pat-001',
        code: option.coding,
        type: 'primary',
        status: 'active',
        diagnosedAt: undefined,
        notes: undefined,
      },
    });
    expect(option.coding.system).toBe('SNOMED');
  });

  it('converts a chosen date to an ISO instant', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;
    const option = component['diagnosisOptions']()[0];

    let emitted: { draft: { diagnosedAt?: string } } | undefined;
    component.save.subscribe((event) => (emitted = event));

    const date = new Date('2026-01-15T10:00:00Z');
    component['form'].controls.code.setValue(option.value);
    component['form'].controls.diagnosedAt.setValue(date);
    component['onSave']();

    expect(emitted?.draft.diagnosedAt).toBe(date.toISOString());
  });

  it('emits visibleChange(false) on cancel', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;

    let visible: boolean | undefined;
    component.visibleChange.subscribe((v) => (visible = v));
    component['onCancel']();

    expect(visible).toBe(false);
  });
});
