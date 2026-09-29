import { describe, expect, it, vi } from 'vitest';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { tryAdvance } from './wizard';

describe('tryAdvance', () => {
  it('advances and returns true when the form is valid', () => {
    const form = new FormGroup({ name: new FormControl('Jan', Validators.required) });
    const activate = vi.fn();
    const result = tryAdvance(form, activate, 2);
    expect(result).toBe(true);
    expect(activate).toHaveBeenCalledWith(2);
  });

  it('does not advance and returns false when the form is invalid', () => {
    const form = new FormGroup({ name: new FormControl('', Validators.required) });
    const activate = vi.fn();
    const result = tryAdvance(form, activate, 2);
    expect(result).toBe(false);
    expect(activate).not.toHaveBeenCalled();
  });

  it('marks all controls as touched even when invalid', () => {
    const control = new FormControl('', Validators.required);
    const form = new FormGroup({ name: control });
    tryAdvance(form, vi.fn(), 2);
    expect(control.touched).toBe(true);
  });
});
