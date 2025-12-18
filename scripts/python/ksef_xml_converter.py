"""
KSeF XML Converter

Converts invoice data to Polish KSeF (Krajowy System e-Faktur) XML format.
"""

from datetime import datetime
from typing import Dict
from xml.etree import ElementTree as ET
from xml.dom import minidom


class KSeFXMLConverter:
    """Convert invoice data to KSeF XML format."""

    def __init__(self):
        """Initialize converter."""
        self.namespace = "http://crd.gov.pl/wzor/2023/06/29/12648/"
        self.namespace_xsi = "http://www.w3.org/2001/XMLSchema-instance"
        self.namespace_xsd = "http://www.w3.org/2001/XMLSchema"

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

        kod_formularza = ET.SubElement(
            naglowek,
            f"{{{self.namespace}}}KodFormularza",
            attrib={
                "kodSystemowy": "FA (2)",
                "wersjaSchemy": "1-0E"
            }
        )
        kod_formularza.text = "FA"

        wariant = ET.SubElement(naglowek, f"{{{self.namespace}}}WariantFormularza")
        wariant.text = "2"

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

    def save_ksef_xml(self, invoice_data: Dict, output_path: str):
        """Save invoice as KSeF XML file."""
        xml_content = self.convert_to_ksef_xml(invoice_data)
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(xml_content)
