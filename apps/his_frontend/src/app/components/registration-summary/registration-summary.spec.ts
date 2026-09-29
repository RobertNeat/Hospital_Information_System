import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { RegistrationSummary } from './registration-summary';

describe('RegistrationSummary', () => {
  it('renders each section title and its items', async () => {
    const fixture = TestBed.createComponent(RegistrationSummary);
    fixture.componentRef.setInput('sections', [
      {
        title: 'Identyfikacja',
        items: [{ label: 'PESEL', value: '90010112345' }],
      },
      {
        title: 'Dane osobowe i kontaktowe',
        items: [{ label: 'Imię i nazwisko', value: 'Jan Kowalski' }],
      },
    ]);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Identyfikacja');
    expect(text).toContain('90010112345');
    expect(text).toContain('Dane osobowe i kontaktowe');
    expect(text).toContain('Jan Kowalski');
  });

  it('renders nothing when there are no sections', async () => {
    const fixture = TestBed.createComponent(RegistrationSummary);
    fixture.componentRef.setInput('sections', []);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.his-section').length).toBe(0);
  });
});
