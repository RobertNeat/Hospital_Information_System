import { toSignal } from '@angular/core/rxjs-interop';
import { map, type Observable, startWith } from 'rxjs';

/** Signal of a form group's raw value that re-emits on every change. */
export function rawValueSignal<T>(group: { valueChanges: Observable<unknown>; getRawValue(): T }) {
  return toSignal(
    group.valueChanges.pipe(
      startWith(null),
      map(() => group.getRawValue()),
    ),
    { initialValue: group.getRawValue() },
  );
}
