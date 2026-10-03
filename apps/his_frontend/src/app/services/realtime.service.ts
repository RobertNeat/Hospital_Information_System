import { DestroyRef, Injectable, InjectionToken, effect, inject, signal } from '@angular/core';
import type { Signal } from '@angular/core';
import { Client, ReconnectionTimeMode } from '@stomp/stompjs';
import type { IMessage, StompConfig, StompSubscription } from '@stomp/stompjs';
import { realtimeWsUrl } from '../config/api.config';
import type { ClinicalAlert, Message, MessageThread, TeamTask } from '../models';
import { AuthService } from './auth.service';
import { StaffService } from './staff.service';
import { TeamMessageService } from './team-message.service';

export type RealtimeStatus = 'connected' | 'connecting' | 'disconnected';

/** The part of the stompjs `Client` used here (replaced by a fake in tests). */
export interface RealtimeClient {
  activate(): void;
  deactivate(): Promise<void>;
  subscribe(destination: string, callback: (message: IMessage) => void): StompSubscription;
}

export const REALTIME_CLIENT_FACTORY = new InjectionToken<(config: StompConfig) => RealtimeClient>(
  'REALTIME_CLIENT_FACTORY',
  { providedIn: 'root', factory: () => (config) => new Client(config) },
);

export const RECONNECT_DELAY_MS = 2_000;
export const MAX_RECONNECT_DELAY_MS = 30_000;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Payload of `/topic/presence` (`staff/events/PresenceChanged`). */
interface PresenceChanged {
  staffId: string;
  online: boolean;
}

/**
 * STOMP push (`/ws`) while signed in; server -> client only (sending goes through REST). The JWT
 * is verified at CONNECT only, so it is read again for every (re)connect. Pushes are applied by
 * `TeamMessageService`; after a reconnect everything is reloaded because events may have been
 * missed. Presence (`/topic/presence`) is applied directly to `StaffService`'s cache.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly auth = inject(AuthService);
  private readonly team = inject(TeamMessageService);
  private readonly staff = inject(StaffService);
  private readonly createClient = inject(REALTIME_CLIENT_FACTORY);

  private client: RealtimeClient | undefined;
  private connectedBefore = false;
  private readonly statusState = signal<RealtimeStatus>('disconnected');

  readonly status: Signal<RealtimeStatus> = this.statusState.asReadonly();

  constructor() {
    effect(() => {
      if (this.auth.isAuthenticated()) this.connect();
      else this.disconnect();
    });
    inject(DestroyRef).onDestroy(() => this.disconnect());
  }

  private connect(): void {
    if (this.client) return;
    this.statusState.set('connecting');
    this.client = this.createClient({
      brokerURL: realtimeWsUrl(window.location),
      reconnectDelay: RECONNECT_DELAY_MS,
      maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      beforeConnect: (client) => {
        const expiresAt = this.auth.expiresAt();
        if (!this.auth.token || (expiresAt && expiresAt.getTime() <= Date.now())) {
          // Expired session: stop retrying with a token the backend would reject.
          void client.deactivate();
          return;
        }
        client.connectHeaders = { Authorization: `Bearer ${this.auth.token}` };
      },
      onConnect: () => this.onConnected(),
      onWebSocketClose: () => {
        if (this.client) this.statusState.set('connecting');
      },
      // ERROR frame (typically a rejected token): let REST decide; a 401 ends the session.
      onStompError: () => {
        if (this.auth.token) this.auth.loadCurrentUser().subscribe({ error: () => undefined });
      },
    });
    this.client.activate();
  }

  private disconnect(): void {
    const client = this.client;
    this.client = undefined;
    this.connectedBefore = false;
    this.statusState.set('disconnected');
    if (client) void client.deactivate();
  }

  private onConnected(): void {
    const client = this.client;
    if (!client) return;
    this.statusState.set('connected');

    client.subscribe('/user/queue/messages', (frame) =>
      this.handle<Message>(frame, (message) => this.team.applyPush({ kind: 'message', message })),
    );
    client.subscribe('/user/queue/threads', (frame) =>
      this.handle<MessageThread>(frame, (thread) =>
        this.team.applyPush({ kind: 'thread', thread }),
      ),
    );
    const onAlert = (frame: IMessage): void =>
      this.handle<ClinicalAlert>(frame, (alert) => this.team.applyPush({ kind: 'alert', alert }));
    client.subscribe('/user/queue/alerts', onAlert);
    client.subscribe('/user/queue/tasks', (frame) =>
      this.handle<TeamTask>(frame, (task) => this.team.applyPush({ kind: 'task', task })),
    );
    client.subscribe('/topic/presence', (frame) =>
      this.handle<PresenceChanged>(frame, (p) => this.staff.applyPresence(p.staffId, p.online)),
    );

    // A denied SUBSCRIBE closes the connection, so only subscribe where the backend allows it.
    const user = this.auth.currentUser();
    if (user && this.auth.hasPermission('alert:read') && UUID.test(user.wardId)) {
      client.subscribe(`/topic/alerts/${user.wardId}`, onAlert);
    }

    if (this.connectedBefore) {
      this.team.refresh();
      this.team.applyPush({ kind: 'resync' });
      // Presence pushes may have been missed while disconnected; reload the cached staff list.
      if (this.staff.staff().length) this.staff.load(true).subscribe({ error: () => undefined });
    }
    this.connectedBefore = true;
  }

  private handle<T>(frame: IMessage, apply: (payload: T) => void): void {
    let payload: T;
    try {
      payload = JSON.parse(frame.body) as T;
    } catch {
      return;
    }
    apply(payload);
  }
}
