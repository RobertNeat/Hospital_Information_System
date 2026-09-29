import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ConfirmationService } from 'primeng/api';
import { firstValueFrom, isObservable, type Observable } from 'rxjs';
import { unsavedChangesGuard, type HasUnsavedChanges } from './unsaved-changes.guard';

describe('unsavedChangesGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ConfirmationService] });
  });

  it('returns true immediately when there are no unsaved changes', () => {
    const component: HasUnsavedChanges = { hasUnsavedChanges: () => false };
    const result = TestBed.runInInjectionContext(() =>
      unsavedChangesGuard(component, {} as never, {} as never, {} as never),
    );
    expect(result).toBe(true);
  });

  it('resolves true when the user confirms leaving', async () => {
    const component: HasUnsavedChanges = { hasUnsavedChanges: () => true };
    const confirmationService = TestBed.inject(ConfirmationService);
    confirmationService.confirm = (opts) => {
      opts.accept?.();
      return confirmationService;
    };

    const result = TestBed.runInInjectionContext(() =>
      unsavedChangesGuard(component, {} as never, {} as never, {} as never),
    );
    expect(isObservable(result)).toBe(true);
    const resolved = await firstValueFrom(result as Observable<boolean>);
    expect(resolved).toBe(true);
  });

  it('resolves false when the user rejects leaving', async () => {
    const component: HasUnsavedChanges = { hasUnsavedChanges: () => true };
    const confirmationService = TestBed.inject(ConfirmationService);
    confirmationService.confirm = (opts) => {
      opts.reject?.();
      return confirmationService;
    };

    const result = TestBed.runInInjectionContext(() =>
      unsavedChangesGuard(component, {} as never, {} as never, {} as never),
    );
    expect(isObservable(result)).toBe(true);
    const resolved = await firstValueFrom(result as Observable<boolean>);
    expect(resolved).toBe(false);
  });
});
