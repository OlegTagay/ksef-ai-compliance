# ksef-ai-compliance

Polish KSeF (Krajowy System e-Faktur) invoice processing and compliance testing system with AI-powered invoice generation.

## Table of Contents
- [Python Invoice Generator](#python-invoice-generator)
  - [Scripts Overview](#scripts-overview)
  - [Configuration](#configuration)
  - [Usage Examples](#usage-examples)
- [Logging Infrastructure](#logging-infrastructure)
  - [Python Logging](#python-logging)
  - [Java Logging](#java-logging)
  - [Log Analysis](#log-analysis)
- [Project Structure](#project-structure)

## Python Invoice Generator

The Python scripts in `scripts/python/` provide a complete invoice generation pipeline that creates both PDF and KSeF-compliant XML invoices for testing purposes.

### Scripts Overview

**Core Scripts:**
1. `invoice_pdf_to_ksef_xml.py` - PDF invoice to KSeF XML parser (library + CLI)
2. `generate_invoices.py` - Test invoice generator
3. `pdf_generator.py` - PDF invoice creation library

#### 1. `invoice_pdf_to_ksef_xml.py` (Core + CLI)
Unified tool for converting invoices to Polish KSeF XML format. Can be used as both a Python library and command-line tool.

**Features:**
- Convert Python dictionaries to KSeF XML
- **Convert PDF invoices to KSeF XML using hybrid parsing**
- **Rule-based parsing** for standard formats (fast, no API costs)
- **Claude AI fallback** for complex/unusual invoice layouts
- **XSD Schema Validation** against official KSeF schemas
- Intelligent data extraction from Polish invoices
- Automatic NIP validation and formatting
- Built-in CLI interface
- Supports FA(2) and FA(3) invoice variants

**Official KSeF Schemas:**
- FA(2): [schemat_FA(2)_v1-0E.xsd](https://github.com/CIRFMF/ksef-docs/blob/main/faktury/schemy/FA/schemat_FA(2)_v1-0E.xsd)
- FA(3): [schemat_FA(3)_v1-0E.xsd](https://github.com/CIRFMF/ksef-docs/blob/main/faktury/schemy/FA/schemat_FA(3)_v1-0E.xsd)
- Documentation: [KSeF Official Docs](https://github.com/CIRFMF/ksef-docs)

**CLI Usage:**
```bash
cd scripts/python

# Basic conversion (tries rule-based first, AI fallback if needed)
python invoice_pdf_to_ksef_xml.py invoice.pdf

# With custom output path
python invoice_pdf_to_ksef_xml.py invoice.pdf -o output/invoice.xml

# Force Claude AI (skip rule-based parsing)
python invoice_pdf_to_ksef_xml.py invoice.pdf --force-ai

# Use custom API key
python invoice_pdf_to_ksef_xml.py invoice.pdf --api-key sk-ant-xxx
```

**Library Usage:**
```python
from invoice_pdf_to_ksef_xml import KSeFXMLConverter

converter = KSeFXMLConverter()

# From dictionary
xml = converter.convert_to_ksef_xml(invoice_data)

# From PDF (hybrid parsing)
xml = converter.convert_pdf_to_ksef_xml('invoice.pdf', 'output.xml')
```

#### 2. `generate_invoices.py`
Main script for generating random test invoices.

**Input:**
- `config.yaml` - Configuration file (seller info, buyer templates, products, VAT rates)
- Command-line arguments: `-n` (count), `-c` (config path)

**Output:**
- PDF invoices (Polish "faktura" format)
- JSON invoice data (optional)
- Saved to: `src/test/resources/invoice/input/pdf/pl/fake/generated/`

**Usage:**
```bash
cd scripts/python

# Generate 10 invoices
./venv/bin/python generate_invoices.py -n 10

# Generate 100 invoices
./venv/bin/python generate_invoices.py -n 100

# Use custom config
./venv/bin/python generate_invoices.py -c my_config.yaml -n 50
```

#### 3. `pdf_generator.py`
Generates professional Polish invoice PDFs with proper VAT calculations and formatting.

**Features:**
- Unicode support for Polish characters (ł, ą, ę, etc.)
- Proper VAT calculations and breakdowns
- Amount in words conversion (Polish language)
- Standard Polish invoice layout

**Output:** PDF files with seller/buyer details, line items table, VAT summary, and payment information.

### Configuration

Edit `config.yaml` to customize invoice generation:

```yaml
# Seller information (your company)
seller:
  name: "Przykladowa Spolka z o.o."
  tax_no: "1234563218"  # NIP
  street: "ul. Przykladowa 1"
  country: "PL"
  bank_account: "12345678901234567890123456"

# Buyer (leave empty for random generation)
buyer:
  name: ""  # Auto-generated if empty
  tax_no: ""

# Invoice settings
invoice:
  currency: "PLN"
  payment_days: 7
  tax_rate: 23  # VAT rate %

# Product pool for random selection
products:
  - name: "LAPTOP"
    price_net_min: 2000
    price_net_max: 5000
  - name: "MONITOR"
    price_net_min: 500
    price_net_max: 1500

# Generation settings
generation:
  min_positions: 1  # Items per invoice
  max_positions: 5
  output_format: "pdf"  # pdf, json, or xml
  output_dir: "../../src/test/resources/invoice/input/pdf/pl/fake/generated"
```

### Usage Examples

**Example 1: Generate test dataset**
```bash
cd scripts/python

# Create 100 test invoices for integration testing
./venv/bin/python generate_invoices.py -n 100
```

**Example 2: Convert existing PDF invoices to KSeF XML**
```bash
cd scripts/python

# Convert a single PDF (uses rule-based parsing, no API key needed for standard invoices)
python invoice_pdf_to_ksef_xml.py path/to/invoice.pdf

# For complex invoices (set API key for AI fallback)
export ANTHROPIC_API_KEY=sk-ant-xxxxx
python invoice_pdf_to_ksef_xml.py path/to/invoice.pdf --force-ai

# Batch convert multiple PDFs
for pdf in ../../src/test/resources/invoice/input/pdf/pl/real/faktura/*.pdf; do
  python invoice_pdf_to_ksef_xml.py "$pdf" -o "output/$(basename "$pdf" .pdf).xml"
done
```

**Example 3: Custom invoice generation in Python**
```python
from generate_invoices import InvoiceGenerator
from invoice_pdf_to_ksef_xml import KSeFXMLConverter

# Generate invoice data
gen = InvoiceGenerator('config.yaml')
invoice = gen.generate_invoice(invoice_number=1)

# Convert to KSeF XML
converter = KSeFXMLConverter()
xml_string = converter.convert_to_ksef_xml(invoice)

# Save for testing
converter.save_ksef_xml(invoice, 'test-invoice.xml')
```

## Logging Infrastructure

The project implements production-grade structured logging for both Python and Java components, enabling comprehensive monitoring, debugging, and audit trails.

### Python Logging

All Python scripts use structured JSON logging with correlation IDs for request tracing.

**Configuration** (`scripts/python/logging_config.py`):
- **Format**: JSON (default) or text
- **Log Rotation**: 10MB max file size, 5 backup files
- **Default Level**: INFO
- **Log Location**: `logs/` directory (auto-created)

**Features:**
- Structured JSON output for easy parsing by log aggregation tools
- Correlation IDs for tracing requests across multiple operations
- Automatic log rotation to prevent disk space issues
- Exception tracking with full stack traces
- Extra fields for filtering and analysis

**Log Output Example:**
```json
{
  "timestamp": "2025-12-18T17:30:45.123456Z",
  "level": "INFO",
  "logger": "generate_invoices",
  "message": "Invoice generated successfully",
  "module": "generate_invoices",
  "function": "save_invoices",
  "line": 322,
  "correlation_id": "a3d7f891-4c2e-4b5a-9f1e-8e7d6c5b4a3f",
  "filepath": "/path/to/invoice.pdf",
  "invoice_number": "1/12/2025",
  "event_type": "invoice_generated"
}
```

**Viewing Logs:**
```bash
cd scripts/python

# View all logs (JSON format)
tail -f logs/invoice_generator.log

# View formatted logs (requires jq)
tail -f logs/invoice_generator.log | jq '.'

# Filter by correlation ID
cat logs/invoice_generator.log | jq 'select(.correlation_id == "abc123")'

# Filter by event type
cat logs/invoice_generator.log | jq 'select(.event_type == "invoice_generated")'

# View errors only
cat logs/invoice_generator.log | jq 'select(.level == "ERROR")'
```

**Correlation IDs:**
Each batch operation generates a unique correlation ID that tracks all related operations:
```python
# Automatically set in main() - all logs in this execution will share the same correlation ID
correlation_id = set_correlation_id()

# Use the same correlation ID to trace: PDF generation → KSeF XML conversion → validation
```

**Log Files by Script:**
- `logs/invoice_generator.log` - Invoice generation batches
- `logs/ksef_invoice.log` - PDF to KSeF XML conversion
- `logs/*.log.1`, `*.log.2`, etc. - Rotated log backups

### Java Logging

Java application uses Logback with JSON encoding for structured logging.

**Configuration** (`src/main/resources/logback.xml`):
- **Format**: JSON (LogstashEncoder)
- **Appenders**: Console + File + Error File
- **Log Rotation**: 10MB max size, daily rotation, 30 days retention
- **Log Location**: `logs/ksef-application.log`

**Features:**
- JSON-formatted logs compatible with ELK stack, Splunk, CloudWatch
- Separate error log file for faster error analysis
- Async appenders for better performance
- MDC (Mapped Diagnostic Context) support for correlation IDs
- Configurable log levels per package

**Log Output Example:**
```json
{
  "@timestamp": "2025-12-18T17:30:45.123Z",
  "level": "INFO",
  "logger_name": "com.bsg6.service.invoice.InvoiceService",
  "message": "Invoice sent to KSeF successfully",
  "thread_name": "http-nio-8080-exec-1",
  "application": "ksef-ai-compliance",
  "environment": "dev",
  "invoice_number": "1/12/2025",
  "reference_number": "12345678-ABCD-1234-5678-1234567890AB"
}
```

**Viewing Java Logs:**
```bash
# View application logs
tail -f logs/ksef-application.log

# View errors only
tail -f logs/ksef-application-error.log

# Formatted output (requires jq)
tail -f logs/ksef-application.log | jq '.'

# View logs for specific invoice
cat logs/ksef-application.log | jq 'select(.invoice_number == "1/12/2025")'
```

**Log Levels Configuration:**
Edit `src/main/resources/logback.xml` to adjust verbosity:
```xml
<!-- Application code -->
<logger name="com.bsg6" level="INFO"/>

<!-- KSeF SDK -->
<logger name="pl.akmf.ksef" level="DEBUG"/>

<!-- HTTP client (for debugging API calls) -->
<logger name="org.apache.http.wire" level="DEBUG"/>
```

**Environment-Specific Configuration:**
```bash
# Set log level via environment variable
export LOG_LEVEL=DEBUG

# Set custom log file location
export LOG_FILE=/var/log/ksef/application

# Set environment tag for log filtering
export ENVIRONMENT=production
```

### Log Analysis

**Common Analysis Scenarios:**

1. **Track invoice processing pipeline:**
   ```bash
   # Find correlation ID from initial generation
   correlation_id=$(cat logs/invoice_generator.log | jq -r 'select(.invoice_number == "1/12/2025") | .correlation_id' | head -1)

   # Trace all operations with that correlation ID
   cat logs/*.log | jq "select(.correlation_id == \"$correlation_id\")"
   ```

2. **Monitor error rates:**
   ```bash
   # Count errors in last hour
   cat logs/ksef-application-error.log | jq -r '.timestamp' | \
     awk -v cutoff="$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S)" '$1 > cutoff' | wc -l
   ```

3. **Performance analysis:**
   ```bash
   # Find slow operations (if duration_ms is logged)
   cat logs/*.log | jq 'select(.duration_ms > 1000)'
   ```

4. **Audit trail for specific invoice:**
   ```bash
   # All operations for invoice 1/12/2025
   grep -r "1/12/2025" logs/*.log | jq '.'
   ```

**Integration with Log Aggregation Tools:**

The JSON format is compatible with:
- **ELK Stack** (Elasticsearch, Logstash, Kibana)
- **Splunk**
- **AWS CloudWatch Logs Insights**
- **Datadog**
- **Grafana Loki**

Example Logstash configuration:
```ruby
input {
  file {
    path => "/path/to/logs/*.log"
    codec => "json"
  }
}

filter {
  # Logs are already in JSON format, no parsing needed
}

output {
  elasticsearch {
    hosts => ["localhost:9200"]
    index => "ksef-logs-%{+YYYY.MM.dd}"
  }
}
```

## Project Structure

```
src/test/resources/invoice/
├── input/
│   ├── json/pl/faktura/           # JSON invoice samples
│   ├── pdf/pl/
│   │   ├── real/faktura/          # Real invoice PDFs
│   │   └── fake/generated/        # Generated test PDFs
│   └── xml/pl/faktura/            # UBL XML samples
└── output/
    └── ksef/
        └── fa_2/
            ├── invoice-template.xml           # Template
            └── generated/                     # Generated KSeF XMLs
```