import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OrderStatusTimeline } from './order-status-timeline';

describe('OrderStatusTimeline', () => {
  it('renders status history entries, newest first', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(OrderStatusTimeline);
    fixture.componentRef.setInput('history', [
      { status: 'ordered', at: '2026-01-01T08:00:00.000Z', byId: 'stf-001' },
      { status: 'completed', at: '2026-01-02T08:00:00.000Z', note: 'Gotowe' },
    ]);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Gotowe');
    const orderedIndex = text.indexOf('Zlecone');
    const completedIndex = text.indexOf('Zakończone');
    expect(completedIndex).toBeGreaterThanOrEqual(0);
    expect(orderedIndex).toBeGreaterThan(completedIndex);
  });
});
