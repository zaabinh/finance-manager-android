# ocr

On-device receipt / bank screenshot import. Images never leave the device; only the confirmed transaction is sent to the backend.

```
Image → capture → recognition → extraction → prediction → review → Transaction API
```

| Package | Layer | Purpose |
|---------|-------|---------|
| `capture/` | — | Take a photo with CameraX or pick one from the gallery |
| `recognition/` | 1. OCR | ML Kit Text Recognition v2 → raw text. Recognizes text only. |
| `extraction/` | 2. Information Extraction | Parse merchant, total amount, date, line items from raw text |
| `prediction/` | 3. Category Prediction | Suggest an expense category + confidence (rule-based / merchant dictionary first, ML later) |
| `review/` | — | Review & edit screen. The user **must confirm** before a transaction is created. |

If prediction confidence is low, the user selects the category manually.
