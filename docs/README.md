# Documentation

Financial-domain terminology is **Wallet / Ví**. Start with [the API contract](api/CONTRACT.md),
[Product Context](product/PRODUCT_CONTEXT_VI.md), and [UI terminology](design/TERMINOLOGY.md).
Login identities remain User/Auth. Flyway migrations in `backend/src/main/resources/db/migration/`
are the only deployable database schema source.

Current finance scope and API decisions: [Finance CRUD contract](api/FINANCE_CRUD_CONTRACT.md).
Implementation plans: [Wallet CRUD](development/wallet-crud-plan.md),
[Category CRUD](development/category-crud-plan.md), [Budget CRUD](development/budget-crud-plan.md),
[Transaction](development/transaction-implementation-plan.md). Older development specifications
are historical references; conflicting finance details are superseded by this contract.

| Folder | Contents |
|--------|----------|
| `api/` | API contract, OpenAPI export, Postman collection |
| `database/` | ERD, schema notes |
| `architecture/` | Diagrams, design decisions |
| `ocr/` | OCR pipeline, extraction rules, classifier notes |
| `ui/` | Wireframes, screen flows |
| `design/` | Wallet/ví terminology, design audits and handoff specifications; screenshots remain ignored |
| `product/` | Product Context and domain decisions |
| `development/` | Local environment setup and development/staging/production distinctions |
| `deployment/` | Manual staging deployment and integration verification; no production CD |
