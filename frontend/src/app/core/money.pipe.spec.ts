import { MoneyPipe, formatMoney } from './money.pipe';

describe('formatMoney', () => {
  it('always shows two decimals and the currency code', () => {
    expect(formatMoney(1234.5, 'EUR')).toBe('1,234.50 EUR');
    expect(formatMoney(0.1, 'PLN')).toBe('0.10 PLN');
  });

  it('adds explicit signs when asked', () => {
    expect(formatMoney(45, 'EUR', true)).toBe('+45.00 EUR');
    expect(formatMoney(-120.5, 'EUR', true)).toBe('−120.50 EUR');
    expect(formatMoney(0, 'EUR', true)).toBe('0.00 EUR');
  });

  it('pipe returns an empty string for missing values', () => {
    expect(new MoneyPipe().transform(null, 'EUR')).toBe('');
  });
});
