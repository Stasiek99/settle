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
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { startWith } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { FluidModule } from 'primeng/fluid';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';

import { AuthService } from '../../../core/auth/auth.service';
import { GroupsApi } from '../../../core/groups/groups.api';
import { CURRENCIES, formatMoney, minorUnitDigits } from '../../../core/money';
import { problemMessage } from '../../../core/problem';
import { MAX_AMOUNT_MINOR, toMinor } from '../expense-form/expense-form.logic';
import { GroupStore } from '../group-store';
import { buildSettlementRequest, prefillFromParams } from './settlement-form.logic';

@Component({
  selector: 'app-settlement-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    DatePickerModule,
    FluidModule,
    InputNumberModule,
    MessageModule,
    SelectModule,
  ],
  templateUrl: './settlement-form-page.html',
  styleUrl: './settlement-form-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettlementFormPage {
  private readonly api = inject(GroupsApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(GroupStore);
  protected readonly locale = inject(LOCALE_ID);

  readonly from = input<string>();
  readonly to = input<string>();
  readonly amount = input<string>();
  readonly currencyParam = input<string | undefined>(undefined, { alias: 'currency' });

  private readonly group = (() => {
    const group = this.store.group();
    if (!group) {
      throw new Error('The settlement form needs a loaded group');
    }
    return group;
  })();

  protected readonly currencyOptions = [...new Set([this.group.currency, ...CURRENCIES])];
  protected readonly currency = signal(this.group.currency);
  protected readonly digits = computed(() => minorUnitDigits(this.currency()));
  protected readonly members = this.store.members();

  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly differentPeople: ValidatorFn = (control) => {
    const value = control.value as { paidBy: string; paidTo: string };
    return value.paidBy && value.paidBy === value.paidTo ? { samePerson: true } : null;
  };

  private readonly amountInRange: ValidatorFn = (control) => {
    const value = control.value as number | null;
    if (value === null || value === undefined) {
      return null;
    }
    const scale = 10 ** this.digits();
    if (value < 1 / scale) {
      return { min: true };
    }
    return value > MAX_AMOUNT_MINOR / scale ? { max: true } : null;
  };

  protected readonly form = new FormGroup(
    {
      paidBy: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      paidTo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      currency: new FormControl(this.group.currency, {
        nonNullable: true,
        validators: [Validators.required],
      }),
      amount: new FormControl<number | null>(null, [Validators.required, this.amountInRange]),
      settledOn: new FormControl<Date | null>(new Date(), [Validators.required]),
    },
    { validators: [this.differentPeople] },
  );

  private readonly value = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { requireSync: true },
  );

  protected readonly preview = computed(() => {
    const { paidBy, paidTo, amount } = this.value();
    if (!paidBy || !paidTo || paidBy === paidTo || !amount || amount <= 0) {
      return null;
    }
    const money = formatMoney(toMinor(amount, this.digits()), this.currency(), this.locale);
    return `${this.store.nameOf(paidBy)} pays ${this.store.nameOf(paidTo)} ${money}`;
  });

  constructor() {
    this.form.controls.currency.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((code) => {
        this.currency.set(code);
        this.form.controls.amount.updateValueAndValidity();
      });

    effect(() => {
      const requested = this.currencyParam()?.toUpperCase();
      const currency =
        requested !== undefined && this.currencyOptions.includes(requested)
          ? requested
          : this.group.currency;
      const prefill = prefillFromParams(
        { from: this.from(), to: this.to(), amount: this.amount() },
        this.members.map((member) => member.userId),
        minorUnitDigits(currency),
      );
      untracked(() => this.form.patchValue({ ...prefill, currency }));
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
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request = buildSettlementRequest(
      {
        paidBy: raw.paidBy,
        paidTo: raw.paidTo,
        amount: raw.amount ?? 0,
        currency: raw.currency,
        date: raw.settledOn,
      },
      this.digits(),
    );
    this.saving.set(true);
    this.error.set(null);
    const groupId = this.store.groupId();
    this.api
      .createSettlement(groupId, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => void this.router.navigate(['/groups', groupId, 'settlements']),
        error: (err: unknown) => {
          this.error.set(problemMessage(err));
          this.saving.set(false);
        },
      });
  }
}
