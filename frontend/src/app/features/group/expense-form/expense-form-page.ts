import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  LOCALE_ID,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { startWith } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';
import { FluidModule } from 'primeng/fluid';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';

import { AuthService } from '../../../core/auth/auth.service';
import { GroupsApi } from '../../../core/groups/groups.api';
import { Expense, SplitType } from '../../../core/models';
import { formatMoney, minorUnitDigits } from '../../../core/money';
import { problemMessage } from '../../../core/problem';
import { GroupStore } from '../group-store';
import {
  allocated,
  buildRequest,
  evenAmounts,
  evenPercents,
  FULL_BASIS_POINTS,
  includedOf,
  MAX_AMOUNT_MINOR,
  ParticipantInput,
  parseDate,
  percentsFromShares,
  splitProblem,
  toMajor,
  toMinor,
} from './expense-form.logic';

interface SplitValue {
  splitType: SplitType;
  amount: number | null;
  participants: ParticipantInput[];
}

interface Summary {
  text: string;
  ok: boolean;
}

@Component({
  selector: 'app-expense-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    CheckboxModule,
    DatePickerModule,
    FluidModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    SelectButtonModule,
    SkeletonModule,
  ],
  templateUrl: './expense-form-page.html',
  styleUrl: './expense-form-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpenseFormPage {
  private readonly api = inject(GroupsApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(GroupStore);
  protected readonly locale = inject(LOCALE_ID);

  private readonly group = (() => {
    const group = this.store.group();
    if (!group) {
      throw new Error('The expense form needs a loaded group');
    }
    return group;
  })();

  protected readonly currency = this.group.currency;
  protected readonly digits = minorUnitDigits(this.group.currency);
  protected readonly members = this.store.members();
  protected readonly splitOptions: { label: string; value: SplitType }[] = [
    { label: 'Equal', value: 'EQUAL' },
    { label: 'Percentage', value: 'PERCENTAGE' },
    { label: 'Exact', value: 'EXACT' },
  ];

  readonly expenseId = input<string>();

  protected readonly editing = computed(() => this.expenseId() !== undefined);
  protected readonly existing = signal<Expense | null>(null);
  protected readonly loadingExisting = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly stale = signal(false);

  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly splitValidator: ValidatorFn = (control) => {
    const value = control.value as SplitValue;
    const total = value.amount === null ? null : toMinor(value.amount, this.digits);
    const problem = splitProblem(value.splitType, value.participants, total, this.digits);
    return problem ? { split: problem } : null;
  };

  protected readonly form = new FormGroup(
    {
      description: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.maxLength(200)],
      }),
      amount: new FormControl<number | null>(null, [
        Validators.required,
        Validators.min(1 / 10 ** this.digits),
        Validators.max(MAX_AMOUNT_MINOR / 10 ** this.digits),
      ]),
      expenseDate: new FormControl<Date | null>(new Date(), [Validators.required]),
      paidBy: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      splitType: new FormControl<SplitType>('EQUAL', { nonNullable: true }),
      participants: new FormArray(
        this.members.map(
          (member) =>
            new FormGroup({
              userId: new FormControl(member.userId, { nonNullable: true }),
              included: new FormControl(true, { nonNullable: true }),
              percent: new FormControl<number | null>(null),
              amount: new FormControl<number | null>(null),
            }),
        ),
      ),
    },
    { validators: [this.splitValidator] },
  );

  protected readonly participants = this.form.controls.participants;

  private readonly value = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { requireSync: true },
  );

  protected readonly splitType = computed(() => this.value().splitType ?? 'EQUAL');

  protected readonly summary = computed<Summary>(() => {
    const value = this.value() as SplitValue;
    const people = value.participants ?? [];
    const type = value.splitType;
    if (type === 'PERCENTAGE') {
      const basisPoints = allocated(type, people, this.digits);
      return {
        text: `Total: ${basisPoints / 100}% of 100%`,
        ok: basisPoints === FULL_BASIS_POINTS,
      };
    }
    if (type === 'EXACT') {
      if (value.amount === null || value.amount === undefined) {
        return { text: 'Enter the total amount first.', ok: false };
      }
      const remaining = toMinor(value.amount, this.digits) - allocated(type, people, this.digits);
      return {
        text: `Remaining: ${formatMoney(remaining, this.currency, this.locale)}`,
        ok: remaining === 0,
      };
    }
    const count = includedOf(people).length;
    return {
      text:
        count === 0
          ? 'Nobody selected.'
          : `Split equally between ${count} ${count === 1 ? 'person' : 'people'}.`,
      ok: count > 0,
    };
  });

  constructor() {
    effect(() => {
      const id = this.expenseId();
      if (id) {
        untracked(() => this.loadExisting(id));
      }
    });

    effect(() => {
      const me = this.auth.user();
      untracked(() => {
        const paidBy = this.form.controls.paidBy;
        if (!paidBy.value && me && this.members.some((member) => member.userId === me.id)) {
          paidBy.setValue(me.id);
        }
      });
    });

    this.form.controls.splitType.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((type) => {
        if (type !== 'EQUAL') {
          this.splitEvenly();
        }
      });
  }

  protected reload(): void {
    const id = this.expenseId();
    if (id) {
      this.loadExisting(id);
    }
  }

  private loadExisting(expenseId: string): void {
    this.loadingExisting.set(true);
    this.loadError.set(null);
    this.stale.set(false);
    this.error.set(null);
    this.api
      .expense(this.store.groupId(), expenseId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (expense) => {
          this.existing.set(expense);
          this.populate(expense);
          this.loadingExisting.set(false);
        },
        error: (err: unknown) => {
          this.loadError.set(problemMessage(err));
          this.loadingExisting.set(false);
        },
      });
  }

  private populate(expense: Expense): void {
    const shareByUser = new Map(expense.splits.map((split) => [split.userId, split.amount]));
    const percentByUser = new Map<string, number>();
    if (expense.splitType === 'PERCENTAGE') {
      const percents = percentsFromShares(
        expense.splits.map((split) => split.amount),
        expense.amount,
      );
      expense.splits.forEach((split, index) => percentByUser.set(split.userId, percents[index]));
    }

    this.form.controls.splitType.setValue(expense.splitType, { emitEvent: false });
    this.participants.controls.forEach((row) => {
      const userId = row.controls.userId.value;
      const share = shareByUser.get(userId);
      row.patchValue({
        included: share !== undefined,
        percent: percentByUser.get(userId) ?? null,
        amount: share === undefined ? null : toMajor(share, this.digits),
      });
    });
    this.form.patchValue({
      description: expense.description,
      amount: toMajor(expense.amount, this.digits),
      expenseDate: parseDate(expense.expenseDate),
      paidBy: expense.paidBy,
    });
    this.form.markAsPristine();
  }

  protected splitEvenly(): void {
    const rows = this.participants.controls.filter((row) => row.controls.included.value);
    const type = this.form.controls.splitType.value;
    if (type === 'PERCENTAGE') {
      evenPercents(rows.length).forEach((percent, index) =>
        rows[index].controls.percent.setValue(percent),
      );
    } else if (type === 'EXACT') {
      const amount = this.form.controls.amount.value;
      if (amount === null) {
        return;
      }
      evenAmounts(toMinor(amount, this.digits), rows.length, this.digits).forEach((share, index) =>
        rows[index].controls.amount.setValue(share),
      );
    }
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.saving() || (this.editing() && !this.existing())) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request = buildRequest(
      {
        description: raw.description,
        amount: raw.amount ?? 0,
        currency: this.currency,
        paidBy: raw.paidBy,
        splitType: raw.splitType,
        date: raw.expenseDate,
        participants: raw.participants,
      },
      this.digits,
    );
    this.saving.set(true);
    this.error.set(null);
    this.stale.set(false);
    const groupId = this.store.groupId();
    const existing = this.existing();
    const save$ = existing
      ? this.api.updateExpense(groupId, existing.id, existing.version, request)
      : this.api.createExpense(groupId, request);
    save$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => void this.router.navigate(['/groups', groupId, 'expenses']),
      error: (err: unknown) => {
        this.stale.set(err instanceof HttpErrorResponse && err.status === 412);
        this.error.set(problemMessage(err));
        this.saving.set(false);
      },
    });
  }
}
