import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { WizardStepFooter } from './wizard-step-footer';

describe('WizardStepFooter', () => {
  it('emits next when the "Dalej" button is clicked', async () => {
    const fixture = TestBed.createComponent(WizardStepFooter);
    await fixture.whenStable();

    let emitted = false;
    fixture.componentInstance.next.subscribe(() => (emitted = true));

    const buttons = fixture.nativeElement.querySelectorAll('button');
    const nextBtn = Array.from(buttons).find((b) =>
      (b as HTMLButtonElement).textContent?.includes('Dalej'),
    ) as HTMLButtonElement;
    nextBtn.click();
    await fixture.whenStable();

    expect(emitted).toBe(true);
  });

  it('shows the submit label on the last step', async () => {
    const fixture = TestBed.createComponent(WizardStepFooter);
    fixture.componentRef.setInput('last', true);
    fixture.componentRef.setInput('submitLabel', 'Zatwierdź');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Zatwierdź');
  });
});
