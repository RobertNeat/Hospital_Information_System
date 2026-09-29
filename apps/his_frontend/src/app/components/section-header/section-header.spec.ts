import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { SectionHeader } from './section-header';

describe('SectionHeader', () => {
  it('renders the title and count', async () => {
    const fixture = TestBed.createComponent(SectionHeader);
    fixture.componentRef.setInput('title', 'Dane podstawowe');
    fixture.componentRef.setInput('count', 3);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Dane podstawowe');
    expect(el.textContent).toContain('3');
  });
});
