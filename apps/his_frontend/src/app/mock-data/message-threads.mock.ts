import type { MessageThread } from '../models';
import { hoursAgo } from './mock-utils';

export const MESSAGE_THREADS: MessageThread[] = [
  {
    id: 'thr-001',
    participantIds: ['stf-001', 'stf-006'],
    subject: 'Pacjent Kowalski - dawkowanie furosemidu',
    patientId: 'pat-001',
    lastMessageAt: hoursAgo(1),
    unreadCount: 1,
  },
  {
    id: 'thr-002',
    participantIds: ['stf-002', 'stf-007'],
    subject: 'Kontrola po koronarografii',
    patientId: 'pat-002',
    lastMessageAt: hoursAgo(3),
    unreadCount: 0,
  },
  {
    id: 'thr-003',
    participantIds: ['stf-001', 'stf-002'],
    subject: 'Konsultacja kardiologiczna',
    patientId: 'pat-001',
    lastMessageAt: hoursAgo(20),
    unreadCount: 0,
  },
  {
    id: 'thr-004',
    participantIds: ['stf-004', 'stf-009'],
    subject: 'Rehabilitacja pacjentki po udarze',
    patientId: 'pat-004',
    lastMessageAt: hoursAgo(6),
    unreadCount: 2,
  },
  {
    id: 'thr-005',
    participantIds: ['stf-003', 'stf-008'],
    subject: 'Przygotowanie sali operacyjnej',
    patientId: 'pat-009',
    lastMessageAt: hoursAgo(10),
    unreadCount: 0,
  },
  {
    id: 'thr-006',
    participantIds: ['stf-005', 'stf-010'],
    subject: 'Organizacja dyżuru SOR',
    lastMessageAt: hoursAgo(2),
    unreadCount: 0,
  },
];
