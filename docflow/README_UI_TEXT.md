# DocFlow UI Text Overrides (ROM / Decoder)

This guide explains how to customize DocFlow UI text safely using the resource bundle system. It is intended for client developers who can edit JSON but are not deeply familiar with i18n.

## A) Purpose (why this exists)
- **Centralized UI text**: All static, user-facing strings live in shared bundles so renaming is consistent and upgrades are conflict-free.
- **Avoid direct TSX edits**: Do **not** change UI text directly in TSX files. Instead, update the client override JSON so your changes survive future product updates.

## B) File locations
- **Base bundle (product-owned)**: `frontend/src/i18n/strings.base.json` (updated by product releases)
- **Client override (client-owned)**: `frontend/src/i18n/strings.client.json` (safe to edit)
- **Helper**: `frontend/src/i18n/index.ts` (where `t()` is implemented)

## C) How override works
- **Runtime merge order**: Base strings load first, then client overrides are merged on top.
- **Override rule**: If a key exists in `strings.client.json`, it **replaces** the base value.
- **Empty override**: If `strings.client.json` is empty, the app behaves exactly like the base bundle.

## D) How to rename something (examples)
Add these entries to `frontend/src/i18n/strings.client.json`:

```json
{
  "module.admin": "Facilitator",
  "module.review": "Verification",
  "action.reject": "Return",
  "module.audit": "Assurance",
  "common.logout": "Sign out"
}
```

**Tip:** Use the exact key from `strings.base.json` to override it.

## E) How to add a new text (when you add a new UI button/label)
1. **No raw string literals in TSX** for user-visible text.
2. Add a new key:
   - **Product extension**: add it to `strings.base.json` (product team change).
   - **Client-only change**: add it to `strings.client.json` (client-owned).
3. Update UI code to use the helper: `t("your.new.key")`.

Example (client-only button):
```json
{
  "action.requestClarification": "Request clarification"
}
```
```tsx
<button>{t("action.requestClarification")}</button>
```

## F) Conflict-free upgrade guidance
- During upgrades, **do not re-apply manual TSX text edits**.
- Keep client changes isolated to `strings.client.json` (and only minimal UI logic changes if unavoidable).
- If a base key name changes in a future release:
  1. Search for the old key in `strings.base.json`.
  2. Update your override to match the new key name.
  3. Re-run the app/build to verify.

## G) Safety notes
- **Don’t delete keys** from `strings.base.json`.
- **Keep JSON valid** (no trailing commas).
- **Avoid duplicate keys** (one key = one meaning).
- **Validate after changes**: run `npm run build` or start the app and click through key screens.

## H) Key naming cheat sheet
- `common.*` — Shared labels and prompts
- `module.*` — Module titles/navigation labels
- `action.*` — Buttons and actions
- `msg.*` — Messages/errors
- `screen.<id>.*` — Screen-specific titles and subtitles
- `table.*` — Table headers and captions
