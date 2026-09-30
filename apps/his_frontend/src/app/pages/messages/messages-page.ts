import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MessageService as ToastService } from 'primeng/api';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { MultiSelect } from 'primeng/multiselect';
import { SelectButton } from 'primeng/selectbutton';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Tag } from 'primeng/tag';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';

import { PageHeader } from '../../components/page-header/page-header';
import { EmptyState } from '../../components/empty-state/empty-state';
import { LoadingBlock } from '../../components/loading-block/loading-block';
import { DataTable } from '../../components/data-table/data-table';
import { MessagesAlertList } from '../../components/messages-alert-list/messages-alert-list';
import { MessagesHandoffList } from '../../components/messages-handoff-list/messages-handoff-list';
import { FormField } from '../../components/form-field/form-field';
import { PatientSearch } from '../../components/patient-search/patient-search';
import { MessageThreadList } from '../../components/message-thread-list/message-thread-list';
import { MessageConversation } from '../../components/message-conversation/message-conversation';
import { TaskDialog } from '../../components/task-dialog/task-dialog';
import { HandoffNoteDialog } from '../../components/handoff-note-dialog/handoff-note-dialog';

import { StaffService } from '../../services/staff.service';
import { WardService } from '../../services/ward.service';
import { PatientService } from '../../services/patient.service';
import type { StaffMember, Ward } from '../../models';
import { createAlertsState, createHandoffState } from './messages-board.state';
import { createInboxState } from './messages-inbox.state';
import { createTasksState } from './messages-tasks.state';
import { createPatientCache, type MessagesContext } from './messages.context';

type MessagesTab = 'inbox' | 'tasks' | 'handoff' | 'alerts';

const TAB_VALUES: Record<MessagesTab, string> = {
  inbox: 'inbox',
  tasks: 'tasks',
  handoff: 'handoff',
  alerts: 'alerts',
};

@Component({
  selector: 'app-messages-page',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    PageHeader,
    EmptyState,
    LoadingBlock,
    DataTable,
    MessagesAlertList,
    MessagesHandoffList,
    FormField,
    PatientSearch,
    MessageThreadList,
    MessageConversation,
    TaskDialog,
    HandoffNoteDialog,
    Button,
    Dialog,
    InputText,
    Message,
    MultiSelect,
    SelectButton,
    Tab,
    TabList,
    TabPanel,
    TabPanels,
    Tabs,
    Tag,
    Textarea,
    Tooltip,
  ],
  templateUrl: './messages-page.html',
  styleUrl: './messages-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'messages-page' },
})
export class MessagesPage {
  private readonly staffService = inject(StaffService);
  private readonly wardService = inject(WardService);
  private readonly router = inject(Router);

  readonly tab = input<MessagesTab>('inbox');
  readonly thread = input<string>();
  readonly patientId = input<string>();

  protected readonly currentUser = this.staffService.currentUser;

  // ---- shared reference data ----
  protected readonly messagingStaff = signal<StaffMember[]>([]);
  protected readonly wards = signal<Ward[]>([]);
  private readonly patients = createPatientCache(inject(PatientService));
  protected readonly patientLabel = this.patients.label;

  // ---- per-tab state ----
  private readonly ctx: MessagesContext = {
    currentUser: this.currentUser,
    toast: inject(ToastService),
    router: this.router,
    patients: this.patients,
  };
  protected readonly inbox = createInboxState(this.ctx, this.thread);
  protected readonly taskTab = createTasksState(this.ctx, this.patientId);
  protected readonly handoff = createHandoffState(this.ctx);
  protected readonly alertTab = createAlertsState(this.ctx);

  protected readonly taskFilter = this.taskTab.taskFilter;
  protected readonly taskRows = this.taskTab.taskRows;
  protected readonly sortedAlerts = this.alertTab.sortedAlerts;

  constructor() {
    this.staffService.getStaff('doctor').subscribe((doctors) => {
      this.staffService.getStaff('nurse').subscribe((nurses) => {
        this.messagingStaff.set([...doctors, ...nurses]);
      });
    });
    this.wardService.getWards().subscribe((wards) => this.wards.set(wards));
    this.inbox.load();
    this.taskTab.load();
    this.handoff.load();
    this.alertTab.load();

    const initialPatientId = this.patientId();
    if (initialPatientId) {
      this.patients.resolve(initialPatientId);
    }
  }

  protected readonly activeTab = computed(() => TAB_VALUES[this.tab()]);

  protected onTabChange(value: string | number | undefined): void {
    if (value === undefined) return;
    this.router.navigate([], { queryParams: { tab: value }, queryParamsHandling: 'merge' });
  }
}
