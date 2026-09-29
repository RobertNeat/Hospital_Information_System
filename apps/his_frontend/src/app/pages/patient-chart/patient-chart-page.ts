import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { switchMap } from 'rxjs';
import {
  PatientContextBar,
  type PatientBarAction,
} from '../../components/patient-context-bar/patient-context-bar';
import { PatientChartNav } from '../../components/patient-chart-nav/patient-chart-nav';
import { EhrService } from '../../services/ehr.service';
import { TeamMessageService } from '../../services/team-message.service';
import { PatientContextService } from '../../services/patient-context.service';

@Component({
  selector: 'app-patient-chart-page',
  imports: [RouterOutlet, PatientContextBar, PatientChartNav],
  templateUrl: './patient-chart-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientChartPage {
  private readonly ehrService = inject(EhrService);
  private readonly teamMessageService = inject(TeamMessageService);
  private readonly router = inject(Router);
  protected readonly ctx = inject(PatientContextService);

  readonly patientId = input.required<string>();

  private readonly patientId$ = toObservable(this.patientId);

  protected readonly allergies = toSignal(
    this.patientId$.pipe(switchMap((id) => this.ehrService.getAllergies(id))),
    { initialValue: [] },
  );

  protected readonly alertsCount = toSignal(
    this.patientId$.pipe(
      switchMap((id) => this.teamMessageService.getAlerts({ patientId: id, acknowledged: false })),
    ),
    { initialValue: [] },
  );

  protected onBarAction(action: PatientBarAction): void {
    const id = this.patientId();
    switch (action) {
      case 'lab-order':
        this.router.navigate(['/patients', id, 'orders', 'lab', 'new']);
        break;
      case 'imaging-order':
        this.router.navigate(['/patients', id, 'orders', 'imaging', 'new']);
        break;
      case 'prescription':
        this.router.navigate(['/patients', id, 'prescriptions', 'new']);
        break;
      case 'vitals':
        this.router.navigate(['/patients', id, 'vitals']);
        break;
      case 'message':
        this.router.navigate(['/messages'], { queryParams: { tab: 'tasks', patientId: id } });
        break;
      case 'edit':
        this.router.navigate(['/patients', id, 'edit']);
        break;
    }
  }
}
