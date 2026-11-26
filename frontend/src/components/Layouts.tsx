import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { clearApiKey } from '../auth/keyStore';
import { useApiKey } from '../auth/useApiKey';

function Brand() {
  return (
    <Link to="/" className="brand">
      <svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
        <rect width="32" height="32" rx="7" fill="currentColor" />
        <path
          d="M7 11l9-4 9 4v10l-9 4-9-4zM7 11l9 4 9-4M16 15v10"
          fill="none"
          stroke="#fff"
          strokeWidth="2"
          strokeLinejoin="round"
        />
      </svg>
      <span>Logistics</span>
    </Link>
  );
}

export function PublicLayout() {
  const apiKey = useApiKey();
  return (
    <div className="public-shell">
      <header className="public-header">
        <Brand />
        <nav>
          <Link to={apiKey ? '/console' : '/login'} className="btn btn-ghost">
            Staff console
          </Link>
        </nav>
      </header>
      <main className="public-main">
        <Outlet />
      </main>
      <footer className="public-footer">Parcel tracking needs no account. Staff sign in with an API key.</footer>
    </div>
  );
}

const NAV_ITEMS = [
  { to: '/console', label: 'Dashboard', end: true },
  { to: '/console/orders', label: 'Orders', end: false },
  { to: '/console/deliveries', label: 'Deliveries', end: false },
  { to: '/console/couriers', label: 'Couriers', end: false },
  { to: '/console/warehouse', label: 'Warehouse', end: false },
];

export function RequireAuth() {
  const apiKey = useApiKey();
  const location = useLocation();
  if (!apiKey) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <ConsoleLayout />;
}

function ConsoleLayout() {
  const navigate = useNavigate();

  function signOut() {
    clearApiKey();
    navigate('/login', { replace: true });
  }

  return (
    <div className="console-shell">
      <aside className="sidebar">
        <Brand />
        <nav aria-label="Console">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className="nav-link">
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <Link to="/" className="nav-link">
            Public tracking
          </Link>
          <button type="button" className="btn btn-ghost" onClick={signOut}>
            Sign out
          </button>
        </div>
      </aside>
      <main className="console-main">
        <Outlet />
      </main>
    </div>
  );
}
