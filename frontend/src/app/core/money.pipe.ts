import { Pipe, PipeTransform } from '@angular/core';

const formatters = new Map<string, Intl.NumberFormat>();

/** Formats an amount with its currency code, always with two decimals: 1,234.50 EUR. */
export function formatMoney(amount: number, currency: string, signed = false): string {
  let formatter = formatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    formatters.set(currency, formatter);
  }
  const text = `${formatter.format(Math.abs(amount))} ${currency}`;
  if (!signed || amount === 0) return amount < 0 ? `−${text}` : text;
  return amount < 0 ? `−${text}` : `+${text}`;
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency: string, signed = false): string {
    return amount === null || amount === undefined ? '' : formatMoney(amount, currency, signed);
  }
}
