import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { FormField } from './form-field';

describe('FormField', () => {
  it('renders the label', async () => {
    const fixture = TestBed.createComponent(FormField);
    fixture.componentRef.setInput('label', 'Imię');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('label')?.textContent).toContain('Imię');
  });

  it('shows the Polish required error after the control is touched', async () => {
    const control = new FormControl('', { nonNullable: true, validators: [Validators.required] });
    const fixture = TestBed.createComponent(FormField);
    fixture.componentRef.setInput('label', 'Imię');
    fixture.componentRef.setInput('control', control);
    await fixture.whenStable();

    control.markAllAsTouched();
    control.updateValueAndValidity();
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('To pole jest wymagane.');
  });
});
