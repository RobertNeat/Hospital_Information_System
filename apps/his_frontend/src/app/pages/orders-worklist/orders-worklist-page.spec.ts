import { labOrderServiceStub } from '../../testing/lab-order-service.stub';
import { patientServiceStub } from '../../testing/patient-service.stub';
import { ehrServiceStub } from '../../testing/ehr-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { MOCK_LATENCY_MS } from '../../config/mock-api.config';
import { OrdersWorklistPage } from './orders-worklist-page';

describe('OrdersWorklistPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        patientServiceStub,
        ehrServiceStub,
        wardServiceStub,
        provideRouter([]),
        MessageService,
        labOrderServiceStub,
        { provide: MOCK_LATENCY_MS, useValue: 0 },
      ],
    });
  });

  it('renders the page header and worklist rows once loaded', async () => {
    const fixture = TestBed.createComponent(OrdersWorklistPage);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Zlecenia');
    expect(text).toContain('Zlecone:');
  });

  it('filters rows by the type query param', async () => {
    const fixture = TestBed.createComponent(OrdersWorklistPage);
    fixture.componentRef.setInput('type', 'lab');
    await fixture.whenStable();
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    expect(rows.length).toBeGreaterThan(0);
  });
});
