import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { listProducts, placeOrder } from '../api/endpoints';
import type { PlaceOrderPayload } from '../api/types';
import { Field, Loading, PageHeader } from '../components/Common';
import { ErrorNotice } from '../components/ErrorNotice';
import { newIdempotencyKey } from '../lib/format';
import { isValidPhone, parseWholeNumber } from '../lib/validation';

interface Line {
  id: number;
  sku: string;
  quantity: string;
}

type FormErrors = Partial<Record<'customerName' | 'customerPhone' | 'city' | 'addressLine' | 'items', string>>;

const MAX_LINES = 50;
const MAX_QUANTITY = 1000;

export function OrderNewPage() {
  const navigate = useNavigate();
  const client = useQueryClient();
  const products = useQuery({ queryKey: ['products', 'all'], queryFn: () => listProducts({ size: 200 }) });

  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [city, setCity] = useState('');
  const [addressLine, setAddressLine] = useState('');
  const [lines, setLines] = useState<Line[]>([{ id: 1, sku: '', quantity: '1' }]);
  const [errors, setErrors] = useState<FormErrors>({});
  const nextId = useRef(2);
  const attempt = useRef<{ signature: string; key: string } | null>(null);

  const submit = useMutation({
    mutationFn: ({ payload, key }: { payload: PlaceOrderPayload; key: string }) => placeOrder(payload, key),
    onSuccess: async (order) => {
      await client.invalidateQueries({ queryKey: ['orders'] });
      await client.invalidateQueries({ queryKey: ['products'] });
      navigate(`/console/orders/${order.id}`);
    },
  });

  function updateLine(id: number, patch: Partial<Line>) {
    setLines((current) => current.map((line) => (line.id === id ? { ...line, ...patch } : line)));
  }

  function addLine() {
    setLines((current) => [...current, { id: nextId.current++, sku: '', quantity: '1' }]);
  }

  function removeLine(id: number) {
    setLines((current) => (current.length > 1 ? current.filter((line) => line.id !== id) : current));
  }

  function validate(): PlaceOrderPayload | null {
    const found: FormErrors = {};
    if (!customerName.trim()) {
      found.customerName = 'Enter the customer name.';
    }
    if (!isValidPhone(customerPhone.trim())) {
      found.customerPhone = 'Use 9 to 15 digits with an optional leading +.';
    }
    if (!city.trim()) {
      found.city = 'Enter the city.';
    }
    if (!addressLine.trim()) {
      found.addressLine = 'Enter the street address.';
    }

    const items: PlaceOrderPayload['items'] = [];
    let itemsError: string | undefined;
    for (const line of lines) {
      const quantity = parseWholeNumber(line.quantity);
      if (!line.sku) {
        itemsError = 'Choose a product for every line.';
      } else if (quantity === null || quantity < 1 || quantity > MAX_QUANTITY) {
        itemsError = `Quantity must be a whole number from 1 to ${MAX_QUANTITY}.`;
      } else {
        items.push({ sku: line.sku, quantity });
      }
    }
    if (itemsError) {
      found.items = itemsError;
    }

    setErrors(found);
    if (Object.keys(found).length > 0) {
      return null;
    }
    return {
      customerName: customerName.trim(),
      customerPhone: customerPhone.trim(),
      city: city.trim(),
      addressLine: addressLine.trim(),
      items,
    };
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const payload = validate();
    if (!payload) {
      return;
    }
    const signature = JSON.stringify(payload);
    if (attempt.current?.signature !== signature) {
      attempt.current = { signature, key: newIdempotencyKey() };
    }
    submit.mutate({ payload, key: attempt.current.key });
  }

  return (
    <>
      <PageHeader title="New order" subtitle="Stock is reserved and a courier is assigned when the order is placed" />
      <form className="card form" onSubmit={onSubmit} noValidate>
        <div className="form-grid">
          <Field label="Customer name" error={errors.customerName}>
            <input value={customerName} onChange={(event) => setCustomerName(event.target.value)} maxLength={120} />
          </Field>
          <Field label="Customer phone" error={errors.customerPhone}>
            <input
              value={customerPhone}
              onChange={(event) => setCustomerPhone(event.target.value)}
              placeholder="+998901234567"
              inputMode="tel"
            />
          </Field>
          <Field label="City" error={errors.city}>
            <input value={city} onChange={(event) => setCity(event.target.value)} maxLength={80} />
          </Field>
          <Field label="Address" error={errors.addressLine}>
            <input value={addressLine} onChange={(event) => setAddressLine(event.target.value)} maxLength={255} />
          </Field>
        </div>

        <fieldset className="lines">
          <legend>Items</legend>
          {products.isPending && <Loading label="Loading products" />}
          <ErrorNotice error={products.error} />
          {products.data && products.data.length === 0 && (
            <p className="muted">
              There are no products yet. <Link to="/console/warehouse">Add a product in the warehouse</Link> first.
            </p>
          )}
          {lines.map((line, index) => (
            <div className="line" key={line.id}>
              <label>
                <span className="sr-only">Product for line {index + 1}</span>
                <select
                  value={line.sku}
                  onChange={(event) => updateLine(line.id, { sku: event.target.value })}
                  aria-label={`Product for line ${index + 1}`}
                >
                  <option value="">Choose a product</option>
                  {products.data?.map((product) => (
                    <option key={product.id} value={product.sku}>
                      {product.sku} - {product.name} ({product.available} available)
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span className="sr-only">Quantity for line {index + 1}</span>
                <input
                  value={line.quantity}
                  onChange={(event) => updateLine(line.id, { quantity: event.target.value })}
                  inputMode="numeric"
                  aria-label={`Quantity for line ${index + 1}`}
                />
              </label>
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => removeLine(line.id)}
                disabled={lines.length === 1}
                aria-label={`Remove line ${index + 1}`}
              >
                Remove
              </button>
            </div>
          ))}
          {errors.items && (
            <p className="field-error" role="alert">
              {errors.items}
            </p>
          )}
          <button type="button" className="btn btn-ghost" onClick={addLine} disabled={lines.length >= MAX_LINES}>
            Add line
          </button>
        </fieldset>

        <ErrorNotice error={submit.error} />
        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={submit.isPending}>
            {submit.isPending ? 'Placing order' : 'Place order'}
          </button>
          <Link to="/console/orders" className="btn btn-ghost">
            Cancel
          </Link>
        </div>
      </form>
    </>
  );
}
