import type { TagKind } from '../constants/tag-severity';

export interface TableColumn<T> {
  field: (keyof T & string) | string;
  header: string;
  type?: 'text' | 'date' | 'datetime' | 'number' | 'tag' | 'custom';
  tagKind?: TagKind;
  sortable?: boolean;
  width?: string;
}

export type { TagKind };
