# REST API Documentation & Collection

## Base URL
`http://localhost:8080`

---

## 1. CSV Processing & Job APIs

### A. Upload CSV File
* **Endpoint:** `POST /api/v1/jobs/upload` (or alias `POST /api/v1/inventory/upload`)
* **Content-Type:** `multipart/form-data`
* **Query Parameters:**
  * `mode` (optional, default: `PARALLEL`): `PARALLEL` or `SEQUENTIAL`
* **Form Body:**
  * `file`: The `.csv` file containing inventory records
* **Sample `curl`:**
```bash
curl -X POST "http://localhost:8080/api/v1/jobs/upload?mode=PARALLEL" \
  -F "file=@inventory_10000.csv"
```
* **Success Response (`201 Created`):**
```json
{
  "jobId": 1,
  "fileName": "inventory_10000.csv",
  "processingMode": "PARALLEL",
  "status": "COMPLETED",
  "startedAt": "2026-10-23T16:03:09.832",
  "finishedAt": "2026-10-23T16:03:10.525",
  "totalRecords": 10000,
  "successfulRecords": 7941,
  "failedRecords": 610,
  "duplicateRecords": 1449,
  "executionTimeMs": 681,
  "throughputRecordsPerSec": 14684.29,
  "peakMemoryMb": 39.10,
  "errorMessage": null
}
```

---

### B. Inspect Job Status
* **Endpoint:** `GET /api/v1/jobs/{jobId}`
* **Success Response (`200 OK`):**
```json
{
  "jobId": 1,
  "fileName": "inventory_10000.csv",
  "processingMode": "PARALLEL",
  "status": "COMPLETED",
  "startedAt": "2026-10-23T16:03:09.832",
  "finishedAt": "2026-10-23T16:03:10.525",
  "totalRecords": 10000,
  "successfulRecords": 7941,
  "failedRecords": 610,
  "duplicateRecords": 1449,
  "executionTimeMs": 681,
  "throughputRecordsPerSec": 14684.29,
  "peakMemoryMb": 39.10,
  "errorMessage": null
}
```

---

### C. Retrieve Job Error Details
* **Endpoint:** `GET /api/v1/jobs/{jobId}/errors?page=0&size=50&sort=rowNumber,asc`
* **Success Response (`200 OK`):**
```json
{
  "content": [
    {
      "id": 1,
      "jobId": 1,
      "rowNumber": 4,
      "errorType": "DUPLICATE",
      "rawRecord": "MED-00001,Paracetamol 500mg,BATCH-0001,100,12.50,2027-12-31",
      "errorMessage": "DUPLICATE: code='MED-00001', batch_number='BATCH-0001' appears more than once in the uploaded file",
      "createdAt": "2026-10-23T16:03:10.120"
    },
    {
      "id": 2,
      "jobId": 1,
      "rowNumber": 5,
      "errorType": "VALIDATION_ERROR",
      "rawRecord": "MED-00004,Aspirin 100mg,BATCH-0005,-10,5.00,2027-05-20",
      "errorMessage": "[quantity] quantity must be >= 0 but was -10 (got: '-10')",
      "createdAt": "2026-10-23T16:03:10.125"
    }
  ],
  "page": {
    "size": 50,
    "number": 0,
    "totalElements": 2059,
    "totalPages": 42
  }
}
```

---

## 2. Inventory Management & Search APIs

### A. Search & List Inventory
* **Endpoint:** `GET /api/v1/inventory`
* **Query Parameters:**
  * `query`: Free-text search matching `code`, `name`, or `batchNumber`
  * `code`: Filter by code (partial match, case-insensitive)
  * `name`: Filter by name (partial match, case-insensitive)
  * `batchNumber`: Filter by batch (partial match, case-insensitive)
  * `page` (default: 0), `size` (default: 20), `sort` (e.g. `price,desc` or `name,asc`)
* **Sample `curl`:**
```bash
curl "http://localhost:8080/api/v1/inventory?query=Paracetamol&page=0&size=10&sort=price,desc"
```

---

### B. Get Inventory Item by ID
* **Endpoint:** `GET /api/v1/inventory/{id}`
* **Success Response (`200 OK`):**
```json
{
  "id": 1,
  "code": "MED-00001",
  "name": "Paracetamol 500mg",
  "batchNumber": "BATCH-0001",
  "quantity": 100,
  "price": 12.50,
  "expiryDate": "2027-12-31",
  "sourceJobId": 1,
  "createdAt": "2026-10-23T16:03:10.050",
  "updatedAt": "2026-10-23T16:03:10.050"
}
```

---

### C. Create Inventory Item
* **Endpoint:** `POST /api/v1/inventory`
* **Content-Type:** `application/json`
* **Request Body:**
```json
{
  "code": "MED-00099",
  "name": "Ibuprofen 600mg",
  "batchNumber": "BATCH-9999",
  "quantity": 250,
  "price": 22.00,
  "expiryDate": "2028-06-30"
}
```
* **Success Response (`201 Created`):** Returns saved object.
* **Duplicate Business Key Error (`409 Conflict`):**
```json
{
  "timestamp": "2026-10-23T16:05:00",
  "status": 409,
  "error": "Conflict",
  "message": "Inventory item with code 'MED-00099' and batchNumber 'BATCH-9999' already exists",
  "path": "/api/v1/inventory"
}
```
* **Validation Error (`400 Bad Request`):**
```json
{
  "timestamp": "2026-10-23T16:05:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": [
    "quantity: quantity must be greater than or equal to 0",
    "price: price must be greater than 0"
  ]
}
```

---

### D. Update Inventory Item
* **Endpoint:** `PUT /api/v1/inventory/{id}`
* **Content-Type:** `application/json`
* **Success Response (`200 OK`):** Returns updated inventory item.

---

### E. Delete Inventory Item
* **Endpoint:** `DELETE /api/v1/inventory/{id}`
* **Success Response (`204 No Content`)**
