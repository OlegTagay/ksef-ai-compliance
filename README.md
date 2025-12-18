# ksef-ai-compliance

Polish KSeF (Krajowy System e-Faktur) invoice processing and compliance testing system with AI-powered invoice generation.

## Table of Contents
- [Python Invoice Generator](#python-invoice-generator)
  - [Scripts Overview](#scripts-overview)
  - [Configuration](#configuration)
  - [Usage Examples](#usage-examples)
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
- Intelligent data extraction from Polish invoices
- Automatic NIP validation and formatting
- Built-in CLI interface

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