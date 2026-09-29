import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { LoadingBlock } from './loading-block';

describe('LoadingBlock', () => {
  it('renders the default 3 skeleton lines', async () => {
    const fixture = TestBed.createComponent(LoadingBlock);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('p-skeleton').length).toBe(3);
  });

  it('renders a custom number of lines', async () => {
    const fixture = TestBed.createComponent(LoadingBlock);
    fixture.componentRef.setInput('lines', 5);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('p-skeleton').length).toBe(5);
  });
});
