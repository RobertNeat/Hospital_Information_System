import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EmptyState } from '../../components/empty-state/empty-state';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink, EmptyState],
  templateUrl: './not-found-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'not-found-page' },
})
export class NotFoundPage {}
