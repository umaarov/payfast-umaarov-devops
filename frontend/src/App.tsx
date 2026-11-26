import { Route, Routes } from 'react-router-dom';
import { PublicLayout, RequireAuth } from './components/Layouts';
import { CouriersPage } from './pages/CouriersPage';
import { DashboardPage } from './pages/DashboardPage';
import { DeliveriesPage } from './pages/DeliveriesPage';
import { LoginPage } from './pages/LoginPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
import { OrderNewPage } from './pages/OrderNewPage';
import { OrdersPage } from './pages/OrdersPage';
import { HomePage, NotFoundPage, TrackPage } from './pages/PublicPages';
import { WarehousePage } from './pages/WarehousePage';

export function App() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="track/:trackingNumber" element={<TrackPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      <Route path="console" element={<RequireAuth />}>
        <Route index element={<DashboardPage />} />
        <Route path="orders" element={<OrdersPage />} />
        <Route path="orders/new" element={<OrderNewPage />} />
        <Route path="orders/:id" element={<OrderDetailPage />} />
        <Route path="deliveries" element={<DeliveriesPage />} />
        <Route path="couriers" element={<CouriersPage />} />
        <Route path="warehouse" element={<WarehousePage />} />
      </Route>
    </Routes>
  );
}
