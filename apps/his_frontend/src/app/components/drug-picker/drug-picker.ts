import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AutoComplete, type AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { Tag } from 'primeng/tag';
import { DrugService } from '../../services/drug.service';
import { LabelPipe } from '../../pipes/label.pipe';
import type { Drug } from '../../models';

@Component({
  selector: 'app-drug-picker',
  imports: [AutoComplete, Tag, FormsModule, LabelPipe],
  templateUrl: './drug-picker.html',
  styleUrl: './drug-picker.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'drug-picker' },
})
export class DrugPicker {
  private readonly drugService = inject(DrugService);

  readonly drugSelected = output<Drug>();

  protected readonly suggestions = signal<Drug[]>([]);
  protected readonly selected = signal<Drug | null>(null);
  protected readonly searching = signal(false);

  protected search(event: AutoCompleteCompleteEvent): void {
    this.searching.set(true);
    this.drugService.search(event.query).subscribe((drugs) => {
      this.suggestions.set(drugs);
      this.searching.set(false);
    });
  }

  protected onSelect(drug: Drug): void {
    this.selected.set(drug);
    this.drugSelected.emit(drug);
  }
}
