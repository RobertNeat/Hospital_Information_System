import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ClinicalNoteDialog } from './clinical-note-dialog';

describe('ClinicalNoteDialog', () => {
  function createFixture() {
    const fixture = TestBed.createComponent(ClinicalNoteDialog);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('patientId', 'pat-001');
    return fixture;
  }

  it('does not emit save when content is shorter than 10 characters', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;

    let saved = false;
    component.save.subscribe(() => (saved = true));

    component['form'].controls.title.setValue('Tytul');
    component['form'].controls.content.setValue('za krotko');
    component['onSave']();

    expect(saved).toBe(false);
    expect(component['form'].controls.content.invalid).toBe(true);
  });

  it('emits save with the note draft when the form is valid', async () => {
    const fixture = createFixture();
    await fixture.whenStable();
    const component = fixture.componentInstance;

    let emitted: unknown;
    component.save.subscribe((event) => (emitted = event));

    component['form'].controls.category.setValue('progress');
    component['form'].controls.title.setValue('Kontrola');
    component['form'].controls.content.setValue('Pacjent czuje się dobrze, bez dolegliwości.');
    component['form'].controls.symptoms.setValue(['Ból głowy']);
    component['onSave']();

    expect(emitted).toEqual({
      draft: {
        patientId: 'pat-001',
        category: 'progress',
        title: 'Kontrola',
        content: 'Pacjent czuje się dobrze, bez dolegliwości.',
        symptoms: ['Ból głowy'],
      },
    });
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
