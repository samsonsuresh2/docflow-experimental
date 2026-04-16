export function parseStringMultiValueInput(input: string): string[] {
  if (!input.trim()) {
    throw new Error('Enter at least one value.');
  }

  const values: string[] = [];
  let current = '';
  let escaping = false;

  for (const char of input) {
    if (escaping) {
      if (char !== ',' && char !== '\\') {
        throw new Error('Use \\, to escape commas in string lists.');
      }
      current += char;
      escaping = false;
      continue;
    }

    if (char === '\\') {
      escaping = true;
      continue;
    }

    if (char === ',') {
      pushToken(values, current);
      current = '';
      continue;
    }

    current += char;
  }

  if (escaping) {
    throw new Error('String list cannot end with a dangling escape.');
  }

  pushToken(values, current);
  return values;
}

export function parseNumberMultiValueInput(input: string): string[] {
  if (!input.trim()) {
    throw new Error('Enter at least one value.');
  }

  const values = input.split(',').map((part) => part.trim());
  if (values.some((value) => value.length === 0)) {
    throw new Error('Blank values are not allowed.');
  }
  if (values.some((value) => !/^-?\d+(\.\d+)?$/.test(value))) {
    throw new Error('Enter only comma-separated numeric values.');
  }
  return values;
}

function pushToken(values: string[], token: string): void {
  const trimmed = token.trim();
  if (!trimmed) {
    throw new Error('Blank values are not allowed.');
  }
  values.push(trimmed);
}
