import { describe, expect, it } from 'vitest';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DataTable } from './data-table';
import type { TableColumn } from '../../models';

interface Row {
  id: string;
  name: string;
}

@Component({
  imports: [DataTable],
  template: `
    <app-data-table [rows]="rows" [columns]="columns">
      <ng-template #rowExpansion let-row>Szczegóły: {{ row.name }}</ng-template>
    </app-data-table>
  `,
})
class ExpandHost {
  readonly rows: Row[] = [{ id: '1', name: 'Jan' }];
  readonly columns: TableColumn<Row>[] = [{ field: 'name', header: 'Nazwa' }];
}

describe('DataTable', () => {
  it('renders rows using the provided columns', async () => {
    const fixture = TestBed.createComponent(DataTable<Row>);
    const columns: TableColumn<Row>[] = [{ field: 'name', header: 'Nazwa' }];
    fixture.componentRef.setInput('rows', [{ id: '1', name: 'Jan Kowalski' }]);
    fixture.componentRef.setInput('columns', columns);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Jan Kowalski');
    expect(el.textContent).toContain('Nazwa');
  });

  it('shows the empty message when there are no rows', async () => {
    const fixture = TestBed.createComponent(DataTable<Row>);
    fixture.componentRef.setInput('rows', []);
    fixture.componentRef.setInput('columns', [
      { field: 'name', header: 'Nazwa' },
    ] as TableColumn<Row>[]);
    fixture.componentRef.setInput('emptyMessage', 'Brak danych');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Brak danych');
  });

  it('renders an expand toggle and the expansion template when #rowExpansion is projected', async () => {
    const fixture = TestBed.createComponent(ExpandHost);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).not.toContain('Szczegóły: Jan');
    el.querySelector<HTMLButtonElement>('.his-data-table__toggle')?.click();
    await fixture.whenStable();
    expect(el.textContent).toContain('Szczegóły: Jan');
  });
});
