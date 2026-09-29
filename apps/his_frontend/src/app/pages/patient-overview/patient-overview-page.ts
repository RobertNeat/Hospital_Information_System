import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  LOCALE_ID,
  signal,
} from '@angular/core';
import { DatePipe, formatDate } from '@angular/common';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { PageHeader } from '../../components/page-header/page-header';
import { EmptyState } from '../../components/empty-state/empty-state';
import { SectionHeader } from '../../components/section-header/section-header';
import { SummaryList, type SummaryItem } from '../../components/summary-list/summary-list';
import {
  IconActionGroup,
  type IconAction,
} from '../../components/icon-action-group/icon-action-group';
import { StatusTag } from '../../components/status-tag/status-tag';
import { PatientContextService } from '../../services/patient-context.service';
import { PatientService } from '../../services/patient.service';
import { WardService } from '../../services/ward.service';
import { StaffService } from '../../services/staff.service';
import { VitalsService } from '../../services/vitals.service';
import { EhrService } from '../../services/ehr.service';
import { ageFromBirthDate } from '../../utils/date-utils';
import {
  ADMISSION_TYPE_LABELS,
  GENDER_LABELS,
  INSURANCE_PAYER_LABELS,
  INSURANCE_STATUS_LABELS,
  NFZ_BRANCH_OPTIONS,
  TRIAGE_LABELS,
} from '../../constants/labels';
import type { Allergy, VitalSigns } from '../../models';

@Component({
  selector: 'app-patient-overview-page',
  imports: [
    PageHeader,
    EmptyState,
    SectionHeader,
    SummaryList,
    IconActionGroup,
    StatusTag,
    Button,
    DatePipe,
  ],
  templateUrl: './patient-overview-page.html',
  styleUrl: './patient-overview-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'patient-overview-page' },
})
export class PatientOverviewPage {
  private readonly router = inject(Router);
  private readonly patientService = inject(PatientService);
  private readonly wardService = inject(WardService);
  private readonly staffService = inject(StaffService);
  private readonly vitalsService = inject(VitalsService);
  private readonly ehrService = inject(EhrService);
  private readonly messageService = inject(MessageService);
  private readonly confirmationService = inject(ConfirmationService);
  protected readonly ctx = inject(PatientContextService);
  private readonly locale = inject(LOCALE_ID);

  readonly patientId = input.required<string>();

  protected readonly allergies = signal<Allergy[]>([]);
  protected readonly latestVitals = signal<VitalSigns | undefined>(undefined);
  protected readonly vitalsLoaded = signal(false);
  protected readonly discharging = signal(false);

  protected readonly patient = computed(() => this.ctx.patient());

  private formatDate(value: string): string {
    return formatDate(value, 'dd.MM.yyyy', this.locale);
  }

  private formatDateTime(value: string): string {
    return formatDate(value, 'dd.MM.yyyy HH:mm', this.locale);
  }

  protected readonly basicInfoItems = computed<SummaryItem[]>(() => {
    const p = this.patient();
    if (!p) return [];
    return [
      {
        label: 'Imię i nazwisko',
        value: `${p.firstName} ${p.secondName ?? ''} ${p.lastName}`.replace(/\s+/g, ' ').trim(),
      },
      { label: 'PESEL', value: p.pesel ?? 'brak PESEL' },
      { label: 'Data urodzenia', value: this.formatDate(p.birthDate) },
      { label: 'Wiek', value: `${ageFromBirthDate(p.birthDate)} l.` },
      { label: 'Płeć', value: GENDER_LABELS[p.gender] },
      { label: 'Nr historii choroby', value: p.mrn },
      { label: 'Grupa krwi', value: p.bloodType ?? null },
    ];
  });

  protected readonly contactItems = computed<SummaryItem[]>(() => {
    const p = this.patient();
    if (!p) return [];
    const a = p.address;
    return [
      { label: 'Telefon', value: p.phone ?? null },
      { label: 'E-mail', value: p.email ?? null },
      {
        label: 'Adres',
        value: `${a.street} ${a.buildingNumber}${a.apartmentNumber ? '/' + a.apartmentNumber : ''}, ${a.postalCode} ${a.city}, ${a.country}`,
      },
    ];
  });

  protected readonly insuranceItems = computed<SummaryItem[]>(() => {
    const p = this.patient();
    if (!p) return [];
    const branch = NFZ_BRANCH_OPTIONS.find((b) => b.value === p.insurance.nfzBranch)?.label;
    return [
      { label: 'Status ubezpieczenia', value: INSURANCE_STATUS_LABELS[p.insurance.status] },
      { label: 'Płatnik', value: INSURANCE_PAYER_LABELS[p.insurance.payer] },
      { label: 'Oddział NFZ', value: branch ?? p.insurance.nfzBranch ?? null },
      {
        label: 'Weryfikacja eWUŚ',
        value: p.insurance.ewusVerifiedAt ? this.formatDateTime(p.insurance.ewusVerifiedAt) : null,
      },
    ];
  });

  protected readonly contactPersonItems = computed<SummaryItem[]>(() => {
    const c = this.patient()?.emergencyContact;
    if (!c) return [];
    return [
      { label: 'Imię i nazwisko', value: c.fullName },
      { label: 'Relacja', value: c.relation },
      { label: 'Telefon', value: c.phone },
      { label: 'Opiekun prawny', value: c.isLegalGuardian ? 'Tak' : 'Nie' },
    ];
  });

  protected readonly admissionItems = computed<SummaryItem[]>(() => {
    const a = this.patient()?.currentAdmission;
    if (!a) return [];
    return [
      { label: 'Typ przyjęcia', value: ADMISSION_TYPE_LABELS[a.admissionType] },
      { label: 'Data przyjęcia', value: this.formatDateTime(a.admittedAt) },
      { label: 'Oddział', value: this.wardService.nameOf(a.wardId) },
      { label: 'Sala/Łóżko', value: [a.room, a.bed].filter(Boolean).join(' / ') || null },
      {
        label: 'Lekarz prowadzący',
        value: this.staffService.nameOf(a.attendingPhysicianId),
      },
      {
        label: 'Triage',
        value: a.triageLevel ? TRIAGE_LABELS[a.triageLevel] : null,
      },
      { label: 'Powód przyjęcia', value: a.reason },
      { label: 'Nr skierowania', value: a.referralNumber ?? null },
    ];
  });

  protected readonly quickActions: IconAction[] = [
    { id: 'history', icon: 'pi pi-book', label: 'Historia choroby' },
    { id: 'results', icon: 'pi pi-chart-bar', label: 'Wyniki badań' },
    { id: 'vitals', icon: 'pi pi-heart', label: 'Parametry życiowe' },
    { id: 'orders', icon: 'pi pi-list-check', label: 'Zlecenia' },
    { id: 'prescriptions', icon: 'pi pi-file-edit', label: 'Leki i recepty' },
  ];

  constructor() {
    toObservable(this.patientId)
      .pipe(
        switchMap((id) => {
          this.vitalsLoaded.set(false);
          return this.ehrService.getAllergies(id);
        }),
        takeUntilDestroyed(),
      )
      .subscribe((allergies) => this.allergies.set(allergies));

    toObservable(this.patientId)
      .pipe(
        switchMap((id) => this.vitalsService.getLatest(id)),
        takeUntilDestroyed(),
      )
      .subscribe((v) => {
        this.latestVitals.set(v);
        this.vitalsLoaded.set(true);
      });
  }

  protected onQuickAction(id: string): void {
    this.router.navigate(['/patients', this.patientId(), id]);
  }

  protected editPatient(): void {
    this.router.navigate(['/patients', this.patientId(), 'edit']);
  }

  protected dischargePatient(): void {
    this.confirmationService.confirm({
      header: 'Wypis pacjenta',
      message: 'Czy na pewno chcesz wypisać tego pacjenta?',
      acceptLabel: 'Tak, wypisz',
      rejectLabel: 'Anuluj',
      accept: () => {
        this.discharging.set(true);
        this.patientService
          .dischargePatient(this.patientId(), new Date().toISOString())
          .pipe(switchMap(() => this.ctx.refresh()))
          .subscribe({
            next: () => {
              this.discharging.set(false);
              this.messageService.add({
                severity: 'success',
                summary: 'Pacjent wypisany',
              });
            },
            error: () => {
              this.discharging.set(false);
              this.messageService.add({
                severity: 'error',
                summary: 'Nie udało się wypisać pacjenta',
              });
            },
          });
      },
    });
  }
}
