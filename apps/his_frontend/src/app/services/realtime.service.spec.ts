import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { IMessage, StompConfig } from '@stomp/stompjs';
import { AuthService } from './auth.service';
import { REALTIME_CLIENT_FACTORY, RealtimeService } from './realtime.service';
import type { RealtimeClient } from './realtime.service';
import { TeamMessageService } from './team-message.service';

const WARD = '123e4567-e89b-42d3-a456-426614174000';

class FakeClient implements RealtimeClient {
  connectHeaders: Record<string, string> = {};
  subs = new Map<string, (m: IMessage) => void>();
  activate = vi.fn();
  deactivate = vi.fn(() => Promise.resolve());
  constructor(readonly config: StompConfig) {}
  subscribe(destination: string, callback: (m: IMessage) => void) {
    this.subs.set(destination, callback);
    return { id: destination, unsubscribe: vi.fn() };
  }
  /** Mimics stompjs: `beforeConnect` first, then `onConnect` with a fresh subscription set. */
  connect(): void {
    this.subs.clear();
    this.config.beforeConnect?.(this as never);
    this.config.onConnect?.({} as never);
  }
  push(destination: string, payload: unknown): void {
    this.subs.get(destination)?.({ body: JSON.stringify(payload) } as IMessage);
  }
}

describe('RealtimeService', () => {
  const authenticated = signal(false);
  const expiresAt = signal<Date | null>(null);
  let token: string | null;
  let permissions: string[];
  let clients: FakeClient[];
  const team = { refresh: vi.fn(), applyPush: vi.fn() };
  const loadCurrentUser = vi.fn(() => ({ subscribe: vi.fn() }));

  function create(): RealtimeService {
    return TestBed.inject(RealtimeService);
  }

  beforeEach(() => {
    authenticated.set(false);
    expiresAt.set(null);
    token = 'jwt-1';
    permissions = ['alert:read'];
    clients = [];
    team.refresh.mockClear();
    team.applyPush.mockClear();
    loadCurrentUser.mockClear();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: authenticated,
            expiresAt,
            get token() {
              return token;
            },
            currentUser: () => ({ wardId: WARD }),
            hasPermission: (p: string) => permissions.includes(p),
            loadCurrentUser,
          },
        },
        { provide: TeamMessageService, useValue: team },
        {
          provide: REALTIME_CLIENT_FACTORY,
          useValue: (config: StompConfig) => {
            const client = new FakeClient(config);
            clients.push(client);
            return client;
          },
        },
      ],
    });
  });

  it('stays disconnected while signed out', () => {
    const service = create();
    TestBed.tick();
    expect(clients).toHaveLength(0);
    expect(service.status()).toBe('disconnected');
  });

  it('connects after sign-in with the bearer token and a ws URL, then disconnects on sign-out', () => {
    const service = create();
    authenticated.set(true);
    TestBed.tick();
    expect(clients).toHaveLength(1);
    const [client] = clients;
    expect(client.activate).toHaveBeenCalled();
    expect(client.config.brokerURL).toBe(`ws://${window.location.host}/ws`);
    expect(client.config.reconnectDelay).toBeGreaterThan(0);
    expect(service.status()).toBe('connecting');

    client.connect();
    expect(client.connectHeaders).toEqual({ Authorization: 'Bearer jwt-1' });
    expect(service.status()).toBe('connected');

    authenticated.set(false);
    TestBed.tick();
    expect(client.deactivate).toHaveBeenCalled();
    expect(service.status()).toBe('disconnected');
  });

  it('subscribes to the user queues and the ward topic', () => {
    create();
    authenticated.set(true);
    TestBed.tick();
    clients[0].connect();
    expect([...clients[0].subs.keys()].sort()).toEqual(
      [
        '/user/queue/alerts',
        '/user/queue/messages',
        '/user/queue/tasks',
        '/user/queue/threads',
        `/topic/alerts/${WARD}`,
      ].sort(),
    );
  });

  it('skips the ward topic without alert:read', () => {
    permissions = [];
    create();
    authenticated.set(true);
    TestBed.tick();
    clients[0].connect();
    expect(clients[0].subs.has(`/topic/alerts/${WARD}`)).toBe(false);
  });

  it('maps pushed payloads to the team message service and ignores malformed bodies', () => {
    create();
    authenticated.set(true);
    TestBed.tick();
    const [client] = clients;
    client.connect();
    const message = { id: 'm1' };
    client.push('/user/queue/messages', message);
    client.push('/user/queue/threads', { id: 't1' });
    client.push('/user/queue/alerts', { id: 'a1' });
    client.push(`/topic/alerts/${WARD}`, { id: 'a2' });
    client.push('/user/queue/tasks', { id: 'k1' });
    client.subs.get('/user/queue/tasks')?.({ body: 'not json' } as IMessage);
    expect(team.applyPush.mock.calls.map(([p]) => p.kind)).toEqual([
      'message',
      'thread',
      'alert',
      'alert',
      'task',
    ]);
    expect(team.applyPush).toHaveBeenCalledWith({ kind: 'message', message });
  });

  it('refreshes after a reconnect but not after the first connect', () => {
    const service = create();
    authenticated.set(true);
    TestBed.tick();
    const [client] = clients;
    client.connect();
    expect(team.refresh).not.toHaveBeenCalled();

    client.config.onWebSocketClose?.({} as never);
    expect(service.status()).toBe('connecting');
    client.connect();
    expect(service.status()).toBe('connected');
    expect(team.refresh).toHaveBeenCalledTimes(1);
    expect(team.applyPush).toHaveBeenCalledWith({ kind: 'resync' });
  });

  it('stops reconnecting once the token has expired', () => {
    create();
    authenticated.set(true);
    TestBed.tick();
    expiresAt.set(new Date(Date.now() - 1000));
    clients[0].config.beforeConnect?.(clients[0] as never);
    expect(clients[0].deactivate).toHaveBeenCalled();
  });

  it('asks the API about the session after an ERROR frame', () => {
    create();
    authenticated.set(true);
    TestBed.tick();
    clients[0].config.onStompError?.({} as never);
    expect(loadCurrentUser).toHaveBeenCalled();
  });
});
