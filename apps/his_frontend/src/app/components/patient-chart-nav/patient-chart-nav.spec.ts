import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PatientChartNav } from './patient-chart-nav';

describe('PatientChartNav', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('renders a link for every clinical module', async () => {
    const fixture = TestBed.createComponent(PatientChartNav);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const links = fixture.nativeElement.querySelectorAll('a');
    expect(links.length).toBe(6);
    expect((links[0] as HTMLAnchorElement).getAttribute('href')).toContain(
      '/patients/pat-001/overview',
    );
  });
});
