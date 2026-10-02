import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { AllergyDialog } from './allergy-dialog';

describe('AllergyDialog', () => {
  function createFixture() {
    const fixture = TestBed.createComponent(AllergyDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('patientId', 'pat-001');
    return fixture;
  }

  it('does not emit save when required fields are missing', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;

    let saved = false;
    component.save.subscribe(() => (saved = true));

    component['form'].controls.substance.setValue('');
    component['form'].controls.reaction.setValue('');
    component['onSave']();

    expect(saved).toBe(false);
    expect(component['form'].invalid).toBe(true);
  });

  it('emits save with the allergy draft, parsing comma-separated ATC codes', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;

    let emitted: unknown;
    component.save.subscribe((event) => (emitted = event));

    component['form'].controls.substance.setValue('Penicylina');
    component['form'].controls.category.setValue('drug');
    component['form'].controls.reaction.setValue('Wysypka, obrzęk');
    component['form'].controls.severity.setValue('severe');
    component['form'].controls.status.setValue('active');
    component['form'].controls.atcCodes.setValue(' j01ce01 , j01ce02');
    component['onSave']();

    expect(emitted).toEqual({
      draft: {
        patientId: 'pat-001',
        substance: 'Penicylina',
        category: 'drug',
        reaction: 'Wysypka, obrzęk',
        severity: 'severe',
        status: 'active',
        recordedAt: undefined,
        atcCodes: ['J01CE01', 'J01CE02'],
      },
    });
  });

  it('omits atcCodes when left blank', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;

    let emitted: { draft: { atcCodes?: string[] } } | undefined;
    component.save.subscribe((event) => (emitted = event));

    component['form'].controls.substance.setValue('Pyłki traw');
    component['form'].controls.category.setValue('environment');
    component['form'].controls.reaction.setValue('Katar');
    component['form'].controls.severity.setValue('mild');
    component['onSave']();

    expect(emitted?.draft.atcCodes).toBeUndefined();
  });

  it('only offers active/inactive as write statuses', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const values = fixture.componentInstance['statusOptions'].map((o) => o.value);

    expect(values).toEqual(['active', 'inactive']);
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
