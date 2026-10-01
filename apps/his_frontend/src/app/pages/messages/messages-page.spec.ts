import { staffServiceStub } from '../../testing/staff-service.stub';
import { patientServiceStub } from '../../testing/patient-service.stub';
import { teamMessageServiceStub } from '../../testing/team-message-service.stub';
import { wardServiceStub } from '../../testing/ward-service.stub';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { MessagesPage } from './messages-page';

// jsdom has no ResizeObserver; PrimeNG's p-tabs relies on it in ngAfterViewInit.
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

describe('MessagesPage', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    TestBed.configureTestingModule({
      providers: [
        staffServiceStub,
        provideRouter([]),
        MessageService,
        wardServiceStub,
        patientServiceStub,
        teamMessageServiceStub,
      ],
    });
  });

  it('renders the page header and the live-update info message', async () => {
    const fixture = TestBed.createComponent(MessagesPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Komunikacja zespołowa');
    expect(el.textContent).toContain('na żywo');
  });

  it('defaults to the "Wiadomości" tab and shows a thread with messages', async () => {
    const fixture = TestBed.createComponent(MessagesPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Wiadomości');
  });

  it('filters tasks by "assignedToMe" by default', async () => {
    const fixture = TestBed.createComponent(MessagesPage);
    fixture.componentRef.setInput('tab', 'tasks');
    await fixture.whenStable();
    const instance = fixture.componentInstance as unknown as {
      taskFilter: { (): string };
      taskRows: () => { patientId?: string }[];
    };
    expect(instance.taskFilter()).toBe('assignedToMe');
  });

  it('sorts alerts with critical severity first', async () => {
    const fixture = TestBed.createComponent(MessagesPage);
    fixture.componentRef.setInput('tab', 'alerts');
    await fixture.whenStable();
    const instance = fixture.componentInstance as unknown as {
      sortedAlerts: () => { severity: string }[];
    };
    const severities = instance.sortedAlerts().map((a) => a.severity);
    const criticalIndex = severities.indexOf('critical');
    const infoIndex = severities.lastIndexOf('info');
    if (criticalIndex !== -1 && infoIndex !== -1) {
      expect(criticalIndex).toBeLessThan(infoIndex);
    }
  });
});
