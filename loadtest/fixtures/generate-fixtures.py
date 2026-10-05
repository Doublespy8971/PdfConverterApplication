#!/usr/bin/env python3
"""Generate the tiny valid files used by async-conversion.js."""

from base64 import b64decode
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

FIXTURES = Path(__file__).parent

PNG = b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk"
    "+A8AAQUBAScY42YAAAAASUVORK5CYII="
)

DOCUMENT_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body><w:p><w:r><w:t>Load test fixture</w:t></w:r></w:p>
  <w:sectPr/></w:body>
</w:document>"""

CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    Target="word/document.xml"/>
</Relationships>"""


def main():
    FIXTURES.mkdir(parents=True, exist_ok=True)
    (FIXTURES / "small.png").write_bytes(PNG)
    with ZipFile(FIXTURES / "small.docx", "w", ZIP_DEFLATED) as document:
        document.writestr("[Content_Types].xml", CONTENT_TYPES)
        document.writestr("_rels/.rels", RELS)
        document.writestr("word/document.xml", DOCUMENT_XML)


if __name__ == "__main__":
    main()
