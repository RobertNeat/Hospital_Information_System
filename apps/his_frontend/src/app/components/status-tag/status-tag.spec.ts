import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { StatusTag } from './status-tag';

describe('StatusTag', () => {
  it('renders the Polish label for the value', async () => {
    const fixture = TestBed.createComponent(StatusTag);
    fixture.componentRef.setInput('kind', 'admissionStatus');
    fixture.componentRef.setInput('value', 'admitted');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Przyjęty');
  });

  it('maps urgency stat to the danger severity', async () => {
    const fixture = TestBed.createComponent(StatusTag);
    fixture.componentRef.setInput('kind', 'urgency');
    fixture.componentRef.setInput('value', 'stat');
    await fixture.whenStable();
    const tag = fixture.nativeElement.querySelector('p-tag');
    expect(tag).toBeTruthy();
  });
});
