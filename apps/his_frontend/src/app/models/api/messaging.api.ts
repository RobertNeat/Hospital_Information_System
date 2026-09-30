import type { ID, Priority } from '../common.model';
import type { ClinicalAlert, HandoffNote, TaskStatus, TeamTask } from '../message.model';
import type { PageQuery } from './common.api';

/**
 * `participantId` is sent only by the mock; the backend takes the participant from the
 * session (authenticated user).
 */
export interface ThreadQuery extends PageQuery {
  participantId?: ID;
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

export type TaskCreateRequest = Omit<TeamTask, 'id' | 'createdAt'>;

export interface TaskStatusUpdateRequest {
  status: TaskStatus;
}

export type HandoffNoteCreateRequest = Omit<HandoffNote, 'id' | 'createdAt'>;

export interface AlertQuery {
  patientId?: ID;
  acknowledged?: boolean;
}

/** Internal (server-to-server); alerts are raised by the backend, not by the client. */
export type AlertCreateRequest = Omit<
  ClinicalAlert,
  'id' | 'createdAt' | 'acknowledged' | 'acknowledgedById' | 'acknowledgedAt'
>;

/** Empty body: the acknowledging user comes from the session. */
export type AlertAcknowledgeRequest = Record<string, never>;
