#!/usr/bin/env python3
"""
Invoice Generator Script

Generates N random invoices with random products and quantities.
Uses configuration from config.yaml or auto-generates data if missing.
"""

import json
import yaml
import random
import argparse
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Any
import os
from pdf_generator import PDFInvoiceGenerator

# Import logging configuration
from logging_config import setup_logging, get_logger, set_correlation_id

# Initialize logger
logger = get_logger(__name__)


class InvoiceGenerator:
    """Generate random invoices based on configuration."""

    def __init__(self, config_path: str = "config.yaml"):
        """Initialize generator with config file."""
        self.config = self.load_config(config_path)

    def load_config(self, config_path: str) -> Dict:
        """Load configuration from YAML file."""
        config_file = Path(config_path)
        if config_file.exists():
            with open(config_file, 'r', encoding='utf-8') as f:
                return yaml.safe_load(f)
        else:
            logger.warning(f"Config file {config_path} not found, using defaults", extra={'extra_fields': {'config_path': config_path}})
            return self.get_default_config()

    def get_default_config(self) -> Dict:
        """Return default configuration if config file is missing."""
        return {
            'seller': {
                'name': 'DEFAULT SELLER',
                'tax_no': self.generate_nip(),
                'street': 'ul. Testowa 1',
                'post_code': '00-000',
                'city': 'Warszawa',
                'country': 'PL',
                'email': 'seller@example.com',
                'phone': '+48123456789',
                'person': 'Jan Kowalski',
                'bank': 'PKO BP',
                'bank_account': self.generate_bank_account()
            },
            'buyer': {},
            'invoice': {
                'currency': 'PLN',
                'payment_type': 'transfer',
                'payment_days': 7,
                'tax_rate': 23,
                'lang': 'pl',
                'quantity_unit': 'szt'
            },
            'products': [
                {'name': 'PRODUCT A', 'price_net_min': 100, 'price_net_max': 500},
                {'name': 'PRODUCT B', 'price_net_min': 200, 'price_net_max': 1000},
                {'name': 'PRODUCT C', 'price_net_min': 50, 'price_net_max': 300}
            ],
            'generation': {
                'min_positions': 1,
                'max_positions': 5,
                'min_quantity': 1,
                'max_quantity': 10,
                'output_format': 'json',
                'output_dir': './generated'
            }
        }

    @staticmethod
    def generate_nip() -> str:
        """Generate random valid NIP number."""
        # Simple random 10-digit NIP (not validated)
        return ''.join([str(random.randint(0, 9)) for _ in range(10)])

    @staticmethod
    def generate_bank_account() -> str:
        """Generate random bank account number (26 digits for Polish IBAN)."""
        return ''.join([str(random.randint(0, 9)) for _ in range(26)])

    def generate_buyer(self) -> Dict:
        """Generate random buyer or use from config."""
        buyer_config = self.config.get('buyer', {})

        # Check if buyer has name, if not generate
        if buyer_config.get('name'):
            return {
                'buyer_name': buyer_config['name'],
                'buyer_tax_no': buyer_config.get('tax_no') or self.generate_nip(),
                'buyer_street': buyer_config.get('street', 'ul. Kupujacego 10'),
                'buyer_post_code': buyer_config.get('post_code', '00-001'),
                'buyer_city': buyer_config.get('city', 'Warszawa'),
                'buyer_country': buyer_config.get('country', 'PL'),
                'buyer_email': buyer_config.get('email', ''),
                'buyer_phone': buyer_config.get('phone', ''),
                'buyer_company': True
            }
        else:
            # Generate random buyer
            companies = [
                'GENERAL MOTORS', 'ABC CORPORATION', 'XYZ TRADING',
                'TECH SOLUTIONS', 'MEGA CORP', 'GLOBAL SERVICES',
                'INNOVATION HUB', 'FUTURE SYSTEMS', 'DYNAMIC GROUP',
                'PREMIUM TRADE'
            ]
            cities = ['Warszawa', 'Krakow', 'Wroclaw', 'Poznan', 'Gdansk']

            return {
                'buyer_name': random.choice(companies),
                'buyer_tax_no': self.generate_nip(),
                'buyer_street': f'ul. {random.choice(["Wielka", "Mala", "Nowa", "Stara"])} {random.randint(1, 100)}',
                'buyer_post_code': f'{random.randint(10, 99)}-{random.randint(100, 999)}',
                'buyer_city': random.choice(cities),
                'buyer_country': 'PL',
                'buyer_email': '',
                'buyer_phone': '',
                'buyer_company': True
            }

    def generate_seller(self) -> Dict:
        """Get seller data from config."""
        seller = self.config.get('seller', {})
        return {
            'seller_name': seller.get('name', 'DEFAULT SELLER'),
            'seller_tax_no': seller.get('tax_no', self.generate_nip()),
            'seller_street': seller.get('street', 'ul. Sprzedawcy 1'),
            'seller_post_code': seller.get('post_code', '00-000'),
            'seller_city': seller.get('city', 'Warszawa'),
            'seller_country': seller.get('country', 'PL'),
            'seller_email': seller.get('email', ''),
            'seller_phone': seller.get('phone', ''),
            'seller_fax': '',
            'seller_www': '',
            'seller_person': seller.get('person', ''),
            'seller_bank': seller.get('bank', 'PKO BP'),
            'seller_bank_account': seller.get('bank_account', self.generate_bank_account())
        }

    def generate_positions(self) -> List[Dict]:
        """Generate random invoice positions (products)."""
        gen_config = self.config.get('generation', {})
        products = self.config.get('products', [])

        min_pos = gen_config.get('min_positions', 1)
        max_pos = gen_config.get('max_positions', 5)
        min_qty = gen_config.get('min_quantity', 1)
        max_qty = gen_config.get('max_quantity', 10)

        num_positions = random.randint(min_pos, max_pos)
        positions = []

        invoice_config = self.config.get('invoice', {})
        tax_rate = invoice_config.get('tax_rate', 23)
        quantity_unit = invoice_config.get('quantity_unit', 'szt')

        for _ in range(num_positions):
            product = random.choice(products)
            quantity = random.randint(min_qty, max_qty)

            price_net = round(random.uniform(
                product['price_net_min'],
                product['price_net_max']
            ), 2)

            price_tax = round(price_net * tax_rate / 100, 2)
            price_gross = round(price_net + price_tax, 2)

            total_price_net = round(price_net * quantity, 2)
            total_price_tax = round(price_tax * quantity, 2)
            total_price_gross = round(price_gross * quantity, 2)

            position = {
                'name': product['name'],
                'price_net': str(price_net),
                'quantity': str(float(quantity)),
                'total_price_gross': str(total_price_gross),
                'total_price_net': str(total_price_net),
                'additional_info': '',
                'quantity_unit': quantity_unit,
                'tax': str(tax_rate),
                'price_gross': str(price_gross),
                'price_tax': str(price_tax),
                'total_price_tax': str(total_price_tax),
                'discount': None,
                'discount_percent': None,
                'tax2': '0',
                'code': None
            }
            positions.append(position)

        return positions

    def generate_invoice(self, invoice_number: int, date: datetime = None) -> Dict:
        """Generate a single invoice."""
        if date is None:
            date = datetime.now()

        invoice_config = self.config.get('invoice', {})
        payment_days = invoice_config.get('payment_days', 7)

        issue_date = date.strftime('%Y-%m-%d')
        sell_date = date.strftime('%Y-%m-%d')
        payment_to = (date + timedelta(days=payment_days)).strftime('%Y-%m-%d')

        # Generate positions
        positions = self.generate_positions()

        # Calculate totals
        price_net = sum(float(p['total_price_net']) for p in positions)
        price_tax = sum(float(p['total_price_tax']) for p in positions)
        price_gross = sum(float(p['total_price_gross']) for p in positions)

        # Build invoice
        invoice = {
            'number': f'{invoice_number}/{date.month}/{date.year}',
            'place': '',
            'sell_date': sell_date,
            'payment_type': invoice_config.get('payment_type', 'transfer'),
            'price_net': str(round(price_net, 2)),
            'price_gross': str(round(price_gross, 2)),
            'currency': invoice_config.get('currency', 'PLN'),
            'description': '',
            'kind': 'vat',
            'payment_to': payment_to,
            'paid': str(round(price_gross, 2)),
            'seller_bank_account_id': None,
            'lang': invoice_config.get('lang', 'pl'),
            'issue_date': issue_date,
            'price_tax': str(round(price_tax, 2)),
            'oid': '',
            'discount': '0.0',
            'show_discount': False,
            'description_long': '',
            'buyer_tax_no_kind': None,
            'seller_tax_no_kind': None,
            'description_footer': '',
            'sell_date_kind': '',
            'payment_to_kind': '1',
            'exchange_currency': None,
            'discount_kind': 'percent_total',
            'income': True,
            'exchange_kind': 'nbp',
            'exchange_rate': '1.0',
            'delivery_address': '',
            'buyer_person': '',
            'buyer_bank_account': None,
            'buyer_bank': None,
            'exchange_note': '',
            'exchange_currency_rate': None,
            'exchange_date': None,
            'split_payment': '0',
            'buyer_mobile_phone': '',
            'seller_bdo_no': '',
            'e_receipt_view_url': None,
            'buyer_www': None,
            'buyer_fax': None,
            'positions': positions
        }

        # Add seller and buyer
        invoice.update(self.generate_seller())
        invoice.update(self.generate_buyer())

        return invoice

    def generate_invoices(self, count: int, start_date: datetime = None) -> List[Dict]:
        """Generate N invoices."""
        if start_date is None:
            start_date = datetime.now()

        invoices = []
        for i in range(1, count + 1):
            # Vary dates slightly
            invoice_date = start_date - timedelta(days=random.randint(0, 30))
            invoice = self.generate_invoice(i, invoice_date)
            invoices.append(invoice)

        return invoices

    def save_invoices(self, invoices: List[Dict]):
        """Save invoices to files."""
        gen_config = self.config.get('generation', {})
        output_dir = Path(gen_config.get('output_dir', './generated'))
        output_format = gen_config.get('output_format', 'pdf')

        # Create output directory
        output_dir.mkdir(parents=True, exist_ok=True)

        # Initialize PDF generator if needed
        if output_format == 'pdf':
            pdf_gen = PDFInvoiceGenerator()

        for invoice in invoices:
            filename = f"faktura-{invoice['number'].replace('/', '-')}.{output_format}"
            filepath = output_dir / filename

            if output_format == 'json':
                with open(filepath, 'w', encoding='utf-8') as f:
                    json.dump(invoice, f, indent=2, ensure_ascii=False)
            elif output_format == 'pdf':
                pdf_gen.generate_pdf(invoice, str(filepath))
            elif output_format == 'xml':
                # XML generation can be added later
                logger.warning("XML format not yet implemented, saving as JSON instead", extra={'extra_fields': {'invoice_number': invoice.get('number')}})
                filepath = output_dir / filename.replace('.xml', '.json')
                with open(filepath, 'w', encoding='utf-8') as f:
                    json.dump(invoice, f, indent=2, ensure_ascii=False)

            logger.info(f"Invoice generated successfully", extra={'extra_fields': {'filepath': str(filepath), 'invoice_number': invoice.get('number'), 'event_type': 'invoice_generated'}})


def main():
    """Main entry point."""
    # Initialize logging
    setup_logging(log_level="INFO", log_file="logs/invoice_generator.log", log_format="json")

    parser = argparse.ArgumentParser(
        description='Generate random invoices for testing'
    )
    parser.add_argument(
        '-n', '--count',
        type=int,
        default=10,
        help='Number of invoices to generate (default: 10)'
    )
    parser.add_argument(
        '-c', '--config',
        type=str,
        default='config.yaml',
        help='Path to config file (default: config.yaml)'
    )

    args = parser.parse_args()

    # Set correlation ID for this batch
    correlation_id = set_correlation_id()

    print(f"Generating {args.count} invoices...")
    logger.info(f"Starting invoice generation batch", extra={'extra_fields': {'count': args.count, 'config': args.config, 'event_type': 'batch_start'}})

    try:
        generator = InvoiceGenerator(args.config)
        invoices = generator.generate_invoices(args.count)
        generator.save_invoices(invoices)

        print(f"\nSuccessfully generated {len(invoices)} invoices!")
        print(f"Output directory: {generator.config['generation']['output_dir']}")
        logger.info(f"Invoice generation batch completed", extra={'extra_fields': {'count': len(invoices), 'output_dir': generator.config['generation']['output_dir'], 'event_type': 'batch_complete'}})
    except Exception as e:
        logger.error(f"Invoice generation failed: {str(e)}", extra={'extra_fields': {'event_type': 'batch_failed'}}, exc_info=True)
        raise


if __name__ == '__main__':
    main()
