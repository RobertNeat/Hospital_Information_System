import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { SummaryList } from './summary-list';

describe('SummaryList', () => {
  it('renders each item label and value', async () => {
    const fixture = TestBed.createComponent(SummaryList);
    fixture.componentRef.setInput('items', [
      { label: 'Imię', value: 'Jan' },
      { label: 'Nazwisko', value: 'Kowalski' },
    ]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Imię');
    expect(el.textContent).toContain('Jan');
    expect(el.textContent).toContain('Kowalski');
  });

  it('renders a dash for a null value', async () => {
    const fixture = TestBed.createComponent(SummaryList);
    fixture.componentRef.setInput('items', [{ label: 'PESEL', value: null }]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('—');
  });
});
