import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, PrimeTemplate } from 'primeng/api';
import { Select } from 'primeng/select';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Timeline } from 'primeng/timeline';
import { Button } from 'primeng/button';
import { catchError, forkJoin, of, tap } from 'rxjs';
import type { Observable } from 'rxjs';
import { rxResource } from '@angular/core/rxjs-interop';
import { DataTable } from '../../components/data-table/data-table';
import { EmptyState } from '../../components/empty-state/empty-state';
import { PageHeader } from '../../components/page-header/page-header';
import { SectionHeader } from '../../components/section-header/section-header';
import { StatusTag } from '../../components/status-tag/status-tag';
import { HistoryAllergiesPanel } from '../../components/history-allergies-panel/history-allergies-panel';
import { EhrSummaryCards } from '../../components/ehr-summary-cards/ehr-summary-cards';
import {
  ClinicalNoteDialog,
  type ClinicalNoteDialogSave,
} from '../../components/clinical-note-dialog/clinical-note-dialog';
import {
  DiagnosisDialog,
  type DiagnosisDialogSave,
} from '../../components/diagnosis-dialog/diagnosis-dialog';
import {
  AllergyDialog,
  type AllergyDialogSave,
} from '../../components/allergy-dialog/allergy-dialog';
import { NOTE_CATEGORY_OPTIONS } from '../../constants/labels';
import { PERMISSIONS } from '../../constants/permissions';
import type {
  Allergy,
  ClinicalNote,
  Diagnosis,
  Encounter,
  NoteCategory,
  TableColumn,
  Treatment,
  TreatmentEpisode,
} from '../../models';
import { LabelPipe } from '../../pipes/label.pipe';
import { StaffNamePipe } from '../../pipes/staff-name.pipe';
import { AuthService } from '../../services/auth.service';
import { EhrService } from '../../services/ehr.service';
import { WardService } from '../../services/ward.service';

type HistoryTab = 'overview' | 'encounters' | 'notes' | 'diagnoses' | 'treatments' | 'allergies';

interface EpisodeGroup {
  episode: TreatmentEpisode | null;
  encounters: Encounter[];
}

const VALID_TABS: HistoryTab[] = [
  'overview',
  'encounters',
  'notes',
  'diagnoses',
  'treatments',
  'allergies',
];

/** Tabs backed by `ehr:read` only; `ehr:read-limited` sees just diagnoses, allergies, treatments. */
const FULL_ONLY_TABS: HistoryTab[] = ['overview', 'encounters', 'notes'];

@Component({
  selector: 'app-patient-history-page',
  imports: [
    PageHeader,
    SectionHeader,
    EmptyState,
    DataTable,
    StatusTag,
    EhrSummaryCards,
    ClinicalNoteDialog,
    DiagnosisDialog,
    AllergyDialog,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    Timeline,
    PrimeTemplate,
    HistoryAllergiesPanel,
    Select,
    Button,
    FormsModule,
    DatePipe,
    LabelPipe,
    StaffNamePipe,
  ],
  templateUrl: './patient-history-page.html',
  styleUrl: './patient-history-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'patient-history-page' },
})
export class PatientHistoryPage {
  private readonly ehrService = inject(EhrService);
  private readonly wardService = inject(WardService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(MessageService);
  private readonly auth = inject(AuthService);

  readonly patientId = input.required<string>();
  readonly tab = input<string>();

  /** Full chart (`ehr:read`); otherwise only the limited sections are requested and shown. */
  protected readonly canReadFull = computed(() => this.auth.hasPermission('ehr:read'));

  /** `ehr:diagnosis:write` (lekarz) / `ehr:allergy:write` (lekarz, pielegniarka). */
  protected readonly canWriteDiagnosis = computed(() =>
    this.auth.hasPermission(PERMISSIONS.EHR_DIAGNOSIS_WRITE),
  );
  protected readonly canWriteAllergy = computed(() =>
    this.auth.hasPermission(PERMISSIONS.EHR_ALLERGY_WRITE),
  );

  protected readonly activeTab = computed<HistoryTab>(() => {
    const t = this.tab();
    const requested = (VALID_TABS as string[]).includes(t ?? '') ? (t as HistoryTab) : 'overview';
    return !this.canReadFull() && FULL_ONLY_TABS.includes(requested) ? 'diagnoses' : requested;
  });

  protected readonly noteCategoryOptions = NOTE_CATEGORY_OPTIONS;
  protected readonly noteCategoryFilter = signal<NoteCategory | null>(null);
  protected readonly noteDialogVisible = signal(false);
  protected readonly diagnosisDialogVisible = signal(false);
  protected readonly allergyDialogVisible = signal(false);

  protected get noteCategoryFilterValue(): NoteCategory | null {
    return this.noteCategoryFilter();
  }

  protected set noteCategoryFilterValue(value: NoteCategory | null) {
    this.noteCategoryFilter.set(value);
  }

  private readonly ehrResource = rxResource({
    params: () => this.patientId(),
    stream: ({ params: pid }) => {
      // Sections load independently: one failing (e.g. 403) must not blank the others.
      let failed = false;
      const section = <T, F>(source: Observable<T>, fallback: F): Observable<T | F> =>
        source.pipe(
          catchError(() => {
            failed = true;
            return of(fallback);
          }),
        );
      const full = this.canReadFull();
      const none = <F>(fallback: F): Observable<F> => of(fallback);
      return forkJoin({
        summary: full ? section(this.ehrService.getSummary(pid), null) : none(null),
        encounters: full ? section(this.ehrService.getEncounters(pid), []) : none([]),
        episodes: full ? section(this.ehrService.getEpisodes(pid), []) : none([]),
        notes: full ? section(this.ehrService.getNotes(pid), []) : none([]),
        diagnoses: section(this.ehrService.getDiagnoses(pid), []),
        treatments: section(this.ehrService.getTreatments(pid), []),
        allergies: section(this.ehrService.getAllergies(pid), []),
        contraindications: section(this.ehrService.getContraindications(pid), []),
      }).pipe(
        tap(() => {
          if (failed) {
            this.toast.add({
              severity: 'warn',
              summary: 'Historia choroby',
              detail: 'Nie wszystkie sekcje udało się wczytać.',
            });
          }
        }),
      );
    },
  });

  protected readonly loading = computed(() => this.ehrResource.isLoading());
  protected readonly summary = computed(() => this.ehrResource.value()?.summary ?? null);
  protected readonly encounters = computed<Encounter[]>(
    () => this.ehrResource.value()?.encounters ?? [],
  );
  protected readonly episodes = computed<TreatmentEpisode[]>(
    () => this.ehrResource.value()?.episodes ?? [],
  );
  protected readonly notes = computed<ClinicalNote[]>(() => this.ehrResource.value()?.notes ?? []);
  protected readonly diagnoses = computed<Diagnosis[]>(
    () => this.ehrResource.value()?.diagnoses ?? [],
  );
  protected readonly treatments = computed<Treatment[]>(
    () => this.ehrResource.value()?.treatments ?? [],
  );
  protected readonly allergies = computed<Allergy[]>(
    () => this.ehrResource.value()?.allergies ?? [],
  );
  protected readonly contraindications = computed(
    () => this.ehrResource.value()?.contraindications ?? [],
  );

  protected readonly lifeThreateningAllergies = computed(() =>
    this.allergies().filter((a) => a.severity === 'life_threatening' && a.status === 'active'),
  );

  protected readonly filteredNotes = computed(() => {
    const category = this.noteCategoryFilter();
    const notes = [...this.notes()].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return category ? notes.filter((n) => n.category === category) : notes;
  });

  protected readonly episodeGroups = computed<EpisodeGroup[]>(() => {
    const episodes = this.episodes();
    const encounters = [...this.encounters()].sort((a, b) => a.startAt.localeCompare(b.startAt));
    const groups: EpisodeGroup[] = episodes.map((episode) => ({
      episode,
      encounters: encounters.filter((e) => e.episodeId === episode.id),
    }));
    const ungrouped = encounters.filter((e) => !e.episodeId);
    if (ungrouped.length) {
      groups.push({ episode: null, encounters: ungrouped });
    }
    return groups;
  });

  protected readonly diagnosisColumns: TableColumn<Diagnosis>[] = [
    { field: 'icdCode', header: 'Kod ICD-10', type: 'custom' },
    { field: 'icdName', header: 'Jednostka chorobowa', type: 'custom' },
    { field: 'type', header: 'Typ', type: 'custom' },
    { field: 'status', header: 'Status', type: 'custom' },
    { field: 'diagnosedAt', header: 'Data', type: 'date', sortable: true },
    { field: 'diagnosedById', header: 'Lekarz', type: 'custom' },
  ];

  protected readonly treatmentColumns: TableColumn<Treatment>[] = [
    { field: 'name', header: 'Nazwa', sortable: true },
    { field: 'type', header: 'Typ', type: 'custom' },
    { field: 'status', header: 'Status', type: 'custom' },
    { field: 'startAt', header: 'Data rozpoczęcia', type: 'date', sortable: true },
    { field: 'practitionerId', header: 'Lekarz', type: 'custom' },
  ];

  protected onTabChange(value: string | number | undefined): void {
    if (value === undefined) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: String(value) },
      queryParamsHandling: 'merge',
    });
  }

  protected openNoteDialog(): void {
    this.noteDialogVisible.set(true);
  }

  protected onNoteSave(event: ClinicalNoteDialogSave): void {
    this.ehrService.addNote(event.draft).subscribe({
      next: () => {
        this.toast.add({
          severity: 'success',
          summary: 'Notatka dodana',
          detail: 'Notatka kliniczna została zapisana.',
        });
        this.ehrResource.reload();
      },
      error: () => {
        this.toast.add({
          severity: 'error',
          summary: 'Błąd',
          detail: 'Nie udało się zapisać notatki klinicznej.',
        });
      },
    });
  }

  protected openDiagnosisDialog(): void {
    this.diagnosisDialogVisible.set(true);
  }

  protected onDiagnosisSave(event: DiagnosisDialogSave): void {
    this.ehrService.addDiagnosis(this.patientId(), event.draft).subscribe({
      next: () => {
        this.toast.add({
          severity: 'success',
          summary: 'Rozpoznanie dodane',
          detail: 'Rozpoznanie zostało zapisane.',
        });
        this.ehrResource.reload();
      },
      error: () => {
        this.toast.add({
          severity: 'error',
          summary: 'Błąd',
          detail: 'Nie udało się zapisać rozpoznania.',
        });
      },
    });
  }

  protected openAllergyDialog(): void {
    this.allergyDialogVisible.set(true);
  }

  protected onAllergySave(event: AllergyDialogSave): void {
    this.ehrService.addAllergy(this.patientId(), event.draft).subscribe({
      next: () => {
        this.toast.add({
          severity: 'success',
          summary: 'Alergia dodana',
          detail: 'Alergia została zapisana.',
        });
        this.ehrResource.reload();
      },
      error: () => {
        this.toast.add({
          severity: 'error',
          summary: 'Błąd',
          detail: 'Nie udało się zapisać alergii.',
        });
      },
    });
  }

  protected trackEpisode(_index: number, group: EpisodeGroup): string {
    return group.episode?.id ?? 'ungrouped';
  }

  protected departmentName(wardId: string | undefined): string {
    return wardId ? this.wardService.nameOf(wardId) : '';
  }
}
