import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { EmptyState } from './empty-state';

describe('EmptyState', () => {
  it('renders title and message', async () => {
    const fixture = TestBed.createComponent(EmptyState);
    fixture.componentRef.setInput('title', 'W przygotowaniu');
    fixture.componentRef.setInput('message', 'Ta funkcja pojawi się wkrótce.');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('W przygotowaniu');
    expect(el.textContent).toContain('Ta funkcja pojawi się wkrótce.');
  });
});
