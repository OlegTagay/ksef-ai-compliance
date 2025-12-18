"""
PDF Invoice Generator

Generates Polish invoice (faktura) in PDF format with proper layout.
"""

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.enums import TA_LEFT, TA_RIGHT, TA_CENTER
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from typing import Dict, List
import os

# Import logging configuration
from logging_config import get_logger, log_file_operation

# Initialize logger
logger = get_logger(__name__)


class PDFInvoiceGenerator:
    """Generate PDF invoice with Polish layout."""

    def __init__(self):
        """Initialize PDF generator."""
        # Register Unicode fonts for Polish characters
        self._register_fonts()
        self.styles = getSampleStyleSheet()
        self._setup_styles()

    def _register_fonts(self):
        """Register fonts with Unicode support for Polish characters."""
        # Try to find and register system TTF fonts
        font_paths = [
            # macOS
            '/System/Library/Fonts/Supplemental/Arial.ttf',
            '/System/Library/Fonts/Supplemental/Arial Bold.ttf',
            # Linux
            '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
            '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
            # Common locations
            '/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf',
            '/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf',
        ]

        font_registered = False

        # Try Arial first (macOS)
        try:
            if os.path.exists('/System/Library/Fonts/Supplemental/Arial.ttf'):
                pdfmetrics.registerFont(TTFont('CustomFont', '/System/Library/Fonts/Supplemental/Arial.ttf'))
                pdfmetrics.registerFont(TTFont('CustomFont-Bold', '/System/Library/Fonts/Supplemental/Arial Bold.ttf'))
                self.font_name = 'CustomFont'
                self.font_name_bold = 'CustomFont-Bold'
                font_registered = True
        except Exception as e:
            pass

        # Try DejaVu if Arial not found
        if not font_registered:
            try:
                if os.path.exists('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
                    pdfmetrics.registerFont(TTFont('CustomFont', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'))
                    pdfmetrics.registerFont(TTFont('CustomFont-Bold', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'))
                    self.font_name = 'CustomFont'
                    self.font_name_bold = 'CustomFont-Bold'
                    font_registered = True
            except Exception as e:
                pass

        # Fallback to Helvetica (won't show Polish characters correctly)
        if not font_registered:
            logger.warning("Could not load Unicode fonts - Polish characters may not display correctly", extra={'extra_fields': {'event_type': 'font_fallback'}})
            self.font_name = 'Helvetica'
            self.font_name_bold = 'Helvetica-Bold'

    def _setup_styles(self):
        """Setup custom styles for the invoice."""
        # Header style
        self.styles.add(ParagraphStyle(
            name='InvoiceHeader',
            parent=self.styles['Heading1'],
            fontSize=16,
            textColor=colors.HexColor('#333333'),
            spaceAfter=6,
            alignment=TA_LEFT
        ))

        # Info style
        self.styles.add(ParagraphStyle(
            name='InvoiceInfo',
            parent=self.styles['Normal'],
            fontSize=10,
            spaceAfter=3,
            alignment=TA_LEFT
        ))

        # Summary style
        self.styles.add(ParagraphStyle(
            name='Summary',
            parent=self.styles['Normal'],
            fontSize=11,
            textColor=colors.HexColor('#000000'),
            alignment=TA_RIGHT,
            spaceAfter=2
        ))

        # Address style
        self.styles.add(ParagraphStyle(
            name='Address',
            parent=self.styles['Normal'],
            fontSize=9,
            spaceAfter=2,
            alignment=TA_LEFT
        ))

    def _number_to_words_pl(self, amount: float) -> str:
        """Convert number to Polish words."""
        zloty = int(amount)
        grosze = int(round((amount - zloty) * 100))

        if zloty == 0 and grosze == 0:
            return "zero złotych zero groszy"

        ones = ['', 'jeden', 'dwa', 'trzy', 'cztery', 'pięć', 'sześć', 'siedem', 'osiem', 'dziewięć']
        teens = ['dziesięć', 'jedenaście', 'dwanaście', 'trzynaście', 'czternaście',
                 'piętnaście', 'szesnaście', 'siedemnaście', 'osiemnaście', 'dziewiętnaście']
        tens = ['', '', 'dwadzieścia', 'trzydzieści', 'czterdzieści',
                'pięćdziesiąt', 'sześćdziesiąt', 'siedemdziesiąt', 'osiemdziesiąt', 'dziewięćdziesiąt']
        hundreds = ['', 'sto', 'dwieście', 'trzysta', 'czterysta',
                    'pięćset', 'sześćset', 'siedemset', 'osiemset', 'dziewięćset']

        def convert_group(num):
            """Convert a group of three digits to words."""
            if num == 0:
                return ''

            result = []
            h = num // 100
            t = (num % 100) // 10
            o = num % 10

            if h > 0:
                result.append(hundreds[h])

            if t == 1:
                result.append(teens[o])
            else:
                if t > 0:
                    result.append(tens[t])
                if o > 0:
                    result.append(ones[o])

            return ' '.join(result)

        def zloty_form(num):
            """Return correct form of złoty."""
            if num == 1:
                return 'złoty'
            elif num % 10 in [2, 3, 4] and (num % 100 < 10 or num % 100 >= 20):
                return 'złote'
            else:
                return 'złotych'

        def grosze_form(num):
            """Return correct form of grosz."""
            if num == 1:
                return 'grosz'
            elif num % 10 in [2, 3, 4] and (num % 100 < 10 or num % 100 >= 20):
                return 'grosze'
            else:
                return 'groszy'

        # Convert złoty
        words = []

        if zloty == 0:
            zloty_words = 'zero'
        elif zloty < 1000:
            zloty_words = convert_group(zloty)
        elif zloty < 1000000:
            thousands = zloty // 1000
            remainder = zloty % 1000

            thousands_word = convert_group(thousands)
            if thousands == 1:
                thousands_word = 'tysiąc'
            elif thousands % 10 in [2, 3, 4] and (thousands % 100 < 10 or thousands % 100 >= 20):
                thousands_word += ' tysiące'
            else:
                thousands_word += ' tysięcy'

            words.append(thousands_word)
            if remainder > 0:
                words.append(convert_group(remainder))

            zloty_words = ' '.join(words)
        else:
            # For very large numbers, just use digits
            zloty_words = str(zloty)

        result = f"{zloty_words} {zloty_form(zloty)}"

        if grosze > 0:
            grosze_words = convert_group(grosze) if grosze > 0 else 'zero'
            result += f" {grosze_words} {grosze_form(grosze)}"

        return result

    def generate_pdf(self, invoice_data: Dict, output_path: str):
        """Generate PDF invoice from invoice data."""
        invoice_number = invoice_data.get('number', 'unknown')
        logger.info("Starting PDF generation", extra={'extra_fields': {
            'invoice_number': invoice_number,
            'output_path': output_path,
            'event_type': 'pdf_generation_start'
        }})

        try:
            doc = SimpleDocTemplate(
                output_path,
                pagesize=A4,
                rightMargin=2*cm,
                leftMargin=2*cm,
                topMargin=2*cm,
                bottomMargin=2*cm
            )

            story = []

            # Top summary section
            story.append(self._create_top_summary(invoice_data))
            story.append(Spacer(1, 0.5*cm))

            # Invoice header
            story.append(self._create_invoice_header(invoice_data))
            story.append(Spacer(1, 0.3*cm))

            # Invoice details
            story.append(self._create_invoice_details(invoice_data))
            story.append(Spacer(1, 0.5*cm))

            # Seller and Buyer section
            story.append(self._create_seller_buyer_section(invoice_data))
            story.append(Spacer(1, 0.5*cm))

            # Items table
            story.append(self._create_items_table(invoice_data))
            story.append(Spacer(1, 0.5*cm))

            # Summary section
            story.append(self._create_summary_section(invoice_data))
            story.append(Spacer(1, 0.5*cm))

            # Payment info
            story.append(self._create_payment_info(invoice_data))
            story.append(Spacer(1, 0.5*cm))

            # Footer
            story.append(self._create_footer(invoice_data))

            doc.build(story)

            log_file_operation(logger, 'pdf_generation', output_path, True)
            logger.info("PDF generated successfully", extra={'extra_fields': {
                'invoice_number': invoice_number,
                'output_path': output_path,
                'event_type': 'pdf_generation_complete'
            }})

        except Exception as e:
            log_file_operation(logger, 'pdf_generation', output_path, False, str(e))
            logger.error("PDF generation failed", extra={'extra_fields': {
                'invoice_number': invoice_number,
                'output_path': output_path,
                'event_type': 'pdf_generation_failed',
                'error': str(e)
            }}, exc_info=True)
            raise

    def _create_top_summary(self, data: Dict) -> Table:
        """Create top summary section with totals."""
        summary_data = [
            [f"Wartość netto {data['price_net']} {data['currency']}"],
            [f"Wartość VAT {data['price_tax']} {data['currency']}"],
            [f"Wartość brutto {data['price_gross']} {data['currency']}"]
        ]

        table = Table(summary_data, colWidths=[17*cm])
        table.setStyle(TableStyle([
            ('ALIGN', (0, 0), (-1, -1), 'RIGHT'),
            ('FONTNAME', (0, 0), (-1, -1), self.font_name),
            ('FONTSIZE', (0, 0), (-1, -1), 10),
            ('TEXTCOLOR', (0, 0), (-1, -1), colors.HexColor('#666666')),
            ('TOPPADDING', (0, 0), (-1, -1), 2),
            ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
        ]))

        return table

    def _create_invoice_header(self, data: Dict) -> Paragraph:
        """Create invoice header with number."""
        text = f"<b>Faktura numer {data['number']}</b>"
        return Paragraph(text, self.styles['InvoiceHeader'])

    def _create_invoice_details(self, data: Dict) -> Table:
        """Create invoice details section."""
        payment_type_pl = {
            'cash': 'Gotówka',
            'transfer': 'Przelew',
            'card': 'Karta'
        }.get(data.get('payment_type', 'transfer'), 'Przelew')

        details_data = [
            [f"Data wystawienia: {data['issue_date']}"],
            [f"Data sprzedaży: {data['sell_date']}"],
            [f"Termin płatności: {data['payment_to']}"],
            [f"Płatność: {payment_type_pl}"]
        ]

        table = Table(details_data, colWidths=[17*cm])
        table.setStyle(TableStyle([
            ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
            ('FONTNAME', (0, 0), (-1, -1), self.font_name),
            ('FONTSIZE', (0, 0), (-1, -1), 10),
            ('TOPPADDING', (0, 0), (-1, -1), 2),
            ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
        ]))

        return table

    def _create_seller_buyer_section(self, data: Dict) -> Table:
        """Create seller and buyer section."""
        seller_text = f"""<b>Sprzedawca</b><br/>
<b>{data['seller_name']}</b><br/>
{data['seller_street']}<br/>
{data['seller_post_code']} {data['seller_city']}<br/>
NIP {data['seller_tax_no']}<br/>
Konto: {data['seller_bank_account']}<br/>
Bank: {data['seller_bank']}"""

        buyer_text = f"""<b>Nabywca</b><br/>
<b>{data['buyer_name']}</b><br/>
{data['buyer_street']}<br/>
{data['buyer_post_code']} {data['buyer_city']}<br/>
NIP {data['buyer_tax_no']}"""

        seller_para = Paragraph(seller_text, self.styles['Address'])
        buyer_para = Paragraph(buyer_text, self.styles['Address'])

        section_data = [[seller_para, buyer_para]]

        table = Table(section_data, colWidths=[8.5*cm, 8.5*cm])
        table.setStyle(TableStyle([
            ('VALIGN', (0, 0), (-1, -1), 'TOP'),
            ('LEFTPADDING', (0, 0), (-1, -1), 0),
            ('RIGHTPADDING', (0, 0), (-1, -1), 0),
        ]))

        return table

    def _create_items_table(self, data: Dict) -> Table:
        """Create items table with positions."""
        # Header
        table_data = [[
            'LP',
            'Nazwa towaru / usługi',
            'Ilość',
            'Cena netto',
            'Wartość netto',
            'VAT %',
            'Wartość VAT',
            'Wartość brutto'
        ]]

        # Items
        for idx, position in enumerate(data['positions'], 1):
            quantity_str = f"{float(position['quantity']):.0f} {position['quantity_unit']}"
            table_data.append([
                str(idx),
                position['name'],
                quantity_str,
                position['price_net'],
                position['total_price_net'],
                position['tax'],
                position['total_price_tax'],
                position['total_price_gross']
            ])

        # Summary row "W tym"
        table_data.append([
            '',
            '',
            '',
            'W tym',
            data['price_net'],
            data['positions'][0]['tax'],
            data['price_tax'],
            data['price_gross']
        ])

        # Total row
        table_data.append([
            '',
            '',
            '',
            'Razem',
            data['price_net'],
            '',
            data['price_tax'],
            data['price_gross']
        ])

        # Column widths
        col_widths = [1*cm, 5*cm, 2*cm, 2*cm, 2.5*cm, 1.5*cm, 2*cm, 2*cm]

        table = Table(table_data, colWidths=col_widths)
        table.setStyle(TableStyle([
            # Header style
            ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#E0E0E0')),
            ('TEXTCOLOR', (0, 0), (-1, 0), colors.black),
            ('ALIGN', (0, 0), (-1, 0), 'CENTER'),
            ('FONTNAME', (0, 0), (-1, 0), self.font_name_bold),
            ('FONTSIZE', (0, 0), (-1, 0), 8),
            ('BOTTOMPADDING', (0, 0), (-1, 0), 8),
            ('TOPPADDING', (0, 0), (-1, 0), 8),

            # Data rows
            ('FONTNAME', (0, 1), (-1, -3), self.font_name),
            ('FONTSIZE', (0, 1), (-1, -3), 9),
            ('ALIGN', (0, 1), (0, -1), 'CENTER'),
            ('ALIGN', (2, 1), (-1, -1), 'RIGHT'),
            ('ALIGN', (1, 1), (1, -1), 'LEFT'),

            # Summary rows
            ('FONTNAME', (0, -2), (-1, -1), self.font_name_bold),
            ('FONTSIZE', (0, -2), (-1, -1), 9),
            ('ALIGN', (3, -2), (3, -1), 'RIGHT'),

            # Grid
            ('GRID', (0, 0), (-1, -1), 0.5, colors.grey),
            ('LINEBELOW', (0, 0), (-1, 0), 2, colors.black),

            # Padding
            ('TOPPADDING', (0, 1), (-1, -1), 5),
            ('BOTTOMPADDING', (0, 1), (-1, -1), 5),
            ('LEFTPADDING', (0, 0), (-1, -1), 5),
            ('RIGHTPADDING', (0, 0), (-1, -1), 5),
        ]))

        return table

    def _create_summary_section(self, data: Dict) -> Table:
        """Create summary section on the right."""
        summary_data = [
            ['', f"Wartość netto", f"{data['price_net']} {data['currency']}"],
            ['', f"Wartość VAT", f"{data['price_tax']} {data['currency']}"],
            ['', f"Wartość brutto", f"{data['price_gross']} {data['currency']}"]
        ]

        table = Table(summary_data, colWidths=[10*cm, 4*cm, 3*cm])
        table.setStyle(TableStyle([
            ('ALIGN', (1, 0), (1, -1), 'RIGHT'),
            ('ALIGN', (2, 0), (2, -1), 'RIGHT'),
            ('FONTNAME', (1, 0), (-1, -1), self.font_name_bold),
            ('FONTSIZE', (1, 0), (-1, -1), 10),
            ('TOPPADDING', (0, 0), (-1, -1), 2),
            ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
        ]))

        return table

    def _create_payment_info(self, data: Dict) -> Table:
        """Create payment information section."""
        paid = float(data.get('paid', data['price_gross']))
        gross = float(data['price_gross'])
        to_pay = max(0, gross - paid)

        amount_words = self._number_to_words_pl(gross)

        payment_data = [
            ['Kwota opłacona', f"{paid:.2f} {data['currency']}"],
            ['Do zapłaty', f"{to_pay:.2f} {data['currency']}"],
            ['Słownie:', amount_words]
        ]

        table = Table(payment_data, colWidths=[4*cm, 13*cm])
        table.setStyle(TableStyle([
            ('FONTNAME', (0, 0), (0, -1), self.font_name_bold),
            ('FONTNAME', (1, 0), (1, -1), self.font_name),
            ('FONTSIZE', (0, 0), (-1, -1), 10),
            ('ALIGN', (0, 0), (0, -1), 'LEFT'),
            ('ALIGN', (1, 0), (1, -1), 'LEFT'),
            ('TOPPADDING', (0, 0), (-1, -1), 3),
            ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
        ]))

        return table

    def _create_footer(self, data: Dict) -> Table:
        """Create footer with issuer name."""
        footer_data = [
            ['', 'Imię i nazwisko wystawcy'],
            ['', data.get('seller_person', '')]
        ]

        table = Table(footer_data, colWidths=[10*cm, 7*cm])
        table.setStyle(TableStyle([
            ('ALIGN', (1, 0), (1, -1), 'CENTER'),
            ('FONTNAME', (1, 0), (1, 0), self.font_name),
            ('FONTNAME', (1, 1), (1, 1), self.font_name_bold),
            ('FONTSIZE', (1, 0), (-1, -1), 9),
            ('TOPPADDING', (0, 0), (-1, -1), 3),
            ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
        ]))

        return table
