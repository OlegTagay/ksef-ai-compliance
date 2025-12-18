"""
KSeF XML Converter

Converts invoice data to Polish KSeF (Krajowy System e-Faktur) XML format.
Supports conversion from Python dictionaries and PDF files.

Validates against official KSeF schemas:
- FA(2): https://github.com/CIRFMF/ksef-docs/blob/main/faktury/schemy/FA/schemat_FA(2)_v1-0E.xsd
- FA(3): https://github.com/CIRFMF/ksef-docs/blob/main/faktury/schemy/FA/schemat_FA(3)_v1-0E.xsd

Official KSeF documentation: https://github.com/CIRFMF/ksef-docs
"""

import os
import json
import re
from datetime import datetime
from typing import Dict, Optional, Tuple, Literal
from xml.etree import ElementTree as ET
from xml.dom import minidom
from pathlib import Path
import pdfplumber
from anthropic import Anthropic
from lxml import etree

# Import logging configuration
from logging_config import (
    get_logger,
    set_correlation_id,
    log_file_operation,
    log_api_call,
    log_invoice_processing
)

# Initialize logger
logger = get_logger(__name__)


class KSeFXMLConverter:
    """Convert invoice data to KSeF XML format."""

    def __init__(self, anthropic_api_key: Optional[str] = None, schema_type: Literal['FA2', 'FA3'] = 'FA2'):
        """Initialize converter.

        Args:
            anthropic_api_key: Optional API key for Claude AI. If not provided,
                             will look for ANTHROPIC_API_KEY environment variable.
            schema_type: Type of KSeF schema to use ('FA2' or 'FA3'). Default is 'FA2'.
        """
        self.namespace = "http://crd.gov.pl/wzor/2023/06/29/12648/"
        self.namespace_xsi = "http://www.w3.org/2001/XMLSchema-instance"
        self.namespace_xsd = "http://www.w3.org/2001/XMLSchema"
        self.schema_type = schema_type

        # Determine schema file path
        script_dir = Path(__file__).parent.parent.parent  # Go up to project root
        self.schema_path = script_dir / 'src' / 'main' / 'resources' / f'schemat_FA({schema_type[-1]})_v1-0E.xsd'

        # Initialize Anthropic client for PDF parsing
        api_key = anthropic_api_key or os.environ.get('ANTHROPIC_API_KEY')
        if api_key:
            self.anthropic_client = Anthropic(api_key=api_key)
        else:
            self.anthropic_client = None

    def convert_to_ksef_xml(self, invoice_data: Dict) -> str:
        """Convert invoice data to KSeF XML string."""
        # Register namespaces
        ET.register_namespace('', self.namespace)
        ET.register_namespace('xsi', self.namespace_xsi)
        ET.register_namespace('xsd', self.namespace_xsd)

        # Create root element
        root = ET.Element(
            f"{{{self.namespace}}}Faktura",
            attrib={
                f"{{{self.namespace_xsi}}}schemaLocation": f"{self.namespace} http://crd.gov.pl/wzor/2023/06/29/12648/schemat.xsd"
            }
        )

        # Naglowek (Header)
        naglowek = ET.SubElement(root, f"{{{self.namespace}}}Naglowek")

        # Use schema_type to determine variant (2 or 3)
        variant_number = self.schema_type[-1]  # Extract '2' from 'FA2' or '3' from 'FA3'

        kod_formularza = ET.SubElement(
            naglowek,
            f"{{{self.namespace}}}KodFormularza",
            attrib={
                "kodSystemowy": f"FA ({variant_number})",
                "wersjaSchemy": "1-0E"
            }
        )
        kod_formularza.text = "FA"

        wariant = ET.SubElement(naglowek, f"{{{self.namespace}}}WariantFormularza")
        wariant.text = variant_number

        data_wytworzenia = ET.SubElement(naglowek, f"{{{self.namespace}}}DataWytworzeniaFa")
        data_wytworzenia.text = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")

        # Podmiot1 (Seller)
        podmiot1 = ET.SubElement(root, f"{{{self.namespace}}}Podmiot1")
        dane_id1 = ET.SubElement(podmiot1, f"{{{self.namespace}}}DaneIdentyfikacyjne")

        nip1 = ET.SubElement(dane_id1, f"{{{self.namespace}}}NIP")
        nip1.text = invoice_data['seller_tax_no']

        nazwa1 = ET.SubElement(dane_id1, f"{{{self.namespace}}}Nazwa")
        nazwa1.text = invoice_data['seller_name']

        adres1 = ET.SubElement(podmiot1, f"{{{self.namespace}}}Adres")
        kod_kraju1 = ET.SubElement(adres1, f"{{{self.namespace}}}KodKraju")
        kod_kraju1.text = invoice_data.get('seller_country', 'PL')

        adres_l1_1 = ET.SubElement(adres1, f"{{{self.namespace}}}AdresL1")
        # For seller, use only street address
        adres_l1_1.text = invoice_data['seller_street']

        # Podmiot2 (Buyer)
        podmiot2 = ET.SubElement(root, f"{{{self.namespace}}}Podmiot2")
        dane_id2 = ET.SubElement(podmiot2, f"{{{self.namespace}}}DaneIdentyfikacyjne")

        nip2 = ET.SubElement(dane_id2, f"{{{self.namespace}}}NIP")
        nip2.text = invoice_data['buyer_tax_no']

        nazwa2 = ET.SubElement(dane_id2, f"{{{self.namespace}}}Nazwa")
        nazwa2.text = invoice_data['buyer_name']

        adres2 = ET.SubElement(podmiot2, f"{{{self.namespace}}}Adres")
        kod_kraju2 = ET.SubElement(adres2, f"{{{self.namespace}}}KodKraju")
        kod_kraju2.text = invoice_data.get('buyer_country', 'PL')

        adres_l1_2 = ET.SubElement(adres2, f"{{{self.namespace}}}AdresL1")
        adres_l1_2.text = f"{invoice_data['buyer_street']}, {invoice_data['buyer_post_code']} {invoice_data['buyer_city']}"

        # Fa (Invoice details)
        fa = ET.SubElement(root, f"{{{self.namespace}}}Fa")

        kod_waluty = ET.SubElement(fa, f"{{{self.namespace}}}KodWaluty")
        kod_waluty.text = invoice_data['currency']

        p_1 = ET.SubElement(fa, f"{{{self.namespace}}}P_1")
        p_1.text = invoice_data['issue_date']

        p_2 = ET.SubElement(fa, f"{{{self.namespace}}}P_2")
        p_2.text = invoice_data['number']

        p_13_1 = ET.SubElement(fa, f"{{{self.namespace}}}P_13_1")
        p_13_1.text = invoice_data['price_net']

        p_14_1 = ET.SubElement(fa, f"{{{self.namespace}}}P_14_1")
        p_14_1.text = invoice_data['price_tax']

        p_15 = ET.SubElement(fa, f"{{{self.namespace}}}P_15")
        p_15.text = invoice_data['price_gross']

        # Adnotacje (Annotations)
        adnotacje = ET.SubElement(fa, f"{{{self.namespace}}}Adnotacje")

        p_16 = ET.SubElement(adnotacje, f"{{{self.namespace}}}P_16")
        p_16.text = "2"

        p_17 = ET.SubElement(adnotacje, f"{{{self.namespace}}}P_17")
        p_17.text = "2"

        p_18 = ET.SubElement(adnotacje, f"{{{self.namespace}}}P_18")
        p_18.text = "2"

        p_18a = ET.SubElement(adnotacje, f"{{{self.namespace}}}P_18A")
        p_18a.text = "2"

        zwolnienie = ET.SubElement(adnotacje, f"{{{self.namespace}}}Zwolnienie")
        p_19n = ET.SubElement(zwolnienie, f"{{{self.namespace}}}P_19N")
        p_19n.text = "1"

        nowe_srodki = ET.SubElement(adnotacje, f"{{{self.namespace}}}NoweSrodkiTransportu")
        p_22n = ET.SubElement(nowe_srodki, f"{{{self.namespace}}}P_22N")
        p_22n.text = "1"

        p_23 = ET.SubElement(adnotacje, f"{{{self.namespace}}}P_23")
        p_23.text = "2"

        p_marzy = ET.SubElement(adnotacje, f"{{{self.namespace}}}PMarzy")
        p_marzy_n = ET.SubElement(p_marzy, f"{{{self.namespace}}}P_PMarzyN")
        p_marzy_n.text = "1"

        # RodzajFaktury (Invoice type)
        rodzaj = ET.SubElement(fa, f"{{{self.namespace}}}RodzajFaktury")
        rodzaj.text = "VAT"

        # Convert to pretty XML string
        xml_str = ET.tostring(root, encoding='utf-8')
        dom = minidom.parseString(xml_str)
        return dom.toprettyxml(indent="    ", encoding='utf-8').decode('utf-8')

    def validate_against_xsd(self, xml_content: str) -> Tuple[bool, Optional[str]]:
        """Validate XML content against KSeF XSD schema.

        Args:
            xml_content: XML string to validate

        Returns:
            Tuple of (is_valid: bool, error_message: Optional[str])
            If valid, returns (True, None)
            If invalid, returns (False, error_message)

        Raises:
            FileNotFoundError: If XSD schema file not found
        """
        logger.info(
            f"Validating XML against {self.schema_type} schema",
            extra={'extra_fields': {
                'schema_type': self.schema_type,
                'schema_path': str(self.schema_path),
                'event_type': 'validation_start'
            }}
        )

        if not self.schema_path.exists():
            error_msg = f"XSD schema file not found: {self.schema_path}"
            logger.error(error_msg, extra={'extra_fields': {'event_type': 'schema_not_found'}})
            raise FileNotFoundError(error_msg)

        try:
            # Parse XSD schema
            with open(self.schema_path, 'rb') as schema_file:
                schema_doc = etree.parse(schema_file)
                schema = etree.XMLSchema(schema_doc)

            # Parse XML content
            xml_doc = etree.fromstring(xml_content.encode('utf-8'))

            # Validate
            is_valid = schema.validate(xml_doc)

            if is_valid:
                logger.info(
                    "XML validation successful",
                    extra={'extra_fields': {
                        'schema_type': self.schema_type,
                        'event_type': 'validation_success'
                    }}
                )
                return True, None
            else:
                # Collect all validation errors
                errors = []
                for error in schema.error_log:
                    errors.append(f"Line {error.line}: {error.message}")

                error_message = "\n".join(errors)
                logger.error(
                    "XML validation failed",
                    extra={'extra_fields': {
                        'schema_type': self.schema_type,
                        'validation_errors': errors,
                        'event_type': 'validation_failed'
                    }}
                )
                return False, error_message

        except etree.XMLSyntaxError as e:
            error_msg = f"XML syntax error: {str(e)}"
            logger.error(
                error_msg,
                extra={'extra_fields': {'event_type': 'xml_syntax_error'}},
                exc_info=True
            )
            return False, error_msg
        except Exception as e:
            error_msg = f"Validation error: {str(e)}"
            logger.error(
                error_msg,
                extra={'extra_fields': {'event_type': 'validation_error'}},
                exc_info=True
            )
            return False, error_msg

    def save_ksef_xml(self, invoice_data: Dict, output_path: str, validate: bool = True):
        """Save invoice as KSeF XML file with optional validation.

        Args:
            invoice_data: Invoice data dictionary
            output_path: Path to save XML file
            validate: If True, validate against XSD schema before saving (default: True)

        Raises:
            ValueError: If validation fails
        """
        xml_content = self.convert_to_ksef_xml(invoice_data)

        # Validate before saving if requested
        if validate:
            is_valid, error_message = self.validate_against_xsd(xml_content)
            if not is_valid:
                raise ValueError(f"XML validation failed:\n{error_message}")

        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(xml_content)

    def extract_text_from_pdf(self, pdf_path: str) -> str:
        """Extract text content from PDF file.

        Args:
            pdf_path: Path to the PDF file

        Returns:
            Extracted text content from the PDF

        Raises:
            FileNotFoundError: If PDF file doesn't exist
        """
        if not os.path.exists(pdf_path):
            raise FileNotFoundError(f"PDF file not found: {pdf_path}")

        text_content = []
        with pdfplumber.open(pdf_path) as pdf:
            for page in pdf.pages:
                text = page.extract_text()
                if text:
                    text_content.append(text)

        return "\n\n".join(text_content)

    def parse_invoice_with_rules(self, text: str) -> Tuple[Dict, bool]:
        """Parse invoice data from text using rule-based pattern matching.

        Args:
            text: Text content extracted from invoice

        Returns:
            Tuple of (invoice_data dict, success boolean)
            If successful, returns populated dict and True
            If failed, returns partial dict and False
        """
        logger.info("Attempting rule-based parsing", extra={'extra_fields': {'parsing_method': 'rule-based'}})

        invoice_data = {
            'seller_name': '',
            'seller_tax_no': '',
            'seller_street': '',
            'seller_country': 'PL',
            'buyer_name': '',
            'buyer_tax_no': '',
            'buyer_street': '',
            'buyer_post_code': '',
            'buyer_city': '',
            'buyer_country': 'PL',
            'currency': 'PLN',
            'issue_date': '',
            'number': '',
            'price_net': '0.00',
            'price_tax': '0.00',
            'price_gross': '0.00'
        }

        # Normalize text for easier matching
        text_lines = text.split('\n')

        # Pattern 1: Extract NIP numbers (10 digits, may have dashes or spaces)
        nip_pattern = r'NIP[:\s]*(\d{3}[-\s]?\d{3}[-\s]?\d{2}[-\s]?\d{2}|\d{10})'
        nip_matches = re.findall(nip_pattern, text, re.IGNORECASE)
        if len(nip_matches) >= 2:
            # First NIP is usually seller, second is buyer
            invoice_data['seller_tax_no'] = re.sub(r'[-\s]', '', nip_matches[0])
            invoice_data['buyer_tax_no'] = re.sub(r'[-\s]', '', nip_matches[1])
        elif len(nip_matches) == 1:
            invoice_data['seller_tax_no'] = re.sub(r'[-\s]', '', nip_matches[0])

        # Pattern 2: Extract invoice number
        # Supports both Polish ("Faktura Nr") and English ("INVOICE #") formats
        invoice_num_patterns = [
            r'INVOICE\s*#\s*(\d+)',  # English: "INVOICE # 13"
            r'Invoice\s+(?:Number|No\.?)[:\s]*(\d+)',  # English: "Invoice Number: 13"
            r'Faktura\s+(?:Nr\.?|numer|VAT)?\s*[:\s]*([A-Z0-9/\-]+)',  # Polish
            r'(?:FA|FV)[/\-](\d+[/\-]\d+[/\-]\d+)',  # Polish: FA/001/2025
            r'Numer\s+faktury[:\s]*([A-Z0-9/\-]+)'  # Polish
        ]
        for pattern in invoice_num_patterns:
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                invoice_data['number'] = match.group(1) if len(match.groups()) == 1 else match.group(0)
                break

        # Pattern 3: Extract dates
        # Supports Polish, English, and various numeric formats
        date_patterns = [
            r'DATE[:\s]*(\d{1,2})\s+([A-Za-z]+)[,\s]+(\d{4})',  # English: "DATE: 02 April, 2025"
            r'(?:Invoice\s+)?Date[:\s]*(\d{1,2})\s+([A-Za-z]+)[,\s]+(\d{4})',  # "Invoice Date: 02 April, 2025"
            r'Data\s+wystawienia[:\s]*(\d{4}-\d{2}-\d{2})',  # Polish: "Data wystawienia: 2025-04-02"
            r'Data\s+wystawienia[:\s]*(\d{2}[-/.]\d{2}[-/.]\d{4})',  # Polish: "Data wystawienia: 02.04.2025"
            r'Data\s+sprzedaży[:\s]*(\d{4}-\d{2}-\d{2})',
            r'Data\s+sprzedaży[:\s]*(\d{2}[-/.]\d{2}[-/.]\d{4})'
        ]

        month_names = {
            'january': '01', 'february': '02', 'march': '03', 'april': '04',
            'may': '05', 'june': '06', 'july': '07', 'august': '08',
            'september': '09', 'october': '10', 'november': '11', 'december': '12'
        }

        for pattern in date_patterns:
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                groups = match.groups()
                if len(groups) == 3:  # English date format (day, month name, year)
                    day, month_name, year = groups
                    month = month_names.get(month_name.lower(), '01')
                    date_str = f"{year}-{month}-{day.zfill(2)}"
                else:  # Numeric date format
                    date_str = groups[0]
                    # Convert DD-MM-YYYY or DD.MM.YYYY to YYYY-MM-DD
                    if re.match(r'\d{2}[-/.]\d{2}[-/.]\d{4}', date_str):
                        parts = re.split(r'[-/.]', date_str)
                        date_str = f"{parts[2]}-{parts[1]}-{parts[0]}"
                invoice_data['issue_date'] = date_str
                break

        # Pattern 4: Extract amounts
        # Supports both Polish and English formats (EU invoices only)
        amount_patterns = [
            # English patterns with PLN
            (r'SUBTOTAL[:\s]*([\d,\s]+(?:\.\d{2})?)\s*zł', 'price_net'),  # "SUBTOTAL 22,344.00 zł"
            (r'VAT\s+\d+%[:\s]*(?:EUR\s+)?([\d,\s]+(?:\.\d{2})?)\s*zł', 'price_tax'),  # "VAT 23% EUR 5,139.12 zł"
            (r'TOTAL[:\s]*([\d,\s]+(?:\.\d{2})?)\s*zł', 'price_gross'),  # "TOTAL 27,483.12 zł"
            # Polish patterns
            (r'(?:Wartość\s+)?[Nn]etto[:\s]*([\d,\s]+)', 'price_net'),
            (r'(?:Wartość\s+)?VAT[:\s]*([\d,\s]+)', 'price_tax'),
            (r'(?:Wartość\s+)?[Bb]rutto[:\s]*([\d,\s]+)', 'price_gross'),
            (r'Razem[:\s]*([\d,\s]+)', 'price_gross'),
            (r'Do\s+zapłaty[:\s]*([\d,\s]+)', 'price_gross')
        ]

        for pattern, field in amount_patterns:
            matches = re.findall(pattern, text, re.IGNORECASE)
            if matches:
                # Take the last match (usually the total)
                amount = matches[-1].replace(' ', '').replace(',', '')
                # Handle potential decimal formatting
                if '.' in amount:
                    # Already has decimal point
                    parts = amount.split('.')
                    if len(parts[-1]) == 2:  # e.g., "22344.00"
                        amount = amount
                    else:  # e.g., "22.344" (thousands separator)
                        amount = amount.replace('.', '') + '.00'
                else:
                    amount += '.00'
                invoice_data[field] = amount

        # Pattern 5: Extract company names
        # Supports Polish ("Sprzedawca") and English ("Bill To") formats

        # Try Polish format first
        seller_section = re.search(r'Sprzedawca[:\s]*\n([^\n]+)', text, re.IGNORECASE)
        if seller_section:
            invoice_data['seller_name'] = seller_section.group(1).strip()

        # Try English format: look for name after INVOICE and before street address
        if not invoice_data['seller_name']:
            # Pattern: INVOICE, then name on next line, then street address
            seller_eng = re.search(r'INVOICE\s*\n([^\n]+)\s*\n([^\n]+street[^\n]*)', text, re.IGNORECASE)
            if seller_eng:
                invoice_data['seller_name'] = seller_eng.group(1).strip()
                invoice_data['seller_street'] = seller_eng.group(2).strip()

        buyer_section = re.search(r'Nabywca[:\s]*\n([^\n]+)', text, re.IGNORECASE)
        if buyer_section:
            invoice_data['buyer_name'] = buyer_section.group(1).strip()

        # Try English format: "Bill To:" followed by company name
        if not invoice_data['buyer_name']:
            buyer_eng = re.search(r'Bill\s+To[:\s]*([^\n]+)', text, re.IGNORECASE)
            if buyer_eng:
                invoice_data['buyer_name'] = buyer_eng.group(1).strip()

        # Pattern 6: Extract addresses
        # Polish format: ul., al., pl.
        street_pattern = r'(?:ul\.|al\.|pl\.)\s+([^\n,]+)'
        street_matches = re.findall(street_pattern, text, re.IGNORECASE)
        if len(street_matches) >= 2:
            invoice_data['seller_street'] = street_matches[0].strip()
            invoice_data['buyer_street'] = street_matches[1].strip()
        elif len(street_matches) == 1:
            if not invoice_data['seller_street']:
                invoice_data['seller_street'] = street_matches[0].strip()

        # English format: Look for "street" or "Aleja" keyword
        if not invoice_data['seller_street']:
            seller_street_eng = re.search(r'([A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż]+\s+street[^,\n]+)', text, re.IGNORECASE)
            if seller_street_eng:
                invoice_data['seller_street'] = seller_street_eng.group(1).strip()

        if not invoice_data['buyer_street']:
            # Look for address after "Bill To" section
            buyer_street_eng = re.search(r'Bill\s+To[^\n]*\n[^\n]+\n([^\n]+)', text, re.IGNORECASE)
            if buyer_street_eng:
                invoice_data['buyer_street'] = buyer_street_eng.group(1).strip()

        # Pattern 7: Extract postal code and city for buyer
        postal_pattern = r'(\d{2}-\d{3})[,\s]+([A-ZĄĆĘŁŃÓŚŹŻa-ząćęłńóśźż\s]+)'
        postal_matches = re.findall(postal_pattern, text)
        if len(postal_matches) >= 2:
            invoice_data['buyer_post_code'] = postal_matches[1][0]
            invoice_data['buyer_city'] = postal_matches[1][1].strip().rstrip(',')
        elif len(postal_matches) >= 1:
            # Check if this is near "Bill To" (buyer) or seller section
            if 'Bill To' in text or not invoice_data['buyer_post_code']:
                invoice_data['buyer_post_code'] = postal_matches[0][0]
                invoice_data['buyer_city'] = postal_matches[0][1].strip().rstrip(',')

        # Validate required fields
        required_fields = [
            'seller_name', 'seller_tax_no', 'buyer_name', 'buyer_tax_no',
            'number', 'issue_date', 'price_net', 'price_tax', 'price_gross'
        ]

        missing_fields = [field for field in required_fields if not invoice_data.get(field) or invoice_data[field] == '0.00']

        if missing_fields:
            logger.warning(
                "Rule-based parsing incomplete",
                extra={'extra_fields': {
                    'missing_fields': missing_fields,
                    'parsing_method': 'rule-based',
                    'event_type': 'parsing_incomplete'
                }}
            )
            return invoice_data, False
        else:
            logger.info(
                "Rule-based parsing successful",
                extra={'extra_fields': {
                    'parsing_method': 'rule-based',
                    'event_type': 'parsing_success'
                }}
            )
            return invoice_data, True

    def parse_invoice_from_text(self, text: str) -> Dict:
        """Parse invoice data from text using Claude AI.

        Args:
            text: Text content extracted from invoice

        Returns:
            Dictionary containing parsed invoice data

        Raises:
            ValueError: If Anthropic API key is not configured
            Exception: If parsing fails
        """
        if not self.anthropic_client:
            raise ValueError(
                "Anthropic API key not configured. "
                "Set ANTHROPIC_API_KEY environment variable or pass api_key to constructor."
            )

        prompt = f"""Extract invoice data from the following Polish invoice text and return it as a JSON object.

Invoice Text:
{text}

Return ONLY a valid JSON object with these exact fields (no additional text):
{{
    "seller_name": "Company name of seller",
    "seller_tax_no": "NIP number of seller (10 digits, no dashes)",
    "seller_street": "Street address of seller",
    "seller_country": "PL",
    "buyer_name": "Company name of buyer",
    "buyer_tax_no": "NIP number of buyer (10 digits, no dashes)",
    "buyer_street": "Street address of buyer",
    "buyer_post_code": "Postal code of buyer",
    "buyer_city": "City of buyer",
    "buyer_country": "PL",
    "currency": "PLN",
    "issue_date": "YYYY-MM-DD",
    "number": "Invoice number",
    "price_net": "Net amount as string (e.g., '100.00')",
    "price_tax": "VAT amount as string (e.g., '23.00')",
    "price_gross": "Gross amount as string (e.g., '123.00')"
}}

Important:
- Extract NIP numbers without any dashes or spaces (exactly 10 digits)
- Use YYYY-MM-DD format for dates
- All price fields should be strings with 2 decimal places
- If any field is not found, use empty string "" for text fields or "0.00" for price fields"""

        try:
            message = self.anthropic_client.messages.create(
                model="claude-3-5-sonnet-20241022",
                max_tokens=2000,
                messages=[
                    {"role": "user", "content": prompt}
                ]
            )

            # Extract JSON from response
            response_text = message.content[0].text.strip()

            # Try to find JSON in the response
            if response_text.startswith('```'):
                # Remove markdown code blocks
                response_text = response_text.split('```')[1]
                if response_text.startswith('json'):
                    response_text = response_text[4:]
                response_text = response_text.strip()

            invoice_data = json.loads(response_text)
            return invoice_data

        except json.JSONDecodeError as e:
            raise Exception(f"Failed to parse JSON from AI response: {e}\nResponse: {response_text}")
        except Exception as e:
            raise Exception(f"Failed to parse invoice data: {e}")

    def convert_pdf_to_ksef_xml(self, pdf_path: str, output_path: Optional[str] = None, force_ai: bool = False) -> str:
        """Convert PDF invoice to KSeF XML format using hybrid approach.

        This method first attempts rule-based parsing. If that fails or if force_ai is True,
        it falls back to Claude AI for intelligent parsing.

        Args:
            pdf_path: Path to the PDF invoice file
            output_path: Optional path to save the XML file. If not provided, only returns XML string.
            force_ai: If True, skip rule-based parsing and use Claude AI directly

        Returns:
            KSeF XML string

        Raises:
            FileNotFoundError: If PDF file doesn't exist
            ValueError: If API key is not configured when AI parsing is needed
            Exception: If both parsing methods fail
        """
        # Set correlation ID for this conversion
        correlation_id = set_correlation_id()

        logger.info(
            f"Starting PDF to KSeF XML conversion",
            extra={'extra_fields': {
                'pdf_path': pdf_path,
                'output_path': output_path,
                'force_ai': force_ai,
                'event_type': 'conversion_start'
            }}
        )

        try:
            text = self.extract_text_from_pdf(pdf_path)
            log_file_operation(logger, 'pdf_text_extraction', pdf_path, True)
        except Exception as e:
            log_file_operation(logger, 'pdf_text_extraction', pdf_path, False, str(e))
            raise

        invoice_data = None
        parsing_method = None

        # Try rule-based parsing first (unless force_ai is True)
        if not force_ai:
            invoice_data, success = self.parse_invoice_with_rules(text)
            if success:
                parsing_method = "rule-based"
            else:
                logger.warning(
                    "Rule-based parsing failed, falling back to Claude AI",
                    extra={'extra_fields': {'event_type': 'fallback_to_ai'}}
                )
                invoice_data = None

        # Fall back to Claude AI if rule-based parsing failed
        if invoice_data is None:
            if not self.anthropic_client:
                logger.error(
                    "Claude AI not configured and rule-based parsing failed",
                    extra={'extra_fields': {'event_type': 'parsing_failed'}}
                )
                raise ValueError(
                    "Rule-based parsing failed and Claude AI is not configured. "
                    "Set ANTHROPIC_API_KEY environment variable or pass api_key to constructor."
                )

            logger.info(
                "Using Claude AI for intelligent parsing",
                extra={'extra_fields': {'parsing_method': 'claude_ai'}}
            )

            try:
                invoice_data = self.parse_invoice_from_text(text)
                parsing_method = "Claude AI"
                log_api_call(logger, '/messages', 'POST', 200, None)
            except Exception as e:
                logger.error(
                    f"Claude AI parsing failed: {str(e)}",
                    extra={'extra_fields': {'event_type': 'ai_parsing_failed'}},
                    exc_info=True
                )
                raise

        logger.info(
            f"Successfully parsed invoice",
            extra={'extra_fields': {
                'parsing_method': parsing_method,
                'invoice_number': invoice_data.get('number'),
                'event_type': 'parsing_complete'
            }}
        )

        try:
            xml_content = self.convert_to_ksef_xml(invoice_data)
            logger.info(
                "Successfully converted to KSeF XML format",
                extra={'extra_fields': {'event_type': 'xml_conversion_complete'}}
            )
        except Exception as e:
            logger.error(
                f"XML conversion failed: {str(e)}",
                extra={'extra_fields': {'event_type': 'xml_conversion_failed'}},
                exc_info=True
            )
            raise

        # Validate XML against XSD schema
        try:
            is_valid, error_message = self.validate_against_xsd(xml_content)
            if not is_valid:
                logger.error(
                    f"XSD validation failed: {error_message}",
                    extra={'extra_fields': {'event_type': 'xsd_validation_failed'}}
                )
                raise ValueError(f"Generated XML does not conform to KSeF schema:\n{error_message}")
        except Exception as e:
            logger.error(
                f"Validation error: {str(e)}",
                extra={'extra_fields': {'event_type': 'validation_error'}},
                exc_info=True
            )
            raise

        if output_path:
            try:
                with open(output_path, 'w', encoding='utf-8') as f:
                    f.write(xml_content)
                log_file_operation(logger, 'xml_save', output_path, True)
            except Exception as e:
                log_file_operation(logger, 'xml_save', output_path, False, str(e))
                raise

        logger.info(
            "PDF to KSeF XML conversion completed successfully",
            extra={'extra_fields': {
                'parsing_method': parsing_method,
                'invoice_number': invoice_data.get('number'),
                'schema_validated': True,
                'event_type': 'conversion_complete'
            }}
        )

        return xml_content


# ============================================================================
# CLI Interface
# ============================================================================

def main():
    """Command-line interface for PDF to KSeF XML conversion."""
    import argparse
    import sys

    parser = argparse.ArgumentParser(
        description='Convert PDF invoice to KSeF XML format',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Examples:
  # Convert PDF to XML (tries rule-based first, AI fallback)
  python ksef_xml_converter.py invoice.pdf

  # Convert with custom output path
  python ksef_xml_converter.py invoice.pdf -o output/invoice.xml

  # Force use of Claude AI (skip rule-based parsing)
  python ksef_xml_converter.py invoice.pdf --force-ai

  # Use custom API key
  python ksef_xml_converter.py invoice.pdf --api-key sk-ant-xxx

Parsing Methods:
  1. Rule-based (default): Fast regex parsing for standard Polish invoices
  2. Claude AI (fallback): Intelligent parsing for complex formats

  The script automatically falls back to Claude AI if rule-based parsing fails.
  API key is only required when Claude AI is needed.
        '''
    )
    parser.add_argument(
        'pdf_path',
        type=str,
        help='Path to the PDF invoice file'
    )
    parser.add_argument(
        '-o', '--output',
        type=str,
        help='Output path for KSeF XML file (default: same as PDF with .xml extension)'
    )
    parser.add_argument(
        '--api-key',
        type=str,
        help='Anthropic API key (default: uses ANTHROPIC_API_KEY env variable)'
    )
    parser.add_argument(
        '--force-ai',
        action='store_true',
        help='Force use of Claude AI, skip rule-based parsing'
    )

    args = parser.parse_args()

    # Validate input file
    from pathlib import Path
    pdf_path = Path(args.pdf_path)
    if not pdf_path.exists():
        print(f"Error: PDF file not found: {pdf_path}", file=sys.stderr)
        sys.exit(1)

    # Determine output path
    if args.output:
        output_path = Path(args.output)
    else:
        output_path = pdf_path.with_suffix('.xml')

    # Create output directory if needed
    output_path.parent.mkdir(parents=True, exist_ok=True)

    # Initialize converter
    try:
        converter = KSeFXMLConverter(anthropic_api_key=args.api_key)
    except Exception as e:
        print(f"Error initializing converter: {e}", file=sys.stderr)
        sys.exit(1)

    # Convert PDF to KSeF XML
    try:
        print(f"\n{'='*60}")
        print(f"PDF to KSeF XML Converter")
        print(f"{'='*60}")
        print(f"Input:  {pdf_path}")
        print(f"Output: {output_path}")
        print(f"{'='*60}\n")

        xml_content = converter.convert_pdf_to_ksef_xml(
            str(pdf_path),
            str(output_path),
            force_ai=args.force_ai
        )

        print(f"\n{'='*60}")
        print(f"✓ Successfully converted PDF to KSeF XML!")
        print(f"✓ Output saved to: {output_path}")
        print(f"{'='*60}\n")

    except FileNotFoundError as e:
        print(f"\nError: {e}", file=sys.stderr)
        sys.exit(1)
    except ValueError as e:
        print(f"\nError: {e}", file=sys.stderr)
        print("\nHint: Set ANTHROPIC_API_KEY environment variable or use --api-key option.")
        sys.exit(1)
    except Exception as e:
        print(f"\nError converting PDF: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
