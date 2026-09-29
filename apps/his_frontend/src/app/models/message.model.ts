import type { ID, ISODate, ISODateTime, Priority } from './common.model';

export interface MessageThread {
  id: ID;
  participantIds: ID[];
  subject: string;
  patientId?: ID;
  lastMessageAt: ISODateTime;
  unreadCount: number;
}

export interface Message {
  id: ID;
  threadId: ID;
  senderId: ID;
  sentAt: ISODateTime;
  body: string;
  priority: Priority;
  readByIds: ID[];
}

export type TaskStatus = 'open' | 'in_progress' | 'done' | 'cancelled';

export interface TeamTask {
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
  shift: 'day' | 'night';
  fromId: ID;
  toId: ID;
  createdAt: ISODateTime;
  generalNotes?: string;
  patientNotes: HandoffPatientNote[];
}

export type AlertType = 'critical_result' | 'vital_anomaly' | 'order_status' | 'task' | 'system';

export interface ClinicalAlert {
  id: ID;
  type: AlertType;
  severity: 'info' | 'warning' | 'critical';
  patientId?: ID;
  message: string;
  createdAt: ISODateTime;
  acknowledged: boolean;
  acknowledgedById?: ID;
  link?: string;
}
