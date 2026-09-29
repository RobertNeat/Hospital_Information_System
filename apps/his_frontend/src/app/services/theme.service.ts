import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import type { Signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'his.theme';
const DARK_CLASS = 'app-dark';

/**
 * Light/dark mode switch (ADDENDUM B.3). Applies/removes the `.app-dark` class on
 * `<html>` (matching `darkModeSelector: '.app-dark'` configured in `providePrimeNG`,
 * Phase 0b's job in app.config.ts) and persists the choice in `localStorage`.
 * Falls back to `prefers-color-scheme` when nothing is stored yet.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly _mode = signal<ThemeMode>(this.readInitialMode());
  readonly mode: Signal<ThemeMode> = this._mode.asReadonly();

  constructor() {
    this.applyMode(this._mode());
  }

  toggle(): void {
    this.setMode(this._mode() === 'dark' ? 'light' : 'dark');
  }

  setMode(mode: ThemeMode): void {
    this._mode.set(mode);
    this.applyMode(mode);
    this.persist(mode);
  }

  private applyMode(mode: ThemeMode): void {
    const root = this.document.documentElement;
    root.classList.toggle(DARK_CLASS, mode === 'dark');
  }

  private persist(mode: ThemeMode): void {
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      /* ignore storage errors (private mode, SSR, etc.) */
    }
  }

  private readInitialMode(): ThemeMode {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'light' || stored === 'dark') return stored;
    } catch {
      /* ignore storage errors */
    }
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return 'light';
  }
}
