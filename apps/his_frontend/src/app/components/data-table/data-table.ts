import { DatePipe, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  contentChild,
  input,
  output,
  TemplateRef,
} from '@angular/core';
import { Table, TableModule, type TableRowSelectEvent } from 'primeng/table';
import { IconField } from 'primeng/iconfield';
import { InputText } from 'primeng/inputtext';
import type { TableColumn } from '../../models';
import { StatusTag } from '../status-tag/status-tag';

/**
 * Dense `p-table` wrapper. Custom cell rendering is provided by projecting a
 * `<ng-template #cell let-row let-col="col">` and/or `<ng-template #rowActions let-row>`;
 * a toolbar can be projected via `<ng-template #toolbar>`.
 */
@Component({
  selector: 'app-data-table',
  imports: [TableModule, IconField, InputText, DatePipe, NgTemplateOutlet, StatusTag],
  templateUrl: './data-table.html',
  styleUrl: './data-table.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataTable<T extends Record<string, unknown>> {
  readonly rows = input.required<T[]>();
  readonly columns = input.required<TableColumn<T>[]>();
  readonly loading = input(false);
  readonly globalFilterFields = input<string[]>();
  readonly paginator = input(true);
  readonly pageSize = input(10);
  readonly selectionMode = input<'single' | undefined>(undefined);
  readonly dataKey = input('id');
  readonly emptyMessage = input('Brak danych');

  readonly rowSelect = output<T>();

  readonly cellTemplate = contentChild<TemplateRef<unknown>>('cell');
  readonly rowActionsTemplate = contentChild<TemplateRef<unknown>>('rowActions');
  readonly toolbarTemplate = contentChild<TemplateRef<unknown>>('toolbar');

  protected cellValue(row: T, col: TableColumn<T>): unknown {
    return row[col.field as keyof T];
  }

  protected cellValueAsString(row: T, col: TableColumn<T>): string {
    return String(this.cellValue(row, col) ?? '');
  }

  protected onRowSelect(event: TableRowSelectEvent<T>): void {
    if (event.data && !Array.isArray(event.data)) {
      this.rowSelect.emit(event.data);
    }
  }

  protected applyGlobalFilter(table: Table, value: string): void {
    table.filterGlobal(value, 'contains');
  }
}
