import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { createProduct, listProducts, receiveStock } from '../api/endpoints';
import type { Product } from '../api/types';
import { EmptyState, Field, Loading, PageHeader, Pager } from '../components/Common';
import { ErrorNotice } from '../components/ErrorNotice';
import { isValidSku, parseWholeNumber } from '../lib/validation';

const PAGE_SIZE = 20;
const MAX_STOCK = 1_000_000;

function CreateProductForm() {
  const client = useQueryClient();
  const [sku, setSku] = useState('');
  const [name, setName] = useState('');
  const [quantity, setQuantity] = useState('0');
  const [errors, setErrors] = useState<Partial<Record<'sku' | 'name' | 'quantity', string>>>({});

  const create = useMutation({
    mutationFn: createProduct,
    onSuccess: async () => {
      setSku('');
      setName('');
      setQuantity('0');
      await client.invalidateQueries({ queryKey: ['products'] });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    const found: typeof errors = {};
    const trimmedSku = sku.trim();
    const initialQuantity = parseWholeNumber(quantity);
    if (!isValidSku(trimmedSku)) {
      found.sku = 'Use letters, digits, dot, underscore or dash, up to 64 characters.';
    }
    if (!name.trim()) {
      found.name = 'Enter a product name.';
    }
    if (initialQuantity === null || initialQuantity > MAX_STOCK) {
      found.quantity = `Enter a whole number from 0 to ${MAX_STOCK}.`;
    }
    setErrors(found);
    if (Object.keys(found).length > 0 || initialQuantity === null) {
      return;
    }
    create.mutate({ sku: trimmedSku, name: name.trim(), initialQuantity });
  }

  return (
    <form className="card form" onSubmit={submit} noValidate aria-label="Add a product">
      <h2>Add a product</h2>
      <div className="form-grid">
        <Field label="SKU" error={errors.sku}>
          <input value={sku} onChange={(event) => setSku(event.target.value)} maxLength={64} />
        </Field>
        <Field label="Name" error={errors.name}>
          <input value={name} onChange={(event) => setName(event.target.value)} maxLength={255} />
        </Field>
        <Field label="Initial quantity" error={errors.quantity}>
          <input value={quantity} onChange={(event) => setQuantity(event.target.value)} inputMode="numeric" />
        </Field>
      </div>
      <ErrorNotice error={create.error} />
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={create.isPending}>
          {create.isPending ? 'Saving' : 'Add product'}
        </button>
      </div>
    </form>
  );
}

function ReceiveRow({ product }: { product: Product }) {
  const client = useQueryClient();
  const [quantity, setQuantity] = useState('');
  const [error, setError] = useState<string | null>(null);

  const receive = useMutation({
    mutationFn: (amount: number) => receiveStock(product.sku, amount),
    onSuccess: async () => {
      setQuantity('');
      await client.invalidateQueries({ queryKey: ['products'] });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    const amount = parseWholeNumber(quantity);
    if (amount === null || amount < 1 || amount > MAX_STOCK) {
      setError(`1 to ${MAX_STOCK}`);
      return;
    }
    setError(null);
    receive.mutate(amount);
  }

  return (
    <tr>
      <td className="mono">{product.sku}</td>
      <td>{product.name}</td>
      <td>{product.quantityOnHand}</td>
      <td>{product.quantityReserved}</td>
      <td>
        <strong>{product.available}</strong>
      </td>
      <td>
        <form className="inline-form" onSubmit={submit} noValidate>
          <input
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            inputMode="numeric"
            placeholder="Qty"
            aria-label={`Quantity to receive for ${product.sku}`}
            aria-invalid={error ? true : undefined}
          />
          <button type="submit" className="btn btn-ghost" disabled={receive.isPending}>
            Receive
          </button>
        </form>
        {error && (
          <span className="field-error" role="alert">
            {error}
          </span>
        )}
        {receive.error && <ErrorNotice error={receive.error} />}
      </td>
    </tr>
  );
}

export function WarehousePage() {
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState('');

  const products = useQuery({
    queryKey: ['products', { page }],
    queryFn: () => listProducts({ page, size: PAGE_SIZE }),
    refetchInterval: 30000,
    placeholderData: (previous) => previous,
  });

  const needle = filter.trim().toLowerCase();
  const visible = (products.data ?? []).filter(
    (product) => !needle || product.sku.toLowerCase().includes(needle) || product.name.toLowerCase().includes(needle),
  );

  return (
    <>
      <PageHeader title="Warehouse" subtitle="Stock on hand, reserved for orders, and available to sell" />
      <CreateProductForm />
      <div className="toolbar">
        <input
          type="search"
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
          placeholder="Filter this page by SKU or name"
          aria-label="Filter products"
        />
      </div>
      <ErrorNotice error={products.error} />
      {products.isPending && <Loading />}
      {products.data && visible.length === 0 && <EmptyState>No products to show.</EmptyState>}
      {visible.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>SKU</th>
                <th>Name</th>
                <th>On hand</th>
                <th>Reserved</th>
                <th>Available</th>
                <th>Receive stock</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((product) => (
                <ReceiveRow key={product.id} product={product} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager page={page} hasNext={(products.data?.length ?? 0) === PAGE_SIZE} onChange={setPage} />
    </>
  );
}
