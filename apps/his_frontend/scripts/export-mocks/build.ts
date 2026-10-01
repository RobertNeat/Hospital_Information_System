import type {
  Allergy,
  ClinicalAlert,
  ClinicalNote,
  Contraindication,
  Diagnosis,
  Drug,
  Encounter,
  HandoffNote,
  ImagingExam,
  ImagingModality,
  ImagingOrder,
  ImagingResult,
  LabOrder,
  LabPanel,
  LabResult,
  LabTest,
  Message,
  MessageThread,
  Patient,
  Prescription,
  ScheduleSlot,
  StaffMember,
  TeamTask,
  TreatmentEpisode,
  Treatment,
  VitalSigns,
  VitalThreshold,
  Ward,
} from '../../src/app/models';
import { DEFAULT, Table, type Changeset, type SqlFile } from './sql';
import { IdRegistry } from './uuid';
import { addDaysToT0Date, relDate, TIME_ZONE, ts, tsOpt } from './time';

/** Everything the generator reads from the frontend mocks (loaded after the clock is pinned). */
export interface Mocks {
  STAFF: StaffMember[];
  WARDS: Ward[];
  PATIENTS: Patient[];
  EPISODES: TreatmentEpisode[];
  ENCOUNTERS: Encounter[];
  CLINICAL_NOTES: ClinicalNote[];
  DIAGNOSES: Diagnosis[];
  ICD10_DICTIONARY: readonly { code: string; display: string }[];
  ALLERGIES: Allergy[];
  CONTRAINDICATIONS: Contraindication[];
  TREATMENTS: Treatment[];
  LAB_CATALOG: LabTest[];
  LAB_PANELS: LabPanel[];
  LAB_ORDERS: LabOrder[];
  LAB_RESULTS: LabResult[];
  IMAGING_CATALOG: ImagingExam[];
  IMAGING_ORDERS: ImagingOrder[];
  IMAGING_RESULTS: ImagingResult[];
  generateSlots: (modality: ImagingModality, date: string) => ScheduleSlot[];
  DRUGS: Drug[];
  PRESCRIPTIONS: Prescription[];
  VITALS: VitalSigns[];
  VITAL_THRESHOLDS: Record<string, VitalThreshold>;
  MESSAGE_THREADS: MessageThread[];
  MESSAGES: Message[];
  ALERTS: ClinicalAlert[];
  TASKS: TeamTask[];
  HANDOFF_NOTES: HandoffNote[];
}

export interface BuildResult {
  files: SqlFile[];
  /** table -> row count, split by changeset context. */
  manifest: { reference: Record<string, number>; mock: Record<string, number> };
}

/**
 * BCrypt (cost 10) of the shared demo password `HisDemo2026!`. Computed once with Spring Security's
 * BCryptPasswordEncoder (the salt is part of the hash, so the constant is stable); see README.
 */
export const DEMO_PASSWORD_HASH = '$2a$10$5oDBlRGwsrFsuDdeTl/EPOPDhCFr3VoyDb1Bi.4B4hn3fKXcGdmnC';

/** Modalities with slot generation, in the order of the `ck_schedule_slot_modality` constraint. */
const SLOT_MODALITIES: ImagingModality[] = [
  'USG',
  'RTG',
  'CT',
  'MRI',
  'MMG',
  'ENDOSCOPY',
  'COLONOSCOPY',
  'ANGIOGRAPHY',
];
/** Imaging schedule window, in days relative to T0. */
const SLOT_WINDOW = { from: -7, to: 14 };

const REFERENCE_AUTHOR = 'his-ref';
const MOCK_AUTHOR = 'his-mock';

class FileBuilder {
  private readonly changesets: Changeset[] = [];
  readonly tables: Table[] = [];

  constructor(
    private readonly path: string,
    private readonly context: 'reference' | 'mock',
  ) {}

  /** One changeset = one or more tables inserted in a single transaction. */
  changeset(id: string, ...tables: Table[]): void {
    this.tables.push(...tables);
    this.changesets.push({ id, statements: tables.map((t) => t.insert()) });
  }

  sql(id: string, ...statements: string[]): void {
    this.changesets.push({ id, statements });
  }

  done(): SqlFile {
    return {
      path: this.path,
      author: this.context === 'mock' ? MOCK_AUTHOR : REFERENCE_AUTHOR,
      context: this.context,
      changesets: this.changesets,
    };
  }
}

export function build(m: Mocks): BuildResult {
  const ids = new IdRegistry();
  const files: SqlFile[] = [];
  const manifest: BuildResult['manifest'] = { reference: {}, mock: {} };

  const finish = (fb: FileBuilder, bucket: 'reference' | 'mock') => {
    files.push(fb.done());
    for (const t of fb.tables) manifest[bucket][t.name] = (manifest[bucket][t.name] ?? 0) + t.count;
  };

  // ---------------------------------------------------------------- REFERENCE
  {
    const thresholds = new Table('vital_threshold', [
      'type',
      'label',
      'unit',
      'low',
      'high',
      'critical_low',
      'critical_high',
      'min_value',
      'max_value',
    ]);
    for (const v of Object.values(m.VITAL_THRESHOLDS)) {
      thresholds.add({
        type: v.type,
        label: v.label,
        unit: v.unit,
        low: v.low,
        high: v.high,
        critical_low: v.criticalLow,
        critical_high: v.criticalHigh,
        min_value: v.min,
        max_value: v.max,
      });
    }
    const f1 = new FileBuilder('reference/001-vital-threshold.sql', 'reference');
    f1.changeset('001-vital-threshold', thresholds);
    finish(f1, 'reference');

    const icd = new Table('icd10_code', ['code', 'display']);
    for (const c of m.ICD10_DICTIONARY) icd.add({ code: c.code, display: c.display });
    const f2 = new FileBuilder('reference/002-icd10-code.sql', 'reference');
    f2.changeset('002-icd10-code', icd);
    finish(f2, 'reference');
  }

  // ---------------------------------------------------------------- MOCK 001: staff
  {
    const ward = new Table('ward', ['id', 'name', 'short_name', 'floor', 'beds']);
    for (const w of m.WARDS) {
      ward.add({
        id: ids.define('ward', w.id),
        name: w.name,
        short_name: w.shortName,
        floor: w.floor,
        beds: w.beds,
      });
    }
    const staff = new Table('staff_member', [
      'id',
      'title',
      'first_name',
      'last_name',
      'role',
      'specialization',
      'ward_id',
      'phone',
      'pwz',
      'employee_id',
      'email',
    ]);
    const accounts = new Table('user_account', [
      'id',
      'staff_id',
      'employee_id',
      'password_hash',
      'account_status',
    ]);
    for (const s of m.STAFF) {
      const id = ids.define('staff_member', s.id);
      staff.add({
        id,
        title: s.title,
        first_name: s.firstName,
        last_name: s.lastName,
        role: s.role,
        specialization: s.specialization ?? null,
        ward_id: ids.ref('ward', s.wardId),
        phone: s.phone ?? null,
        pwz: s.pwz ?? null,
        employee_id: s.employeeId ?? null,
        email: s.email ?? null,
      });
      if (s.employeeId) {
        accounts.add({
          id: ids.define('user_account', `user-account/${s.id}`),
          staff_id: id,
          employee_id: s.employeeId,
          password_hash: DEMO_PASSWORD_HASH,
          account_status: s.accountStatus ?? 'active',
        });
      }
    }
    const f = new FileBuilder('mock/001-staff.sql', 'mock');
    f.changeset('001-ward', ward);
    f.changeset('001-staff-member', staff);
    f.changeset('001-user-account', accounts);
    finish(f, 'mock');
  }

  // ---------------------------------------------------------------- MOCK 002: catalogs
  {
    const labTest = new Table('lab_test', [
      'code',
      'loinc',
      'name',
      'category',
      'default_specimen',
      'turnaround_hours',
      'fasting_required',
    ]);
    const labSpecimen = new Table('lab_test_specimen', ['test_code', 'specimen_type']);
    const analytes = new Table('lab_analyte_definition', [
      'id',
      'test_code',
      'code',
      'name',
      'unit',
      'low',
      'high',
    ]);
    for (const t of m.LAB_CATALOG) {
      ids.define('lab_test', t.code);
      labTest.add({
        code: t.code,
        loinc: t.loinc ?? null,
        name: t.name,
        category: t.category,
        default_specimen: t.defaultSpecimen,
        turnaround_hours: t.turnaroundHours,
        fasting_required: t.fastingRequired,
      });
      if (!t.specimenTypes.includes(t.defaultSpecimen)) {
        throw new Error(`${t.code}: defaultSpecimen is not among specimenTypes`);
      }
      for (const sp of t.specimenTypes) labSpecimen.add({ test_code: t.code, specimen_type: sp });
      for (const a of t.analytes) {
        analytes.add({
          id: ids.define('lab_analyte_definition', `lab-analyte/${t.code}/${a.code}`),
          test_code: t.code,
          code: a.code,
          name: a.name,
          unit: a.unit,
          low: a.low ?? null,
          high: a.high ?? null,
        });
      }
    }
    const panels = new Table('lab_panel', ['id', 'name']);
    const panelTests = new Table('lab_panel_test', ['panel_id', 'test_code']);
    for (const p of m.LAB_PANELS) {
      const id = ids.define('lab_panel', p.id);
      panels.add({ id, name: p.name });
      for (const code of p.testCodes) {
        if (!ids.has('lab_test', code)) throw new Error(`${p.id}: unknown test ${code}`);
        panelTests.add({ panel_id: id, test_code: code });
      }
    }
    const exams = new Table('imaging_exam', [
      'code',
      'modality',
      'name',
      'body_region',
      'contrast_possible',
      'requires_laterality',
      'preparation',
      'duration_minutes',
    ]);
    for (const e of m.IMAGING_CATALOG) {
      ids.define('imaging_exam', e.code);
      exams.add({
        code: e.code,
        modality: e.modality,
        name: e.name,
        body_region: e.bodyRegion,
        contrast_possible: e.contrastPossible,
        requires_laterality: e.requiresLaterality,
        preparation: e.preparation ?? null,
        duration_minutes: e.durationMinutes,
      });
    }
    const drug = new Table('drug', [
      'id',
      'name',
      'active_substance',
      'atc_code',
      'form',
      'strength',
      'package_size',
      'package_unit',
      'default_dose_unit',
      'rx_only',
      'max_daily_dose_value',
      'max_daily_dose_unit',
    ]);
    const routes = new Table('drug_route', ['drug_id', 'route']);
    const reimb = new Table('drug_reimbursement_option', ['drug_id', 'reimbursement']);
    const interacts = new Table('drug_interacts_with_atc', ['drug_id', 'atc_code']);
    for (const d of m.DRUGS) {
      const id = ids.define('drug', d.id);
      drug.add({
        id,
        name: d.name,
        active_substance: d.activeSubstance,
        atc_code: d.atcCode,
        form: d.form,
        strength: d.strength,
        package_size: d.packageSize,
        package_unit: d.packageUnit,
        default_dose_unit: d.defaultDoseUnit,
        rx_only: d.rxOnly,
        max_daily_dose_value: d.maxDailyDose?.value ?? null,
        max_daily_dose_unit: d.maxDailyDose?.unit ?? null,
      });
      for (const r of d.routes) routes.add({ drug_id: id, route: r });
      for (const r of d.reimbursementOptions) reimb.add({ drug_id: id, reimbursement: r });
      for (const a of d.interactsWithAtc ?? []) interacts.add({ drug_id: id, atc_code: a });
    }
    const f = new FileBuilder('mock/002-catalogs.sql', 'mock');
    // lab_test.default_specimen -> lab_test_specimen is a deferrable FK: both tables in one transaction.
    f.changeset('002-lab-test', labTest, labSpecimen);
    f.changeset('002-lab-analyte-definition', analytes);
    f.changeset('002-lab-panel', panels);
    f.changeset('002-lab-panel-test', panelTests);
    f.changeset('002-imaging-exam', exams);
    f.changeset('002-drug', drug);
    f.changeset('002-drug-route', routes);
    f.changeset('002-drug-reimbursement-option', reimb);
    f.changeset('002-drug-interacts-with-atc', interacts);
    finish(f, 'mock');
  }

  // ---------------------------------------------------------------- MOCK 003: schedule slots
  {
    const slots: {
      id: string;
      modality: string;
      day: number;
      start: number;
      dur: number;
      room: string;
      available: boolean;
    }[] = [];
    for (let day = SLOT_WINDOW.from; day <= SLOT_WINDOW.to; day++) {
      const date = addDaysToT0Date(day);
      for (const modality of SLOT_MODALITIES) {
        for (const s of m.generateSlots(modality, date)) {
          const start = new Date(s.start);
          const startMin = start.getHours() * 60 + start.getMinutes();
          const dur = Math.round((Date.parse(s.end) - Date.parse(s.start)) / 60000);
          const key = `schedule-slot/${modality}/${s.room}/d${day}/${String(startMin).padStart(4, '0')}`;
          slots.push({
            id: ids.define('schedule_slot', key),
            modality,
            day,
            start: startMin,
            dur,
            room: s.room,
            available: s.available,
          });
        }
      }
    }
    const values = slots
      .map(
        (s) =>
          `        ('${s.id}', '${s.modality}', ${s.day}, ${s.start}, ${s.dur}, '${s.room}', ${s.available ? 'TRUE' : 'FALSE'})`,
      )
      .join(',\n');
    // Day-anchored (local midnight of the migration day + offset), so slots keep their 08:00-16:00 working hours.
    const statement = `INSERT INTO schedule_slot (id, modality, start_at, end_at, room, available)
SELECT v.id::uuid,
       v.modality,
       (d.day + make_interval(days => v.day_offset, mins => v.start_minute)) AT TIME ZONE '${TIME_ZONE}',
       (d.day + make_interval(days => v.day_offset, mins => v.start_minute + v.duration_minutes)) AT TIME ZONE '${TIME_ZONE}',
       v.room,
       v.available
FROM (SELECT date_trunc('day', now() AT TIME ZONE '${TIME_ZONE}') AS day) AS d
CROSS JOIN (VALUES
${values}
) AS v(id, modality, day_offset, start_minute, duration_minutes, room, available);`;
    const f = new FileBuilder('mock/003-schedule-slots.sql', 'mock');
    f.sql('003-schedule-slot', statement);
    files.push(f.done());
    manifest.mock['schedule_slot'] = slots.length;
  }

  let admissionsList: { mockId: string; patient: Patient }[] = [];

  // ---------------------------------------------------------------- MOCK 004: patients, episodes, encounters, admissions
  {
    const patient = new Table('patient', [
      'id',
      'mrn',
      'pesel',
      'no_pesel_reason',
      'identity_doc_type',
      'identity_doc_number',
      'first_name',
      'last_name',
      'second_name',
      'birth_date',
      'gender',
      'phone',
      'email',
      'address_street',
      'address_building_number',
      'address_apartment_number',
      'address_postal_code',
      'address_city',
      'address_country',
      'emergency_contact_full_name',
      'emergency_contact_relation',
      'emergency_contact_phone',
      'emergency_contact_is_legal_guardian',
      'insurance_status',
      'insurance_nfz_branch',
      'insurance_payer',
      'insurance_ewus_verified_at',
      'blood_type',
      'status',
      'created_at',
      'updated_at',
    ]);
    const flags = new Table('patient_flag', ['patient_id', 'flag']);
    let maxMrn = 0;
    for (const p of m.PATIENTS) {
      const id = ids.define('patient', p.id);
      const mrnNo = /(\d+)$/.exec(p.mrn);
      if (!mrnNo) throw new Error(`${p.id}: MRN without numeric suffix`);
      maxMrn = Math.max(maxMrn, Number(mrnNo[1]));
      patient.add({
        id,
        mrn: p.mrn,
        pesel: p.pesel,
        no_pesel_reason: p.noPeselReason ?? null,
        identity_doc_type: p.identityDocument?.type ?? null,
        identity_doc_number: p.identityDocument?.number ?? null,
        first_name: p.firstName,
        last_name: p.lastName,
        second_name: p.secondName ?? null,
        birth_date: p.birthDate,
        gender: p.gender,
        phone: p.phone ?? null,
        email: p.email ?? null,
        address_street: p.address.street,
        address_building_number: p.address.buildingNumber,
        address_apartment_number: p.address.apartmentNumber ?? null,
        address_postal_code: p.address.postalCode,
        address_city: p.address.city,
        address_country: p.address.country,
        emergency_contact_full_name: p.emergencyContact?.fullName ?? null,
        emergency_contact_relation: p.emergencyContact?.relation ?? null,
        emergency_contact_phone: p.emergencyContact?.phone ?? null,
        emergency_contact_is_legal_guardian: p.emergencyContact?.isLegalGuardian ?? null,
        insurance_status: p.insurance.status,
        insurance_nfz_branch: p.insurance.nfzBranch,
        insurance_payer: p.insurance.payer,
        insurance_ewus_verified_at: tsOpt(p.insurance.ewusVerifiedAt),
        blood_type: p.bloodType ?? null,
        status: p.status,
        created_at: ts(p.createdAt),
        updated_at: ts(p.updatedAt),
      });
      for (const flag of p.flags) flags.add({ patient_id: id, flag });
    }

    const episode = new Table('treatment_episode', [
      'id',
      'patient_id',
      'title',
      'start_at',
      'end_at',
      'status',
    ]);
    for (const e of m.EPISODES) {
      episode.add({
        id: ids.define('treatment_episode', e.id),
        patient_id: ids.ref('patient', e.patientId),
        title: e.title,
        start_at: ts(e.startAt),
        end_at: tsOpt(e.endAt),
        status: e.status,
      });
    }

    // Admissions: Patient.currentAdmission is a projection; a hospitalization/emergency encounter in
    // the same ward already exists for each one, so it is reused (a synthetic one only as a fallback).
    const encounters = [...m.ENCOUNTERS];
    const usedEncounters = new Set<string>();
    const admissions: { mockId: string; patient: Patient; encounterId: string }[] = [];
    for (const p of m.PATIENTS) {
      const a = p.currentAdmission;
      if (!a) continue;
      const candidates = encounters
        .filter(
          (e) =>
            e.patientId === p.id &&
            e.wardId === a.wardId &&
            (e.type === 'hospitalization' || e.type === 'emergency') &&
            !usedEncounters.has(e.id),
        )
        .sort(
          (x, y) =>
            Math.abs(Date.parse(x.startAt) - Date.parse(a.admittedAt)) -
            Math.abs(Date.parse(y.startAt) - Date.parse(a.admittedAt)),
        );
      let enc = candidates[0];
      if (!enc) {
        enc = {
          id: `enc-adm-${p.id}`,
          patientId: p.id,
          type: 'hospitalization',
          status: a.dischargedAt ? 'finished' : 'in_progress',
          startAt: a.admittedAt,
          endAt: a.dischargedAt,
          wardId: a.wardId,
          practitionerId: a.attendingPhysicianId,
          reason: a.reason,
        };
        encounters.push(enc);
      }
      usedEncounters.add(enc.id);
      admissions.push({ mockId: `adm-${p.id}`, patient: p, encounterId: enc.id });
    }

    const encounter = new Table('encounter', [
      'id',
      'patient_id',
      'type',
      'status',
      'start_at',
      'end_at',
      'ward_id',
      'practitioner_id',
      'reason',
      'summary',
      'episode_id',
    ]);
    for (const e of encounters) {
      encounter.add({
        id: ids.define('encounter', e.id),
        patient_id: ids.ref('patient', e.patientId),
        type: e.type,
        status: e.status,
        start_at: ts(e.startAt),
        end_at: tsOpt(e.endAt),
        ward_id: e.wardId ? ids.ref('ward', e.wardId) : null,
        practitioner_id: ids.ref('staff_member', e.practitionerId),
        reason: e.reason,
        summary: e.summary ?? null,
        episode_id: e.episodeId ? ids.ref('treatment_episode', e.episodeId) : null,
      });
    }

    const admission = new Table('admission', [
      'id',
      'patient_id',
      'encounter_id',
      'status',
      'admission_type',
      'admitted_at',
      'ward_id',
      'room',
      'bed',
      'attending_physician_id',
      'triage_level',
      'reason',
      'referral_number',
      'discharged_at',
      'discharge_disposition',
    ]);
    for (const { mockId, patient: p, encounterId } of admissions) {
      const a = p.currentAdmission!;
      const discharged = Boolean(a.dischargedAt);
      admission.add({
        id: ids.define('admission', mockId),
        patient_id: ids.ref('patient', p.id),
        encounter_id: ids.ref('encounter', encounterId),
        status: discharged ? 'discharged' : 'active',
        admission_type: a.admissionType,
        admitted_at: ts(a.admittedAt),
        ward_id: ids.ref('ward', a.wardId),
        room: a.room ?? null,
        bed: a.bed ?? null,
        attending_physician_id: ids.ref('staff_member', a.attendingPhysicianId),
        triage_level: a.triageLevel ?? null,
        reason: a.reason,
        referral_number: a.referralNumber ?? null,
        discharged_at: tsOpt(a.dischargedAt),
        // The mocks carry no disposition; a discharged stay is modelled as a regular discharge home.
        discharge_disposition: discharged ? 'home' : null,
      });
    }

    const f = new FileBuilder('mock/004-patients.sql', 'mock');
    f.changeset('004-patient', patient);
    // Mock MRNs are explicit; move the sequence so that the backend continues after the last one.
    f.sql('004-patient-mrn-seq', `SELECT setval('patient_mrn_seq', ${maxMrn});`);
    f.changeset('004-patient-flag', flags);
    f.changeset('004-treatment-episode', episode);
    f.changeset('004-encounter', encounter);
    f.changeset('004-admission', admission);
    finish(f, 'mock');

    admissionsList = admissions;
  }

  // ---------------------------------------------------------------- MOCK 005: EHR
  {
    const note = new Table('clinical_note', [
      'id',
      'patient_id',
      'encounter_id',
      'author_id',
      'category',
      'title',
      'content',
      'created_at',
      'updated_at',
    ]);
    const symptoms = new Table('clinical_note_symptom', ['note_id', 'symptom']);
    for (const n of m.CLINICAL_NOTES) {
      const id = ids.define('clinical_note', n.id);
      note.add({
        id,
        patient_id: ids.ref('patient', n.patientId),
        encounter_id: n.encounterId ? ids.ref('encounter', n.encounterId) : null,
        author_id: ids.ref('staff_member', n.authorId),
        category: n.category,
        title: n.title,
        content: n.content,
        created_at: ts(n.createdAt),
        updated_at: ts(n.updatedAt ?? n.createdAt),
      });
      for (const s of n.symptoms ?? []) symptoms.add({ note_id: id, symptom: s });
    }

    // discharged admission -> the patient's discharge summary note
    const dischargeLinks: string[] = [];
    for (const { mockId, patient: p } of admissionsList) {
      if (!p.currentAdmission?.dischargedAt) continue;
      const summary = m.CLINICAL_NOTES.find(
        (n) => n.patientId === p.id && n.category === 'discharge',
      );
      if (!summary) continue;
      dischargeLinks.push(
        `UPDATE admission SET discharge_summary_note_id = '${ids.ref('clinical_note', summary.id)}' WHERE id = '${ids.ref('admission', mockId)}';`,
      );
    }

    const dx = new Table('diagnosis', [
      'id',
      'patient_id',
      'encounter_id',
      'code_system',
      'code_value',
      'code_display',
      'type',
      'status',
      'diagnosed_at',
      'diagnosed_by_id',
      'notes',
      'created_at',
      'updated_at',
    ]);
    for (const d of m.DIAGNOSES) {
      dx.add({
        id: ids.define('diagnosis', d.id),
        patient_id: ids.ref('patient', d.patientId),
        encounter_id: d.encounterId ? ids.ref('encounter', d.encounterId) : null,
        code_system: d.code.system,
        code_value: d.code.code,
        code_display: d.code.display,
        type: d.type,
        status: d.status,
        diagnosed_at: ts(d.diagnosedAt),
        diagnosed_by_id: ids.ref('staff_member', d.diagnosedById),
        notes: d.notes ?? null,
        created_at: ts(d.createdAt ?? d.diagnosedAt),
        updated_at: ts(d.updatedAt ?? d.createdAt ?? d.diagnosedAt),
      });
    }
    const epDx = new Table('episode_diagnosis', ['episode_id', 'diagnosis_id']);
    for (const e of m.EPISODES) {
      for (const dId of e.diagnosisIds) {
        epDx.add({
          episode_id: ids.ref('treatment_episode', e.id),
          diagnosis_id: ids.ref('diagnosis', dId),
        });
      }
    }

    const allergy = new Table('allergy', [
      'id',
      'patient_id',
      'substance',
      'category',
      'reaction',
      'severity',
      'status',
      'recorded_at',
      'recorded_by_id',
      'created_at',
      'updated_at',
    ]);
    const allergyAtc = new Table('allergy_atc_code', ['allergy_id', 'atc_code']);
    for (const a of m.ALLERGIES) {
      const id = ids.define('allergy', a.id);
      allergy.add({
        id,
        patient_id: ids.ref('patient', a.patientId),
        substance: a.substance,
        category: a.category,
        reaction: a.reaction,
        severity: a.severity,
        status: a.status,
        recorded_at: ts(a.recordedAt),
        recorded_by_id: a.recordedById ? ids.ref('staff_member', a.recordedById) : null,
        created_at: ts(a.createdAt ?? a.recordedAt),
        updated_at: ts(a.updatedAt ?? a.createdAt ?? a.recordedAt),
      });
      for (const atc of a.atcCodes ?? []) allergyAtc.add({ allergy_id: id, atc_code: atc });
    }
    const contra = new Table('contraindication', [
      'id',
      'patient_id',
      'description',
      'reason',
      'recorded_at',
    ]);
    for (const c of m.CONTRAINDICATIONS) {
      contra.add({
        id: ids.define('contraindication', c.id),
        patient_id: ids.ref('patient', c.patientId),
        description: c.description,
        reason: c.reason,
        recorded_at: ts(c.recordedAt),
      });
    }
    const treatment = new Table('treatment', [
      'id',
      'patient_id',
      'encounter_id',
      'name',
      'type',
      'start_at',
      'end_at',
      'status',
      'description',
      'practitioner_id',
    ]);
    for (const t of m.TREATMENTS) {
      treatment.add({
        id: ids.define('treatment', t.id),
        patient_id: ids.ref('patient', t.patientId),
        encounter_id: t.encounterId ? ids.ref('encounter', t.encounterId) : null,
        name: t.name,
        type: t.type,
        start_at: ts(t.startAt),
        end_at: tsOpt(t.endAt),
        status: t.status,
        description: t.description,
        practitioner_id: ids.ref('staff_member', t.practitionerId),
      });
    }

    const f = new FileBuilder('mock/005-ehr.sql', 'mock');
    f.changeset('005-clinical-note', note);
    f.changeset('005-clinical-note-symptom', symptoms);
    if (dischargeLinks.length) f.sql('005-admission-discharge-summary-note', ...dischargeLinks);
    f.changeset('005-diagnosis', dx);
    f.changeset('005-episode-diagnosis', epDx);
    f.changeset('005-allergy', allergy);
    f.changeset('005-allergy-atc-code', allergyAtc);
    f.changeset('005-contraindication', contra);
    f.changeset('005-treatment', treatment);
    finish(f, 'mock');
  }

  // ---------------------------------------------------------------- MOCK 006: laboratory
  {
    const order = new Table('lab_order', [
      'id',
      'patient_id',
      'encounter_id',
      'ordered_by_id',
      'ordered_at',
      'urgency',
      'fasting',
      'planned_collection_at',
      'diagnosis_code_system',
      'diagnosis_code_value',
      'diagnosis_code_display',
      'clinical_info',
      'notes',
      'status',
      'created_at',
      'updated_at',
    ]);
    const item = new Table('lab_order_item', [
      'id',
      'order_id',
      'test_code',
      'test_name',
      'specimen_id',
      'specimen_type',
    ]);
    const change = new Table('lab_order_status_change', [
      'id',
      'order_id',
      'status',
      'at',
      'by_id',
      'note',
    ]);
    /** orderId + testCode -> item uuid (for lab_result.order_item_id). */
    const itemByOrderTest = new Map<string, string>();
    for (const o of m.LAB_ORDERS) {
      const id = ids.define('lab_order', o.id);
      order.add({
        id,
        patient_id: ids.ref('patient', o.patientId),
        encounter_id: o.encounterId ? ids.ref('encounter', o.encounterId) : null,
        ordered_by_id: ids.ref('staff_member', o.orderedById),
        ordered_at: ts(o.orderedAt),
        urgency: o.urgency,
        fasting: o.fasting,
        planned_collection_at: ts(o.plannedCollectionAt),
        diagnosis_code_system: o.diagnosisCode?.system ?? null,
        diagnosis_code_value: o.diagnosisCode?.code ?? null,
        diagnosis_code_display: o.diagnosisCode?.display ?? null,
        clinical_info: o.clinicalInfo,
        notes: o.notes ?? null,
        status: o.status,
        created_at: ts(o.createdAt ?? o.orderedAt),
        updated_at: ts(o.updatedAt ?? o.createdAt ?? o.orderedAt),
      });
      o.items.forEach((it, i) => {
        if (!ids.has('lab_test', it.testCode))
          throw new Error(`${o.id}: unknown test ${it.testCode}`);
        const itemId = ids.define('lab_order_item', `lab-order-item/${o.id}/${i}`);
        if (!itemByOrderTest.has(`${o.id}|${it.testCode}`)) {
          itemByOrderTest.set(`${o.id}|${it.testCode}`, itemId);
        }
        item.add({
          id: itemId,
          order_id: id,
          test_code: it.testCode,
          test_name: it.testName,
          specimen_id: it.specimenId ? ids.define('specimen', it.specimenId) : null,
          specimen_type: it.specimenType,
        });
      });
      o.statusHistory.forEach((h, i) => {
        change.add({
          id: ids.define('lab_order_status_change', `lab-order-status/${o.id}/${i}`),
          order_id: id,
          status: h.status,
          at: ts(h.at),
          by_id: h.byId ? ids.ref('staff_member', h.byId) : null,
          note: h.note ?? null,
        });
      });
    }

    const result = new Table('lab_result', [
      'id',
      'patient_id',
      'order_id',
      'order_item_id',
      'test_code',
      'test_name',
      'category',
      'collected_at',
      'resulted_at',
      'status',
      'performer_name',
      'comment',
      'reviewed_at',
      'reviewed_by_id',
    ]);
    const obs = new Table('lab_observation', [
      'id',
      'result_id',
      'analyte_code',
      'analyte_name',
      'value_numeric',
      'value_text',
      'unit',
      'ref_low',
      'ref_high',
      'ref_text',
      'flag',
    ]);
    for (const r of m.LAB_RESULTS) {
      const id = ids.define('lab_result', r.id);
      if (!ids.has('lab_test', r.testCode)) throw new Error(`${r.id}: unknown test ${r.testCode}`);
      const orderItemId =
        r.orderItemId ??
        (r.orderId ? (itemByOrderTest.get(`${r.orderId}|${r.testCode}`) ?? null) : null);
      result.add({
        id,
        patient_id: ids.ref('patient', r.patientId),
        order_id: r.orderId ? ids.ref('lab_order', r.orderId) : null,
        order_item_id: orderItemId,
        test_code: r.testCode,
        test_name: r.testName,
        category: r.category,
        collected_at: ts(r.collectedAt),
        resulted_at: ts(r.resultedAt),
        status: r.status,
        performer_name: r.performerName,
        comment: r.comment ?? null,
        reviewed_at: tsOpt(r.reviewedAt),
        reviewed_by_id: r.reviewedById ? ids.ref('staff_member', r.reviewedById) : null,
      });
      r.observations.forEach((o, i) => {
        const numeric = typeof o.value === 'number';
        obs.add({
          id: ids.define('lab_observation', `lab-observation/${r.id}/${i}`),
          result_id: id,
          analyte_code: o.analyteCode,
          analyte_name: o.analyteName,
          value_numeric: numeric ? (o.value as number) : null,
          value_text: numeric ? null : String(o.value),
          unit: o.unit,
          ref_low: o.referenceRange.low ?? null,
          ref_high: o.referenceRange.high ?? null,
          ref_text: o.referenceRange.text ?? null,
          flag: o.flag,
        });
      });
    }

    const f = new FileBuilder('mock/006-lab.sql', 'mock');
    f.changeset('006-lab-order', order);
    f.changeset('006-lab-order-item', item);
    f.changeset('006-lab-order-status-change', change);
    f.changeset('006-lab-result', result);
    f.changeset('006-lab-observation', obs);
    finish(f, 'mock');
  }

  // ---------------------------------------------------------------- MOCK 007: imaging
  {
    const order = new Table('imaging_order', [
      'id',
      'patient_id',
      'encounter_id',
      'exam_code',
      'exam_name',
      'modality',
      'body_region',
      'laterality',
      'contrast',
      'clinical_indication',
      'clinical_question',
      'diagnosis_code_system',
      'diagnosis_code_value',
      'diagnosis_code_display',
      'urgency',
      'safety_pregnancy',
      'safety_pacemaker_or_implant',
      'safety_metal_fragments',
      'safety_contrast_allergy',
      'safety_creatinine',
      'safety_egfr',
      'safety_claustrophobia',
      'safety_confirmed',
      'slot_id',
      'scheduled_at',
      'ordered_by_id',
      'ordered_at',
      'status',
      'created_at',
      'updated_at',
    ]);
    const change = new Table('imaging_order_status_change', [
      'id',
      'order_id',
      'status',
      'at',
      'by_id',
      'note',
    ]);
    for (const o of m.IMAGING_ORDERS) {
      const id = ids.define('imaging_order', o.id);
      if (!ids.has('imaging_exam', o.examCode))
        throw new Error(`${o.id}: unknown exam ${o.examCode}`);
      order.add({
        id,
        patient_id: ids.ref('patient', o.patientId),
        encounter_id: o.encounterId ? ids.ref('encounter', o.encounterId) : null,
        exam_code: o.examCode,
        exam_name: o.examName,
        modality: o.modality,
        body_region: o.bodyRegion,
        laterality: o.laterality,
        contrast: o.contrast,
        clinical_indication: o.clinicalIndication,
        clinical_question: o.clinicalQuestion ?? null,
        diagnosis_code_system: o.diagnosisCode?.system ?? null,
        diagnosis_code_value: o.diagnosisCode?.code ?? null,
        diagnosis_code_display: o.diagnosisCode?.display ?? null,
        urgency: o.urgency,
        safety_pregnancy: o.safety.pregnancy,
        safety_pacemaker_or_implant: o.safety.pacemakerOrImplant,
        safety_metal_fragments: o.safety.metalFragments,
        safety_contrast_allergy: o.safety.contrastAllergy,
        safety_creatinine: o.safety.creatinine ?? null,
        safety_egfr: o.safety.egfr ?? null,
        safety_claustrophobia: o.safety.claustrophobia,
        safety_confirmed: o.safety.confirmed,
        // Generated slots live in a relative window; mock orders do not reserve any of them.
        slot_id: o.slotId ? ids.ref('schedule_slot', o.slotId) : null,
        scheduled_at: tsOpt(o.scheduledAt),
        ordered_by_id: ids.ref('staff_member', o.orderedById),
        ordered_at: ts(o.orderedAt),
        status: o.status,
        created_at: ts(o.createdAt ?? o.orderedAt),
        updated_at: ts(o.updatedAt ?? o.createdAt ?? o.orderedAt),
      });
      o.statusHistory.forEach((h, i) => {
        change.add({
          id: ids.define('imaging_order_status_change', `imaging-order-status/${o.id}/${i}`),
          order_id: id,
          status: h.status,
          at: ts(h.at),
          by_id: h.byId ? ids.ref('staff_member', h.byId) : null,
          note: h.note ?? null,
        });
      });
    }
    const result = new Table('imaging_result', [
      'id',
      'patient_id',
      'order_id',
      'modality',
      'exam_name',
      'body_region',
      'performed_at',
      'reported_at',
      'radiologist_name',
      'radiologist_id',
      'technique',
      'findings',
      'conclusion',
      'status',
      'image_count',
      'critical',
      'reviewed_at',
      'reviewed_by_id',
    ]);
    for (const r of m.IMAGING_RESULTS) {
      result.add({
        id: ids.define('imaging_result', r.id),
        patient_id: ids.ref('patient', r.patientId),
        order_id: r.orderId ? ids.ref('imaging_order', r.orderId) : null,
        modality: r.modality,
        exam_name: r.examName,
        body_region: r.bodyRegion,
        performed_at: ts(r.performedAt),
        reported_at: ts(r.reportedAt),
        radiologist_name: r.radiologistName,
        radiologist_id: r.radiologistId ? ids.ref('staff_member', r.radiologistId) : null,
        technique: r.technique ?? null,
        findings: r.findings,
        conclusion: r.conclusion,
        status: r.status,
        image_count: r.imageCount,
        critical: r.critical,
        reviewed_at: tsOpt(r.reviewedAt),
        reviewed_by_id: r.reviewedById ? ids.ref('staff_member', r.reviewedById) : null,
      });
    }
    const f = new FileBuilder('mock/007-imaging.sql', 'mock');
    f.changeset('007-imaging-order', order);
    f.changeset('007-imaging-order-status-change', change);
    f.changeset('007-imaging-result', result);
    finish(f, 'mock');
  }

  // ---------------------------------------------------------------- MOCK 008: prescriptions
  {
    const rx = new Table('prescription', [
      'id',
      'patient_id',
      'encounter_id',
      'prescriber_id',
      'issued_at',
      'valid_from',
      'valid_until',
      'kind',
      'status',
      'access_code',
      'erx_key',
      'notes',
      'cancelled_at',
      'cancel_reason',
      'created_at',
      'updated_at',
    ]);
    const item = new Table('prescription_item', [
      'id',
      'prescription_id',
      'drug_id',
      'drug_name',
      'active_substance',
      'strength',
      'form',
      'dosage_dose',
      'dosage_dose_unit',
      'dosage_route',
      'dosage_frequency',
      'dosage_duration_days',
      'dosage_as_needed',
      'dosage_max_per_day',
      'dosage_instructions',
      'quantity_packages',
      'reimbursement',
      'substitution_allowed',
    ]);
    const tod = new Table('prescription_item_time_of_day', ['item_id', 'time_of_day']);
    for (const p of m.PRESCRIPTIONS) {
      const id = ids.define('prescription', p.id);
      rx.add({
        id,
        patient_id: ids.ref('patient', p.patientId),
        encounter_id: p.encounterId ? ids.ref('encounter', p.encounterId) : null,
        prescriber_id: ids.ref('staff_member', p.prescriberId),
        issued_at: ts(p.issuedAt),
        valid_from: relDate(p.validFrom),
        valid_until: relDate(p.validUntil),
        kind: p.kind,
        status: p.status,
        access_code: p.accessCode,
        erx_key: p.eRxKey,
        notes: p.notes ?? null,
        cancelled_at: tsOpt(p.cancelledAt),
        cancel_reason: p.cancelReason ?? null,
        created_at: ts(p.createdAt ?? p.issuedAt),
        updated_at: ts(p.updatedAt ?? p.createdAt ?? p.issuedAt),
      });
      p.items.forEach((it, i) => {
        const itemId = ids.define('prescription_item', `prescription-item/${p.id}/${i}`);
        item.add({
          id: itemId,
          prescription_id: id,
          drug_id: ids.ref('drug', it.drugId),
          drug_name: it.drugName,
          active_substance: it.activeSubstance,
          strength: it.strength,
          form: it.form,
          dosage_dose: it.dosage.dose,
          dosage_dose_unit: it.dosage.doseUnit,
          dosage_route: it.dosage.route,
          dosage_frequency: it.dosage.frequency,
          dosage_duration_days: it.dosage.durationDays,
          dosage_as_needed: it.dosage.asNeeded,
          dosage_max_per_day: it.dosage.maxPerDay ?? null,
          dosage_instructions: it.dosage.instructions ?? null,
          quantity_packages: it.quantityPackages,
          reimbursement: it.reimbursement,
          substitution_allowed: it.substitutionAllowed,
        });
        for (const t of it.dosage.timesOfDay ?? []) tod.add({ item_id: itemId, time_of_day: t });
      });
    }
    const f = new FileBuilder('mock/008-prescriptions.sql', 'mock');
    f.changeset('008-prescription', rx);
    f.changeset('008-prescription-item', item);
    f.changeset('008-prescription-item-time-of-day', tod);
    finish(f, 'mock');
  }

  // ---------------------------------------------------------------- MOCK 009: vital signs
  {
    const v = new Table('vital_signs', [
      'id',
      'patient_id',
      'recorded_at',
      'recorded_by_id',
      'context',
      'source',
      'device_id',
      'encounter_id',
      'systolic',
      'diastolic',
      'heart_rate',
      'spo2',
      'respiratory_rate',
      'temperature',
      'pain_score',
      'notes',
    ]);
    for (const s of m.VITALS) {
      v.add({
        id: ids.define('vital_signs', s.id),
        patient_id: ids.ref('patient', s.patientId),
        recorded_at: ts(s.recordedAt),
        recorded_by_id: ids.ref('staff_member', s.recordedById),
        context: s.context,
        source: s.source ?? 'manual',
        device_id: s.deviceId ?? null,
        encounter_id: s.encounterId ? ids.ref('encounter', s.encounterId) : null,
        systolic: s.systolic ?? null,
        diastolic: s.diastolic ?? null,
        heart_rate: s.heartRate ?? null,
        spo2: s.spo2 ?? null,
        respiratory_rate: s.respiratoryRate ?? null,
        temperature: s.temperature ?? null,
        pain_score: s.painScore ?? null,
        notes: s.notes ?? null,
      });
    }
    const f = new FileBuilder('mock/009-vitals.sql', 'mock');
    f.changeset('009-vital-signs', v);
    finish(f, 'mock');
  }

  // ---------------------------------------------------------------- MOCK 010: messaging, alerts, tasks, handoff
  {
    const thread = new Table('message_thread', [
      'id',
      'subject',
      'patient_id',
      'created_by_id',
      'created_at',
      'last_message_at',
    ]);
    const participant = new Table('thread_participant', [
      'thread_id',
      'staff_id',
      'last_read_at',
      'joined_at',
    ]);
    const message = new Table('message', [
      'id',
      'thread_id',
      'sender_id',
      'sent_at',
      'body',
      'priority',
    ]);
    const byThread = new Map<string, Message[]>();
    for (const msg of m.MESSAGES) {
      const list = byThread.get(msg.threadId) ?? [];
      list.push(msg);
      byThread.set(msg.threadId, list);
    }
    for (const t of m.MESSAGE_THREADS) {
      const id = ids.define('message_thread', t.id);
      const msgs = byThread.get(t.id) ?? [];
      if (msgs.length === 0) throw new Error(`${t.id}: thread without messages`);
      const firstSent = msgs.map((x) => x.sentAt).sort()[0];
      thread.add({
        id,
        subject: t.subject,
        patient_id: t.patientId ? ids.ref('patient', t.patientId) : null,
        created_by_id: t.createdById ? ids.ref('staff_member', t.createdById) : null,
        created_at: ts(firstSent),
        last_message_at: ts(t.lastMessageAt),
      });
      let maxUnread = 0;
      for (const staffId of t.participantIds) {
        // readByIds (per message) -> read cursor: the latest message this participant has read.
        const readTimes = msgs.filter((x) => x.readByIds.includes(staffId)).map((x) => x.sentAt);
        const lastRead = readTimes.length ? readTimes.sort().at(-1)! : null;
        const unread = msgs.filter((x) => lastRead === null || x.sentAt > lastRead).length;
        maxUnread = Math.max(maxUnread, unread);
        participant.add({
          thread_id: id,
          staff_id: ids.ref('staff_member', staffId),
          last_read_at: tsOpt(lastRead),
          joined_at: ts(firstSent),
        });
      }
      if (maxUnread !== t.unreadCount) {
        throw new Error(`${t.id}: derived unread ${maxUnread} != unreadCount ${t.unreadCount}`);
      }
    }
    for (const msg of m.MESSAGES) {
      message.add({
        id: ids.define('message', msg.id),
        thread_id: ids.ref('message_thread', msg.threadId),
        sender_id: ids.ref('staff_member', msg.senderId),
        sent_at: ts(msg.sentAt),
        body: msg.body,
        priority: msg.priority,
      });
    }

    const alert = new Table('clinical_alert', [
      'id',
      'type',
      'severity',
      'patient_id',
      'message',
      'created_at',
      'target_kind',
      'target_id',
      'target_patient_id',
    ]);
    const ack = new Table('alert_acknowledgement', ['alert_id', 'staff_id', 'acknowledged_at']);
    const targetTable: Record<string, string> = {
      lab_result: 'lab_result',
      imaging_result: 'imaging_result',
      patient_vitals: 'patient',
      lab_order: 'lab_order',
      imaging_order: 'imaging_order',
      task: 'team_task',
      patient: 'patient',
    };
    // tasks are defined below but alerts may point to them; define task ids first
    for (const t of m.TASKS) ids.define('team_task', t.id);
    for (const a of m.ALERTS) {
      const id = ids.define('clinical_alert', a.id);
      alert.add({
        id,
        type: a.type,
        severity: a.severity,
        patient_id: a.patientId ? ids.ref('patient', a.patientId) : null,
        message: a.message,
        created_at: ts(a.createdAt),
        target_kind: a.target?.kind ?? null,
        target_id: a.target ? ids.ref(targetTable[a.target.kind], a.target.id) : null,
        target_patient_id: a.target?.patientId ? ids.ref('patient', a.target.patientId) : null,
      });
      if (a.acknowledged && a.acknowledgedById) {
        ack.add({
          alert_id: id,
          staff_id: ids.ref('staff_member', a.acknowledgedById),
          acknowledged_at: a.acknowledgedAt ? ts(a.acknowledgedAt) : DEFAULT,
        });
      }
    }

    const task = new Table('team_task', [
      'id',
      'title',
      'description',
      'patient_id',
      'assigned_to_id',
      'created_by_id',
      'created_at',
      'updated_at',
      'due_at',
      'priority',
      'status',
    ]);
    for (const t of m.TASKS) {
      task.add({
        id: ids.ref('team_task', t.id),
        title: t.title,
        description: t.description ?? null,
        patient_id: t.patientId ? ids.ref('patient', t.patientId) : null,
        assigned_to_id: ids.ref('staff_member', t.assignedToId),
        created_by_id: ids.ref('staff_member', t.createdById),
        created_at: ts(t.createdAt),
        updated_at: ts(t.updatedAt ?? t.createdAt),
        due_at: tsOpt(t.dueAt),
        priority: t.priority,
        status: t.status,
      });
    }

    const handoff = new Table('handoff_note', [
      'id',
      'ward_id',
      'shift_date',
      'shift',
      'from_id',
      'to_id',
      'created_at',
      'general_notes',
    ]);
    const handoffPatient = new Table('handoff_patient_note', [
      'handoff_id',
      'patient_id',
      'situation',
      'background',
      'assessment',
      'recommendation',
    ]);
    for (const h of m.HANDOFF_NOTES) {
      const id = ids.define('handoff_note', h.id);
      handoff.add({
        id,
        ward_id: ids.ref('ward', h.wardId),
        shift_date: relDate(h.shiftDate),
        shift: h.shift,
        from_id: ids.ref('staff_member', h.fromId),
        to_id: ids.ref('staff_member', h.toId),
        created_at: ts(h.createdAt),
        general_notes: h.generalNotes ?? null,
      });
      for (const n of h.patientNotes) {
        handoffPatient.add({
          handoff_id: id,
          patient_id: ids.ref('patient', n.patientId),
          situation: n.situation,
          background: n.background,
          assessment: n.assessment,
          recommendation: n.recommendation,
        });
      }
    }

    const f = new FileBuilder('mock/010-messaging.sql', 'mock');
    f.changeset('010-message-thread', thread);
    f.changeset('010-thread-participant', participant);
    f.changeset('010-message', message);
    f.changeset('010-clinical-alert', alert);
    f.changeset('010-alert-acknowledgement', ack);
    f.changeset('010-team-task', task);
    f.changeset('010-handoff-note', handoff);
    f.changeset('010-handoff-patient-note', handoffPatient);
    finish(f, 'mock');
  }

  return { files, manifest };
}
