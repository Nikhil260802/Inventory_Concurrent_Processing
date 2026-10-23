# Sample Data: Inputs and Outputs

This directory contains standalone sample inputs and expected output responses for quick verification and evaluation of the **Inventory Concurrent Processing** backend.

---

## 1. Directory Structure

```
sample_data/
├── inputs/
│   ├── sample_valid_5.csv               # 5 valid inventory records for quick testing
│   └── sample_mixed_with_errors.csv     # 10 records with intentional validation errors & duplicates
└── outputs/
    ├── upload_parallel_response.json    # HTTP 201 response after CSV upload (PARALLEL mode)
    ├── job_status_response.json         # HTTP 200 response for GET /api/v1/jobs/{id}
    ├── job_errors_response.json         # HTTP 200 paginated errors for GET /api/v1/jobs/{id}/errors
    └── inventory_search_response.json   # HTTP 200 paginated search results for GET /api/v1/inventory
```

---

## 2. Sample Inputs

### A. `sample_valid_5.csv`
A clean CSV dataset of 5 valid records conforming to the canonical schema:
`code,name,batch_number,quantity,price,expiry_date`

**Usage:**
```bash
curl -X POST "http://localhost:8080/api/v1/jobs/upload?mode=PARALLEL" \
  -F "file=@sample_data/inputs/sample_valid_5.csv"
```
**Expected Outcome:** `totalRecords: 5`, `successfulRecords: 5`, `failedRecords: 0`, `duplicateRecords: 0`.

---

### B. `sample_mixed_with_errors.csv`
A 10-row dataset containing deliberate error cases to test fail-all field validation and duplicate detection:

| Line # | Record Content | Deliberate Test Case | Expected Result |
| :---: | :--- | :--- | :--- |
| **Line 2** | `MED-00001,Paracetamol 500mg,BATCH-2001,100,12.50,2028-06-30` | Valid row | Success |
| **Line 3** | `MED-00002,,BATCH-2002,50,25.00,2027-11-15` | Missing `name` field | Validation Failure |
| **Line 4** | `MED-00003,Ibuprofen 400mg,BATCH-2003,-15,18.00,2028-01-20` | Negative quantity (`-15`) | Validation Failure |
| **Line 5** | `MED-00004,Aspirin 100mg,BATCH-2004,200,-5.50,2028-09-10` | Negative price (`-5.50`) | Validation Failure |
| **Line 6** | `MED-00005,Cetirizine 10mg,BATCH-2005,150,15.999,2029-03-31` | Price with 3 decimal places | Validation Failure |
| **Line 7** | `MED-00006,Metformin 500mg,BATCH-2006,300,8.75,invalid-date-format` | Unparseable date format | Validation Failure |
| **Line 8** | `MED-00007,Omeprazole 20mg,BATCH-2007,400,22.40,2028-04-15` | Valid row | Success |
| **Line 9** | `MED-00001,Paracetamol 500mg,BATCH-2001,120,12.50,2028-06-30` | In-file duplicate key `(MED-00001, BATCH-2001)` | Duplicate Detected |
| **Line 10** | `,Atorvastatin 10mg,BATCH-2009,180,30.00,2027-12-31` | Missing `code` field | Validation Failure |
| **Line 11** | `MED-00010,Pantoprazole 40mg,BATCH-2010,220,19.80,2028-10-05` | Valid row | Success |

**Usage:**
```bash
curl -X POST "http://localhost:8080/api/v1/jobs/upload?mode=PARALLEL" \
  -F "file=@sample_data/inputs/sample_mixed_with_errors.csv"
```
**Expected Outcome:** `totalRecords: 10`, `successfulRecords: 3`, `failedRecords: 6`, `duplicateRecords: 1`.

---

## 3. Sample Outputs

The JSON files in `sample_data/outputs/` illustrate the exact JSON schemas returned by the API endpoints:

1. **[`upload_parallel_response.json`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/sample_data/outputs/upload_parallel_response.json)**: Contains the job execution metrics (throughput, total/valid/failed/duplicate counts, peak memory).
2. **[`job_status_response.json`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/sample_data/outputs/job_status_response.json)**: Query status for any job ID.
3. **[`job_errors_response.json`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/sample_data/outputs/job_errors_response.json)**: Paginated errors showing exact 1-based CSV row numbers and specific violation reasons.
4. **[`inventory_search_response.json`](file:///d:/Vector%20Labs/Inventory_Concurrent_Processing/sample_data/outputs/inventory_search_response.json)**: Paginated search results across inventory.
