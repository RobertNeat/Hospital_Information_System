import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Skeleton } from 'primeng/skeleton';

@Component({
  selector: 'app-loading-block',
  imports: [Skeleton],
  templateUrl: './loading-block.html',
  styleUrl: './loading-block.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'loading-block' },
})
export class LoadingBlock {
  readonly lines = input(3);
  protected readonly lineArray = computed(() => Array.from({ length: this.lines() }));
}
