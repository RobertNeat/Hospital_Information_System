import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('app-dark');
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    document.documentElement.classList.remove('app-dark');
    vi.restoreAllMocks();
  });

  it('defaults to light mode when nothing is stored and matchMedia is unavailable/false', () => {
    const service = TestBed.inject(ThemeService);
    expect(service.mode()).toBe('light');
    expect(document.documentElement.classList.contains('app-dark')).toBe(false);
  });

  it('reads a stored mode from localStorage on init', () => {
    localStorage.setItem('his.theme', 'dark');
    const service = TestBed.inject(ThemeService);
    expect(service.mode()).toBe('dark');
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
  });

  it('toggle flips the mode and the app-dark class, and persists', () => {
    const service = TestBed.inject(ThemeService);
    expect(service.mode()).toBe('light');

    service.toggle();
    expect(service.mode()).toBe('dark');
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
    expect(localStorage.getItem('his.theme')).toBe('dark');

    service.toggle();
    expect(service.mode()).toBe('light');
    expect(document.documentElement.classList.contains('app-dark')).toBe(false);
    expect(localStorage.getItem('his.theme')).toBe('light');
  });

  it('setMode sets an explicit mode', () => {
    const service = TestBed.inject(ThemeService);
    service.setMode('dark');
    expect(service.mode()).toBe('dark');
  });
});
