import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { FhirIntegrationNote } from './fhir-integration-note';

describe('FhirIntegrationNote', () => {
  it('mentions the resource name', async () => {
    const fixture = TestBed.createComponent(FhirIntegrationNote);
    fixture.componentRef.setInput('resource', 'ServiceRequest');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('ServiceRequest');
    expect(el.textContent).toContain('tryb demonstracyjny');
  });
});
