import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { PatientChartNav } from './patient-chart-nav';

describe('PatientChartNav', () => {
  let permissions: string[];

  beforeEach(() => {
    permissions = [
      'ehr:read',
      'lab-result:read',
      'imaging-result:read',
      'vitals:read',
      'lab-order:read',
      'imaging-order:read',
      'prescription:read',
    ];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { hasPermission: (p: string) => permissions.includes(p) },
        },
      ],
    });
  });

  it('renders a link for every clinical module a role has access to', async () => {
    const fixture = TestBed.createComponent(PatientChartNav);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const links = fixture.nativeElement.querySelectorAll('a');
    expect(links.length).toBe(6);
    expect((links[0] as HTMLAnchorElement).getAttribute('href')).toContain(
      '/patients/pat-001/overview',
    );
  });

  it('hides tabs the role has no permission for (e.g. registrar: no ehr/vitals/orders/prescriptions)', async () => {
    permissions = [];
    const fixture = TestBed.createComponent(PatientChartNav);
    fixture.componentRef.setInput('patientId', 'pat-001');
    await fixture.whenStable();
    const links = fixture.nativeElement.querySelectorAll('a');
    // Only "Dane pacjenta" has no requiresAnyOf, so it's always shown.
    expect(links.length).toBe(1);
    expect((links[0] as HTMLAnchorElement).getAttribute('href')).toContain(
      '/patients/pat-001/overview',
    );
  });
});
