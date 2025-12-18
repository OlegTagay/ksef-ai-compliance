#!/usr/bin/env python3
"""
Generate last 10 invoices and convert to KSeF XML format.
"""

import argparse
from pathlib import Path
from generate_invoices import InvoiceGenerator
from pdf_generator import PDFInvoiceGenerator
from ksef_xml_converter import KSeFXMLConverter


def main():
    """Generate last 10 invoices in both PDF and XML formats."""
    parser = argparse.ArgumentParser(
        description='Generate last 10 invoices with PDF and KSeF XML formats'
    )
    parser.add_argument(
        '-c', '--config',
        type=str,
        default='config.yaml',
        help='Path to config file (default: config.yaml)'
    )
    parser.add_argument(
        '--start-number',
        type=int,
        default=91,
        help='Starting invoice number (default: 91)'
    )

    args = parser.parse_args()

    print(f"Generating invoices {args.start_number} to {args.start_number + 9}...")

    # Initialize generators
    invoice_gen = InvoiceGenerator(args.config)
    pdf_gen = PDFInvoiceGenerator()
    xml_converter = KSeFXMLConverter()

    # Setup output directories
    pdf_output_dir = Path(invoice_gen.config['generation']['output_dir'])
    xml_output_dir = Path('../../src/test/resources/invoice/output/ksef/fa_2/generated')

    pdf_output_dir.mkdir(parents=True, exist_ok=True)
    xml_output_dir.mkdir(parents=True, exist_ok=True)

    # Generate 10 invoices
    invoices = []
    for i in range(args.start_number, args.start_number + 10):
        invoice = invoice_gen.generate_invoice(i)
        invoices.append(invoice)

        # Save as PDF
        pdf_filename = f"faktura-{invoice['number'].replace('/', '-')}.pdf"
        pdf_filepath = pdf_output_dir / pdf_filename
        pdf_gen.generate_pdf(invoice, str(pdf_filepath))
        print(f"Generated PDF: {pdf_filepath}")

        # Save as KSeF XML
        xml_filename = f"faktura-{invoice['number'].replace('/', '-')}.xml"
        xml_filepath = xml_output_dir / xml_filename
        xml_converter.save_ksef_xml(invoice, str(xml_filepath))
        print(f"Generated XML: {xml_filepath}")

    print(f"\nSuccessfully generated {len(invoices)} invoices!")
    print(f"PDF directory: {pdf_output_dir}")
    print(f"XML directory: {xml_output_dir}")


if __name__ == '__main__':
    main()
