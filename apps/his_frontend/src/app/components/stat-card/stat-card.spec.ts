import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { StatCard } from './stat-card';

describe('StatCard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('renders label and value', async () => {
    const fixture = TestBed.createComponent(StatCard);
    fixture.componentRef.setInput('label', 'Pacjenci na oddziale');
    fixture.componentRef.setInput('value', 12);
    fixture.componentRef.setInput('icon', 'pi pi-users');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Pacjenci na oddziale');
    expect(el.textContent).toContain('12');
  });

  it('renders as a link when link is provided', async () => {
    const fixture = TestBed.createComponent(StatCard);
    fixture.componentRef.setInput('label', 'Pacjenci');
    fixture.componentRef.setInput('value', 5);
    fixture.componentRef.setInput('icon', 'pi pi-users');
    fixture.componentRef.setInput('link', '/patients');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('a')).toBeTruthy();
  });
});
