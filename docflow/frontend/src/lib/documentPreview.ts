import { ZipArchive } from './zip';

export interface DocxHtmlResult {
  html: string;
}

export interface XlsxHtmlResult {
  sheetName: string;
  html: string;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export async function convertDocxToHtml(arrayBuffer: ArrayBuffer): Promise<DocxHtmlResult> {
  const archive = new ZipArchive(arrayBuffer);
  const documentXml = await archive.getText('word/document.xml');
  if (!documentXml) {
    throw new Error('DOCX payload did not include word/document.xml.');
  }

  const parser = new DOMParser();
  const xml = parser.parseFromString(documentXml, 'application/xml');
  if (xml.getElementsByTagName('parsererror').length > 0) {
    throw new Error('Unable to parse DOCX XML document.');
  }

  const body = xml.getElementsByTagName('w:body')[0];
  const parts: string[] = [];

  const elements = body ? Array.from(body.childNodes) : [];

  elements.forEach((node) => {
    if (node.nodeType !== Node.ELEMENT_NODE) {
      return;
    }
    const element = node as Element;
    if (element.tagName === 'w:p') {
      parts.push(renderParagraph(element));
      return;
    }
    if (element.tagName === 'w:tbl') {
      const tableHtml = renderTable(element);
      if (tableHtml) {
        parts.push(tableHtml);
      }
    }
  });

  if (parts.length === 0) {
    parts.push('<p class="text-sm text-slate-600">(Document contained no visible text)</p>');
  }

  return { html: parts.join('') };
}

function renderParagraph(paragraph: Element, compact = false): string {
  const runs = Array.from(paragraph.getElementsByTagName('w:r'));
  const runPieces: string[] = [];

  runs.forEach((run) => {
    const texts = Array.from(run.getElementsByTagName('w:t'));
    if (texts.length > 0) {
      const combined = texts.map((node) => node.textContent ?? '').join('');
      if (combined) {
        let content = escapeHtml(combined);
        const properties = run.getElementsByTagName('w:rPr')[0];
        if (properties) {
          if (properties.getElementsByTagName('w:b').length > 0) {
            content = `<strong>${content}</strong>`;
          }
          if (properties.getElementsByTagName('w:i').length > 0) {
            content = `<em>${content}</em>`;
          }
          if (properties.getElementsByTagName('w:u').length > 0) {
            content = `<span style="text-decoration: underline;">${content}</span>`;
          }
        }
        runPieces.push(content);
      }
    }
    const breaks = Array.from(run.getElementsByTagName('w:br'));
    if (breaks.length > 0) {
      runPieces.push('<br />');
    }
  });

  const paragraphHtml = runPieces.length > 0 ? runPieces.join('') : '&nbsp;';
  const spacingClass = compact ? 'm-0' : 'mb-2';
  return `<p class="${spacingClass} text-sm text-slate-800">${paragraphHtml}</p>`;
}

function renderTable(table: Element): string {
  const rows = Array.from(table.getElementsByTagName('w:tr'));
  if (rows.length === 0) {
    return '';
  }

  const bodyRows = rows
    .map((row) => {
      const cells = Array.from(row.getElementsByTagName('w:tc'));
      if (cells.length === 0) {
        return '';
      }
      const cellHtml = cells
        .map((cell) => {
          const paragraphs = Array.from(cell.getElementsByTagName('w:p'));
          const content = paragraphs.length > 0
            ? paragraphs.map((paragraph) => renderParagraph(paragraph, true)).join('')
            : '&nbsp;';
          return `<td class="border border-slate-300 px-2 py-1 align-top">${content}</td>`;
        })
        .join('');
      return `<tr>${cellHtml}</tr>`;
    })
    .filter((row) => row.length > 0)
    .join('');

  if (!bodyRows) {
    return '';
  }

  return `<table class="mb-4 w-full border-collapse border border-slate-300 text-left"><tbody>${bodyRows}</tbody></table>`;
}

function columnLabelToIndex(label: string): number {
  let index = 0;
  for (let i = 0; i < label.length; i += 1) {
    index = index * 26 + (label.charCodeAt(i) - 64);
  }
  return index - 1;
}

function parseCellReference(reference: string): { column: number; row: number } {
  const match = reference.match(/([A-Z]+)(\d+)/i);
  if (!match) {
    return { column: 0, row: 0 };
  }
  const columnLabel = match[1].toUpperCase();
  const rowIndex = Number.parseInt(match[2], 10) - 1;
  return { column: columnLabelToIndex(columnLabel), row: Math.max(0, rowIndex) };
}

function extractSharedStrings(xml: Document): string[] {
  const strings = Array.from(xml.getElementsByTagName('si'));
  return strings.map((si) => {
    const textNodes = Array.from(si.getElementsByTagName('t'));
    if (textNodes.length === 0) {
      return '';
    }
    return textNodes.map((node) => node.textContent ?? '').join('');
  });
}

function getCellValue(cell: Element, sharedStrings: string[]): string {
  const type = cell.getAttribute('t');
  if (type === 's') {
    const valueNode = cell.getElementsByTagName('v')[0];
    if (!valueNode) {
      return '';
    }
    const index = Number.parseInt(valueNode.textContent ?? '', 10);
    if (Number.isNaN(index)) {
      return '';
    }
    const sharedValue = sharedStrings[index] ?? '';
    return escapeHtml(sharedValue);
  }

  if (type === 'inlineStr') {
    const inlineTexts = Array.from(cell.getElementsByTagName('t'));
    return escapeHtml(inlineTexts.map((node) => node.textContent ?? '').join(''));
  }

  const valueNode = cell.getElementsByTagName('v')[0];
  const rawValue = valueNode?.textContent ?? '';
  return escapeHtml(rawValue);
}

function buildTableHtml(rows: string[][], sheetName: string): string {
  const header = `<caption class="caption-top mb-2 text-left text-sm font-semibold text-slate-700">${escapeHtml(sheetName)}</caption>`;
  const columnCount = rows.reduce((max, current) => Math.max(max, current.length), 0) || 1;
  const normalizedRows = rows.map((row) => {
    if (row.length === columnCount) {
      return row;
    }
    const extended = [...row];
    while (extended.length < columnCount) {
      extended.push('');
    }
    return extended;
  });

  const tableRows = normalizedRows
    .map((cells) => {
      const safeCells = cells.length > 0 ? cells : [''];
      const columns = safeCells
        .map((cellValue) => `<td class="border border-slate-200 px-2 py-1 text-xs text-slate-700">${cellValue}</td>`)
        .join('');
      return `<tr>${columns}</tr>`;
    })
    .join('');
  return `<table class="w-full border-collapse text-left">${header}<tbody>${tableRows}</tbody></table>`;
}

export async function convertXlsxToHtml(arrayBuffer: ArrayBuffer): Promise<XlsxHtmlResult> {
  const archive = new ZipArchive(arrayBuffer);
  const workbookXml = await archive.getText('xl/workbook.xml');
  if (!workbookXml) {
    throw new Error('XLSX payload did not include workbook definition.');
  }

  const parser = new DOMParser();
  const workbookDoc = parser.parseFromString(workbookXml, 'application/xml');
  if (workbookDoc.getElementsByTagName('parsererror').length > 0) {
    throw new Error('Unable to parse XLSX workbook.');
  }

  const sheetElement = workbookDoc.getElementsByTagName('sheet')[0];
  if (!sheetElement) {
    throw new Error('XLSX workbook did not contain any worksheets.');
  }

  const sheetName = sheetElement.getAttribute('name') ?? 'Sheet1';
  const relationshipId = sheetElement.getAttribute('r:id');
  if (!relationshipId) {
    throw new Error('Worksheet relationship identifier missing.');
  }

  const relationshipsXml = await archive.getText('xl/_rels/workbook.xml.rels');
  if (!relationshipsXml) {
    throw new Error('XLSX workbook relationships missing.');
  }
  const relationshipsDoc = parser.parseFromString(relationshipsXml, 'application/xml');
  if (relationshipsDoc.getElementsByTagName('parsererror').length > 0) {
    throw new Error('Unable to parse XLSX relationships.');
  }
  const relationshipElements = Array.from(relationshipsDoc.getElementsByTagName('Relationship'));
  const targetRelationship = relationshipElements.find((element) => element.getAttribute('Id') === relationshipId);
  if (!targetRelationship) {
    throw new Error('Worksheet target relationship missing.');
  }

  const target = targetRelationship.getAttribute('Target');
  if (!target) {
    throw new Error('Worksheet target path missing.');
  }

  const normalizedTarget = target.startsWith('/') ? `xl${target}` : `xl/${target}`;
  const sheetXml = await archive.getText(normalizedTarget);
  if (!sheetXml) {
    throw new Error('Worksheet XML not found.');
  }

  const sheetDoc = parser.parseFromString(sheetXml, 'application/xml');
  if (sheetDoc.getElementsByTagName('parsererror').length > 0) {
    throw new Error('Unable to parse worksheet XML.');
  }

  const sharedStringsXml = await archive.getText('xl/sharedStrings.xml');
  const sharedStrings = sharedStringsXml
    ? extractSharedStrings(parser.parseFromString(sharedStringsXml, 'application/xml'))
    : [];

  const rows: string[][] = [];
  const rowElements = Array.from(sheetDoc.getElementsByTagName('row'));
  let maxColumns = 0;

  rowElements.forEach((rowElement) => {
    const rowCells: string[] = [];
    const cellElements = Array.from(rowElement.getElementsByTagName('c'));

    cellElements.forEach((cellElement) => {
      const reference = cellElement.getAttribute('r');
      if (!reference) {
        return;
      }
      const { column } = parseCellReference(reference);
      const value = getCellValue(cellElement, sharedStrings);
      rowCells[column] = value;
      maxColumns = Math.max(maxColumns, column + 1);
    });

    if (rowCells.length === 0) {
      rows.push([]);
    } else {
      const normalizedRow = Array.from({ length: maxColumns }, (_, index) => rowCells[index] ?? '');
      rows.push(normalizedRow);
    }
  });

  if (rows.length === 0) {
    rows.push([]);
  }

  const tableHtml = buildTableHtml(rows, sheetName);
  return { sheetName, html: tableHtml };
}
