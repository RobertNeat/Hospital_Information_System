import type { ID, ISODate, Priority } from '../common.model';
import type { HandoffNote, TaskStatus, TeamTask } from '../message.model';
import type { PageQuery } from './common.api';

/** The backend lists only the threads of the authenticated user (taken from the session). */
export interface ThreadQuery extends PageQuery {
  patientId?: ID;
}

export interface ThreadCreateRequest {
  participantIds: ID[];
  subject: string;
  patientId?: ID;
  firstMessage: { body: string; priority: Priority };
}

export interface MessageSendRequest {
  body: string;
  priority: Priority;
}

/** Empty body: the user whose read cursor moves comes from the session. */
export type ThreadMarkReadRequest = Record<string, never>;

export interface TaskQuery {
  assignedToId?: ID;
  createdById?: ID;
  status?: TaskStatus;
  patientId?: ID;
}

/** A new task is always `open`; the creator comes from the token. */
export type TaskCreateRequest = Omit<
  TeamTask,
  'id' | 'createdAt' | 'createdById' | 'status' | 'updatedAt' | 'updatedById' | 'version'
>;

/** Only the assignee or the creator may change the status (403 otherwise); 409 on a stale `version`. */
export interface TaskStatusUpdateRequest {
  status: TaskStatus;
  version?: number;
}

/** `fromId` comes from the token. */
export type HandoffNoteCreateRequest = Omit<HandoffNote, 'id' | 'createdAt' | 'fromId'>;

export interface HandoffNoteQuery {
  wardId?: ID;
  /** `YYYY-MM-DD`. */
  shiftDate?: ISODate;
}

export interface AlertQuery {
  patientId?: ID;
  acknowledged?: boolean;
}

/** Empty body: the acknowledging user comes from the session. */
export type AlertAcknowledgeRequest = Record<string, never>;
