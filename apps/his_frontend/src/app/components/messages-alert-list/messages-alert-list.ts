import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';
import type { ClinicalAlert } from '../../models';
import { StatusTag } from '../status-tag/status-tag';

@Component({
  selector: 'app-messages-alert-list',
  imports: [RouterLink, Button, Tag, StatusTag],
  templateUrl: './messages-alert-list.html',
  styleUrl: './messages-alert-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'messages-alert-list' },
})
export class MessagesAlertList {
  readonly alerts = input.required<ClinicalAlert[]>();
  readonly patientLabel = input.required<(patientId: string) => string>();
  readonly alertAgo = input.required<(alert: ClinicalAlert) => string>();
  readonly acknowledge = output<ClinicalAlert>();
}
