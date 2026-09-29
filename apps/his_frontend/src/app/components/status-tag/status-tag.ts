import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tag } from 'primeng/tag';
import { TAG_SEVERITY_MAP, type TagKind } from '../../constants/tag-severity';
import { LabelPipe, type LabelMapKey } from '../../pipes/label.pipe';

@Component({
  selector: 'app-status-tag',
  imports: [Tag, LabelPipe],
  templateUrl: './status-tag.html',
  styleUrl: './status-tag.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusTag {
  readonly kind = input.required<TagKind>();
  readonly value = input.required<string>();

  protected readonly severity = computed(
    () => TAG_SEVERITY_MAP[this.kind()][this.value()] ?? 'secondary',
  );

  /** Every `TagKind` is also a valid `LabelPipe` map key. */
  protected readonly labelMapKey = computed(() => this.kind() as unknown as LabelMapKey);
}
