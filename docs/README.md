# Documentation

Financial-domain terminology is **Wallet / Ví**. Start with [the API contract](api/CONTRACT.md),
[Product Context](product/PRODUCT_CONTEXT_VI.md), and [UI terminology](design/TERMINOLOGY.md).
Login identities remain User/Auth. Flyway migrations in `backend/src/main/resources/db/migration/`
are the only deployable database schema source.

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
