import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { Popover } from 'primeng/popover';
import { Tag } from 'primeng/tag';
import { Button } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { WardService } from '../../services/ward.service';
import { StaffNamePipe } from '../../pipes/staff-name.pipe';
import { AgePipe } from '../../pipes/age.pipe';
import { GENDER_LABELS, PATIENT_FLAG_LABELS } from '../../constants/labels';
import { ALLERGY_SEVERITY_SEVERITY, TRIAGE_SEVERITY } from '../../constants/tag-severity';
import type { Allergy, Patient } from '../../models';

export type PatientBarAction =
  'lab-order' | 'imaging-order' | 'prescription' | 'vitals' | 'message' | 'edit';

@Component({
  selector: 'app-patient-context-bar',
  imports: [Popover, Tag, Button, Tooltip, StaffNamePipe, AgePipe],
  templateUrl: './patient-context-bar.html',
  styleUrl: './patient-context-bar.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'patient-context-bar' },
})
export class PatientContextBar {
  private readonly wardService = inject(WardService);

  readonly patient = input.required<Patient>();
  readonly allergies = input<Allergy[]>([]);
  readonly activeAlerts = input(0);

  readonly action = output<PatientBarAction>();

  protected readonly genderLabel = computed(() => GENDER_LABELS[this.patient().gender]);
  protected readonly flagLabels = computed(() =>
    this.patient().flags.map((f) => PATIENT_FLAG_LABELS[f]),
  );
  protected readonly activeAllergies = computed(() =>
    this.allergies().filter((a) => a.status === 'active'),
  );
  protected readonly triageSeverity = computed(() => {
    const level = this.patient().currentAdmission?.triageLevel;
    return level ? TRIAGE_SEVERITY[level] : undefined;
  });
  protected readonly worstAllergySeverity = computed(() => {
    const severities = this.activeAllergies().map((a) => ALLERGY_SEVERITY_SEVERITY[a.severity]);
    return severities.includes('danger') ? 'danger' : severities.length ? 'warn' : undefined;
  });

  protected readonly wardName = computed(() => {
    const admission = this.patient().currentAdmission;
    return admission?.wardId ? this.wardService.nameOf(admission.wardId) : undefined;
  });

  protected emit(a: PatientBarAction): void {
    this.action.emit(a);
  }
}
