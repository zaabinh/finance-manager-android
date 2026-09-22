# Web Admin

React dashboard for system administrators.

Admins manage system-level data only. They must **not** see users' transactions, wallet balances or financial history.

| Path | Purpose |
|------|---------|
| `src/api/` | Axios instance, interceptors, endpoint modules |
| `src/auth/` | Admin login, token handling, protected routes |
| `src/components/` | Reusable UI components |
| `src/layouts/` | Admin shell (sidebar, header) |
| `src/pages/dashboard/` | Counts of users, transactions, wallets, categories; OCR usage |
| `src/pages/users/` | User list, search, detail, disable |
| `src/pages/categories/` | Default category management |
| `src/pages/notifications/` | Create, send, history of system notifications |
| `src/hooks/`, `src/routes/`, `src/utils/` | Custom hooks, route config, helpers |

## Setup

```bash
npm install
npm run dev
```
