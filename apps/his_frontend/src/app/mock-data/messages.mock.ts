import type { Message } from '../models';
import { hoursAgo } from './mock-utils';

export const MESSAGES: Message[] = [
  // thr-001
  {
    id: 'msg-001',
    threadId: 'thr-001',
    senderId: 'stf-006',
    sentAt: hoursAgo(4),
    body: 'Panie doktorze, pacjent Kowalski zgłasza duszność przy zmianie pozycji. Bilans płynów za dobę dodatni.',
    priority: 'high',
    readByIds: ['stf-006', 'stf-001'],
  },
  {
    id: 'msg-002',
    threadId: 'thr-001',
    senderId: 'stf-001',
    sentAt: hoursAgo(3),
    body: 'Proszę zwiększyć dawkę furosemidu do 40 mg dwa razy dziennie i skontrolować elektrolity jutro rano.',
    priority: 'normal',
    readByIds: ['stf-001', 'stf-006'],
  },
  {
    id: 'msg-003',
    threadId: 'thr-001',
    senderId: 'stf-006',
    sentAt: hoursAgo(1),
    body: 'Dawka podana zgodnie z zaleceniem. Pacjent czuje się lepiej.',
    priority: 'normal',
    readByIds: ['stf-006'],
  },

  // thr-002
  {
    id: 'msg-004',
    threadId: 'thr-002',
    senderId: 'stf-002',
    sentAt: hoursAgo(5),
    body: 'Proszę o kontrolę parametrów życiowych co 2h po zabiegu koronarografii.',
    priority: 'high',
    readByIds: ['stf-002', 'stf-007'],
  },
  {
    id: 'msg-005',
    threadId: 'thr-002',
    senderId: 'stf-007',
    sentAt: hoursAgo(3),
    body: 'Parametry stabilne, miejsce wkłucia bez cech krwiaka.',
    priority: 'normal',
    readByIds: ['stf-007', 'stf-002'],
  },

  // thr-003
  {
    id: 'msg-006',
    threadId: 'thr-003',
    senderId: 'stf-001',
    sentAt: hoursAgo(22),
    body: 'Proszę o pilną konsultację kardiologiczną dla pacjenta z sali 204 - narastające obrzęki.',
    priority: 'high',
    readByIds: ['stf-001', 'stf-002'],
  },
  {
    id: 'msg-007',
    threadId: 'thr-003',
    senderId: 'stf-002',
    sentAt: hoursAgo(20),
    body: 'Przyjdę po popołudniowym obchodzie, ok. godz. 15:00.',
    priority: 'normal',
    readByIds: ['stf-002', 'stf-001'],
  },

  // thr-004
  {
    id: 'msg-008',
    threadId: 'thr-004',
    senderId: 'stf-004',
    sentAt: hoursAgo(8),
    body: 'Jak przebiega dzisiejsza rehabilitacja pacjentki Kamińskiej?',
    priority: 'normal',
    readByIds: ['stf-004', 'stf-009'],
  },
  {
    id: 'msg-009',
    threadId: 'thr-004',
    senderId: 'stf-009',
    sentAt: hoursAgo(7),
    body: 'Siła mięśniowa kończyny górnej poprawiła się do 4/5. Pacjentka współpracuje bardzo dobrze.',
    priority: 'normal',
    readByIds: ['stf-009'],
  },
  {
    id: 'msg-010',
    threadId: 'thr-004',
    senderId: 'stf-009',
    sentAt: hoursAgo(6),
    body: 'Proszę o decyzję odnośnie zwiększenia intensywności ćwiczeń od jutra.',
    priority: 'normal',
    readByIds: ['stf-009'],
  },

  // thr-005
  {
    id: 'msg-011',
    threadId: 'thr-005',
    senderId: 'stf-003',
    sentAt: hoursAgo(11),
    body: 'Proszę przygotować salę operacyjną nr 2 na jutro godz. 8:00, cholecystektomia laparoskopowa.',
    priority: 'normal',
    readByIds: ['stf-003', 'stf-008'],
  },
  {
    id: 'msg-012',
    threadId: 'thr-005',
    senderId: 'stf-008',
    sentAt: hoursAgo(10),
    body: 'Sala przygotowana, pacjent poinformowany o zabiegu.',
    priority: 'normal',
    readByIds: ['stf-008', 'stf-003'],
  },

  // thr-006
  {
    id: 'msg-013',
    threadId: 'thr-006',
    senderId: 'stf-005',
    sentAt: hoursAgo(3),
    body: 'Kto obejmuje dyżur nocny na SOR w ten weekend?',
    priority: 'normal',
    readByIds: ['stf-005', 'stf-010'],
  },
  {
    id: 'msg-014',
    threadId: 'thr-006',
    senderId: 'stf-010',
    sentAt: hoursAgo(2),
    body: 'Ja biorę sobotę, w niedzielę jest pielęgniarka Kaczmarek.',
    priority: 'normal',
    readByIds: ['stf-010', 'stf-005'],
  },
];
