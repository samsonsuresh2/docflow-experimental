import { useState } from 'react';

type Field = {
  name: string;
  label: string;
  type: 'text' | 'number' | 'date';
};

type Props = {
  fields: Field[];
  onSubmit: (values: Record<string, string>) => void;
};

export default function DynamicForm({ fields, onSubmit }: Props) {
  const [values, setValues] = useState<Record<string, string>>({});

  const handleChange = (name: string, value: string) => {
    setValues((prev) => ({ ...prev, [name]: value }));
  };

  return (
    <form
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit(values);
      }}
    >
      {fields.map((field) => (
        <label key={field.name} className="block">
          <span className="text-sm font-medium">{field.label}</span>
          <input
            className="mt-1 w-full rounded border border-gray-300 p-2"
            type={field.type}
            onChange={(event) => handleChange(field.name, event.target.value)}
          />
        </label>
      ))}
      <button type="submit" className="rounded bg-blue-600 px-4 py-2 text-white">
        Submit
      </button>
    </form>
  );
}
