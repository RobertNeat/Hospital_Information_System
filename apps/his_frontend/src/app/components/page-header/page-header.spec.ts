import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { PageHeader } from './page-header';

describe('PageHeader', () => {
  it('renders the title', async () => {
    const fixture = TestBed.createComponent(PageHeader);
    fixture.componentRef.setInput('title', 'Pacjenci');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h1')?.textContent).toContain('Pacjenci');
  });

  it('renders the subtitle when provided', async () => {
    const fixture = TestBed.createComponent(PageHeader);
    fixture.componentRef.setInput('title', 'Pacjenci');
    fixture.componentRef.setInput('subtitle', 'Lista wszystkich pacjentów');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Lista wszystkich pacjentów');
  });
});
