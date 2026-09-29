import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { IconActionGroup } from './icon-action-group';

describe('IconActionGroup', () => {
  it('emits the action id when a button is clicked', async () => {
    const fixture = TestBed.createComponent(IconActionGroup);
    fixture.componentRef.setInput('actions', [
      { id: 'lab-order', icon: 'pi pi-eye-dropper', label: 'Zlecenie laboratoryjne' },
    ]);
    await fixture.whenStable();

    let emitted: string | undefined;
    fixture.componentInstance.action.subscribe((id) => (emitted = id));

    const button = fixture.nativeElement.querySelector('p-button button') as HTMLButtonElement;
    button.click();
    await fixture.whenStable();

    expect(emitted).toBe('lab-order');
  });
});
