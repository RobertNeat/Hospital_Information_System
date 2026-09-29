import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Checkbox } from 'primeng/checkbox';
import { Button } from 'primeng/button';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import type { LabCategory, LabTest } from '../../models';
import { LAB_CATEGORY_LABELS } from '../../constants/labels';

export interface CatalogPanel {
  id: string;
  name: string;
  testCodes: string[];
}

interface CatalogGroup {
  category: LabCategory;
  label: string;
  tests: LabTest[];
}

/**
 * Grouped, searchable checkbox picker over a lab test catalog, plus panel buttons that
 * select every test in a panel at once. Selected test codes are exposed as a `model()`.
 */
@Component({
  selector: 'app-catalog-picker',
  imports: [FormsModule, Checkbox, Button, IconField, InputIcon, InputText],
  templateUrl: './catalog-picker.html',
  styleUrl: './catalog-picker.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'catalog-picker' },
})
export class CatalogPicker {
  readonly items = input.required<LabTest[]>();
  readonly panels = input<CatalogPanel[]>([]);
  readonly selectedCodes = model<string[]>([]);

  protected readonly search = signal('');

  protected readonly groups = computed<CatalogGroup[]>(() => {
    const term = this.search().trim().toLowerCase();
    const filtered = term
      ? this.items().filter(
          (t) => t.name.toLowerCase().includes(term) || t.code.toLowerCase().includes(term),
        )
      : this.items();

    const byCategory = new Map<LabCategory, LabTest[]>();
    for (const test of filtered) {
      const bucket = byCategory.get(test.category);
      if (bucket) bucket.push(test);
      else byCategory.set(test.category, [test]);
    }
    return Array.from(byCategory.entries())
      .map(([category, tests]) => ({
        category,
        label: LAB_CATEGORY_LABELS[category],
        tests: [...tests].sort((a, b) => a.name.localeCompare(b.name)),
      }))
      .sort((a, b) => a.label.localeCompare(b.label));
  });

  protected isSelected(code: string): boolean {
    return this.selectedCodes().includes(code);
  }

  protected toggle(code: string, checked: boolean): void {
    const current = this.selectedCodes();
    if (checked) {
      if (!current.includes(code)) this.selectedCodes.set([...current, code]);
    } else {
      this.selectedCodes.set(current.filter((c) => c !== code));
    }
  }

  protected applyPanel(panel: CatalogPanel): void {
    const current = new Set(this.selectedCodes());
    for (const code of panel.testCodes) current.add(code);
    this.selectedCodes.set(Array.from(current));
  }
}
