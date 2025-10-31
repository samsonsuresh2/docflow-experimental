# DocFlow Frontend

React 18 + TypeScript single-page application built with Vite and Tailwind CSS.

## Getting Started
```bash
npm install
npm run dev
```

## Testing Notes
- Use the Login page to pick a seeded profile (Maker, Reviewer, Checker, or Admin). The selection is stored in
  `localStorage` and automatically sent as the `X-USER-ID` header for every API request.
- Click **Logout** to clear the stored profile if you need to switch users or exercise unauthenticated behaviour.
