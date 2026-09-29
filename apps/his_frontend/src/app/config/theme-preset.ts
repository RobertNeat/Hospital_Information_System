import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * HIS theme preset, built on PrimeNG's Aura preset.
 *
 * Brand:
 * - Primary accent: #1e90ff (dodger blue), written out explicitly because
 *   @primeuix/themes 3.0.1 does not export a `palette()` helper that derives
 *   a full 50-950 scale from a single brand color.
 * - Surface: Tailwind "slate", so slate-800 (#1e293b) reads as the app's navy chrome.
 *
 * Semantic severity colors (success / warn / danger / info):
 * Verified against `@primeuix/themes` 3.0.1's `BaseTokenSections.Semantic` type
 * (types/base/index.d.ts): the semantic token tree has NO `success`/`warn`/`danger`/
 * `info` keys at all (only `primary`, `surface`, `highlight`, `text`, `formField`, ...).
 * Aura's own per-component presets (p-tag, p-message, p-button, ...) instead reference
 * the primitive palettes `green`, `yellow`/`orange`, `red` and `blue`/`sky` directly
 * for their success/warn/danger/info variants (verified in dist/aura/tag and
 * dist/aura/message). So there is nothing to override at the semantic-token level for
 * these colors -- Aura's built-in green/orange/red/sky primitives already are the
 * semantic scale, and they are accessible, sane defaults we keep as-is.
 * `constants/tag-severity.ts` documents the kind -> PrimeNG `severity` mapping so every
 * agent uses PrimeNG's built-in `severity` inputs (`success`/`info`/`warn`/`danger`)
 * instead of ad-hoc hex colors, which satisfies ADDENDUM B.1 in practice.
 *
 * Dark mode:
 * `darkModeSelector: '.app-dark'` is configured where this preset is provided
 * (Phase 0b, in app.config.ts via providePrimeNG). This preset only needs to supply
 * `colorScheme.dark.*` tokens (surface included) so dark mode does not silently fall
 * back to Aura's default zinc surface.
 */
export const HisPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#eef6ff',
      100: '#d9ecff',
      200: '#b3daff',
      300: '#7fc0ff',
      400: '#4aa6ff',
      500: '#1e90ff',
      600: '#1777e0',
      700: '#135fb8',
      800: '#114f95',
      900: '#0f3f73',
      950: '#0a2a4f',
    },
    focusRing: {
      width: '2px',
      style: 'solid',
      color: '{primary.color}',
      offset: '2px',
    },
    colorScheme: {
      light: {
        surface: {
          0: '#ffffff',
          50: '#f8fafc',
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
          600: '#475569',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
          950: '#020617',
        },
      },
      dark: {
        surface: {
          0: '#ffffff',
          50: '#f8fafc',
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
          600: '#475569',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
          950: '#020617',
        },
      },
    },
  },
});
