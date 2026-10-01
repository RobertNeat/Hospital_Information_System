import type { Auditable, ID, ISODate, ISODateTime, Priority, Versioned } from './common.model';

/**
 * Documentation model (target table `thread_participant`): membership of a staff member in a
 * thread plus their read cursor. `MessageThread.participantIds` stays in TS as a projection.
 */
export interface ThreadParticipant {
  threadId: ID;
  staffId: ID;
  /** Everything sent up to this instant counts as read by `staffId`. */
  lastReadAt?: ISODateTime;
  joinedAt: ISODateTime;
}

export interface MessageThread {
  id: ID;
  participantIds: ID[];
  subject: string;
  patientId?: ID;
  /** Filled by the backend from the session. */
  createdById?: ID;
  lastMessageAt: ISODateTime;
  /**
   * @viewerScoped Number of messages newer than `ThreadParticipant.lastReadAt` of the
   * authenticated user.
   */
  unreadCount: number;
}

export interface Message {
  id: ID;
  threadId: ID;
  senderId: ID;
  sentAt: ISODateTime;
  body: string;
  priority: Priority;
  /**
   * @projection Who has read the message; to be derived from `ThreadParticipant.lastReadAt`.
   * @deprecated Direction: per-user read state lives in `ThreadParticipant`.
   */
  readByIds: ID[];
}
/* `Message` does not extend `Partial<Auditable>`: `sentAt`/`senderId` already play that role
   and adding audit fields would duplicate them. */

export type TaskStatus = 'open' | 'in_progress' | 'done' | 'cancelled';

export interface TeamTask extends Partial<Auditable>, Versioned {
  id: ID;
  title: string;
  description?: string;
  patientId?: ID;
  assignedToId: ID;
  createdById: ID;
  createdAt: ISODateTime;
  dueAt?: ISODateTime;
  priority: Priority;
  status: TaskStatus;
}

export type ShiftType = 'day' | 'night';

/** SBAR note for a single patient inside a handoff. */
export interface HandoffPatientNote {
  patientId: ID;
  situation: string;
  background: string;
  assessment: string;
  recommendation: string;
}

export interface HandoffNote {
  id: ID;
  wardId: ID;
  shiftDate: ISODate;
  shift: ShiftType;
  fromId: ID;
  toId: ID;
  createdAt: ISODateTime;
  generalNotes?: string;
  patientNotes: HandoffPatientNote[];
}

export type AlertType = 'critical_result' | 'vital_anomaly' | 'order_status' | 'task' | 'system';

export type AlertSeverity = 'info' | 'warning' | 'critical';

export type AlertTargetKind =
  | 'lab_result'
  | 'imaging_result'
  | 'patient_vitals'
  | 'lab_order'
  | 'imaging_order'
  | 'task'
  | 'patient';

/** Typed pointer to the entity an alert is about (the UI derives its route from it). */
export interface AlertTarget {
  kind: AlertTargetKind;
  id: ID;
  patientId?: ID;
}

export interface ClinicalAlert {
  id: ID;
  type: AlertType;
  severity: AlertSeverity;
  patientId?: ID;
  message: string;
  createdAt: ISODateTime;
  /** @viewerScoped Whether the authenticated user has acknowledged the alert. */
  acknowledged: boolean;
  acknowledgedById?: ID;
  acknowledgedAt?: ISODateTime;
  /** The UI derives its route from the target (`alertRoute`); the backend does not know UI routes. */
  target?: AlertTarget;
}
