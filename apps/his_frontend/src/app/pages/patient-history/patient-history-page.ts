import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, PrimeTemplate } from 'primeng/api';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Timeline } from 'primeng/timeline';
import { Button } from 'primeng/button';
import { forkJoin } from 'rxjs';
import { rxResource } from '@angular/core/rxjs-interop';
import { DataTable } from '../../components/data-table/data-table';
import { EmptyState } from '../../components/empty-state/empty-state';
import { PageHeader } from '../../components/page-header/page-header';
import { SectionHeader } from '../../components/section-header/section-header';
import { StatusTag } from '../../components/status-tag/status-tag';
import { EhrSummaryCards } from '../../components/ehr-summary-cards/ehr-summary-cards';
import {
  ClinicalNoteDialog,
  type ClinicalNoteDialogSave,
} from '../../components/clinical-note-dialog/clinical-note-dialog';
import { NOTE_CATEGORY_OPTIONS } from '../../constants/labels';
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
import { EhrService } from '../../services/ehr.service';
import { StaffService } from '../../services/staff.service';
import { WardService } from '../../services/ward.service';

type HistoryTab = 'overview' | 'encounters' | 'notes' | 'diagnoses' | 'treatments' | 'allergies';

interface EpisodeGroup {
  episode: TreatmentEpisode | null;
  encounters: Encounter[];
}

/** `app-data-table` requires `T extends Record<string, unknown>`; domain models don't declare an index signature. */
type DiagnosisRow = Diagnosis & Record<string, unknown>;
type TreatmentRow = Treatment & Record<string, unknown>;

const VALID_TABS: HistoryTab[] = [
  'overview',
  'encounters',
  'notes',
  'diagnoses',
  'treatments',
  'allergies',
];

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
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    Timeline,
    PrimeTemplate,
    Message,
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
  private readonly staffService = inject(StaffService);
  private readonly wardService = inject(WardService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(MessageService);

  readonly patientId = input.required<string>();
  readonly tab = input<string>();

  protected readonly currentUserId = computed(() => this.staffService.currentUser().id);

  protected readonly activeTab = computed<HistoryTab>(() => {
    const t = this.tab();
    return (VALID_TABS as string[]).includes(t ?? '') ? (t as HistoryTab) : 'overview';
  });

  protected readonly noteCategoryOptions = NOTE_CATEGORY_OPTIONS;
  protected readonly noteCategoryFilter = signal<NoteCategory | null>(null);
  protected readonly noteDialogVisible = signal(false);

  protected get noteCategoryFilterValue(): NoteCategory | null {
    return this.noteCategoryFilter();
  }

  protected set noteCategoryFilterValue(value: NoteCategory | null) {
    this.noteCategoryFilter.set(value);
  }

  private readonly ehrResource = rxResource({
    params: () => this.patientId(),
    stream: ({ params: pid }) =>
      forkJoin({
        summary: this.ehrService.getSummary(pid),
        encounters: this.ehrService.getEncounters(pid),
        episodes: this.ehrService.getEpisodes(pid),
        notes: this.ehrService.getNotes(pid),
        diagnoses: this.ehrService.getDiagnoses(pid),
        treatments: this.ehrService.getTreatments(pid),
        allergies: this.ehrService.getAllergies(pid),
        contraindications: this.ehrService.getContraindications(pid),
      }),
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
  protected readonly diagnoses = computed<DiagnosisRow[]>(
    () => (this.ehrResource.value()?.diagnoses ?? []) as DiagnosisRow[],
  );
  protected readonly treatments = computed<TreatmentRow[]>(
    () => (this.ehrResource.value()?.treatments ?? []) as TreatmentRow[],
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

  protected readonly diagnosisColumns: TableColumn<DiagnosisRow>[] = [
    { field: 'icdCode', header: 'Kod ICD-10', type: 'custom' },
    { field: 'icdName', header: 'Jednostka chorobowa', type: 'custom' },
    { field: 'type', header: 'Typ', type: 'custom' },
    { field: 'status', header: 'Status', type: 'custom' },
    { field: 'diagnosedAt', header: 'Data', type: 'date', sortable: true },
    { field: 'diagnosedById', header: 'Lekarz', type: 'custom' },
  ];

  protected readonly treatmentColumns: TableColumn<TreatmentRow>[] = [
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

  protected trackEpisode(_index: number, group: EpisodeGroup): string {
    return group.episode?.id ?? 'ungrouped';
  }

  protected departmentName(wardId: string | undefined): string {
    return wardId ? this.wardService.nameOf(wardId) : '';
  }
}
