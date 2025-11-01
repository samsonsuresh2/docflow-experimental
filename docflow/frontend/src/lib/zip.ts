const EOCD_SIGNATURE = 0x06054b50;
const CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
const LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;

interface ZipEntry {
  name: string;
  compressionMethod: number;
  compressedSize: number;
  uncompressedSize: number;
  localHeaderOffset: number;
}

function decodeFileName(data: DataView, offset: number, length: number): string {
  const bytes = new Uint8Array(data.buffer, offset, length);
  const decoder = new TextDecoder('utf-8', { fatal: false });
  return decoder.decode(bytes);
}

function findEndOfCentralDirectory(data: DataView): number {
  for (let position = data.byteLength - 22; position >= 0; position -= 1) {
    if (data.getUint32(position, true) === EOCD_SIGNATURE) {
      return position;
    }
  }
  throw new Error('End of central directory not found.');
}

function parseCentralDirectory(data: DataView): Map<string, ZipEntry> {
  const entries = new Map<string, ZipEntry>();
  const eocdOffset = findEndOfCentralDirectory(data);
  const centralDirectorySize = data.getUint32(eocdOffset + 12, true);
  const centralDirectoryOffset = data.getUint32(eocdOffset + 16, true);
  const totalEntries = data.getUint16(eocdOffset + 10, true);

  let cursor = centralDirectoryOffset;
  for (let index = 0; index < totalEntries; index += 1) {
    const signature = data.getUint32(cursor, true);
    if (signature !== CENTRAL_DIRECTORY_SIGNATURE) {
      throw new Error('Invalid central directory signature.');
    }

    const compressionMethod = data.getUint16(cursor + 10, true);
    const compressedSize = data.getUint32(cursor + 20, true);
    const uncompressedSize = data.getUint32(cursor + 24, true);
    const fileNameLength = data.getUint16(cursor + 28, true);
    const extraFieldLength = data.getUint16(cursor + 30, true);
    const fileCommentLength = data.getUint16(cursor + 32, true);
    const localHeaderOffset = data.getUint32(cursor + 42, true);

    const nameOffset = cursor + 46;
    const name = decodeFileName(data, nameOffset, fileNameLength);

    entries.set(name, {
      name,
      compressionMethod,
      compressedSize,
      uncompressedSize,
      localHeaderOffset,
    });

    cursor += 46 + fileNameLength + extraFieldLength + fileCommentLength;
  }

  const expectedEnd = centralDirectoryOffset + centralDirectorySize;
  if (cursor !== expectedEnd) {
    // Allow graceful handling by not throwing, but in debug builds this could be helpful.
  }

  return entries;
}

async function decompressDeflateRaw(data: Uint8Array): Promise<Uint8Array> {
  if (typeof DecompressionStream === 'undefined') {
    throw new Error('DecompressionStream API is not supported in this environment.');
  }
  const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  const buffer = await new Response(stream).arrayBuffer();
  return new Uint8Array(buffer);
}

async function readEntryData(entry: ZipEntry, data: DataView): Promise<Uint8Array> {
  const headerOffset = entry.localHeaderOffset;
  if (data.getUint32(headerOffset, true) !== LOCAL_FILE_HEADER_SIGNATURE) {
    throw new Error('Invalid local file header signature.');
  }

  const fileNameLength = data.getUint16(headerOffset + 26, true);
  const extraFieldLength = data.getUint16(headerOffset + 28, true);
  const dataOffset = headerOffset + 30 + fileNameLength + extraFieldLength;

  const compressedBytes = new Uint8Array(data.buffer, dataOffset, entry.compressedSize);
  const copied = new Uint8Array(compressedBytes);

  switch (entry.compressionMethod) {
    case 0:
      return copied;
    case 8:
      return decompressDeflateRaw(copied);
    default:
      throw new Error(`Unsupported ZIP compression method: ${entry.compressionMethod}`);
  }
}

export class ZipArchive {
  private readonly data: DataView;

  private readonly entries: Map<string, ZipEntry>;

  constructor(arrayBuffer: ArrayBuffer) {
    this.data = new DataView(arrayBuffer);
    this.entries = parseCentralDirectory(this.data);
  }

  has(path: string): boolean {
    return this.entries.has(path);
  }

  async getEntry(path: string): Promise<Uint8Array | null> {
    const entry = this.entries.get(path);
    if (!entry) {
      return null;
    }
    return readEntryData(entry, this.data);
  }

  async getText(path: string): Promise<string | null> {
    const entryData = await this.getEntry(path);
    if (!entryData) {
      return null;
    }
    const decoder = new TextDecoder('utf-8', { fatal: false });
    return decoder.decode(entryData);
  }
}
