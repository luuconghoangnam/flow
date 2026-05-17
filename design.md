# Drops Design System: Cyber-Industrial Aesthetic

This guide summarizes the design language used in the **Drops** app. It follows a "Cyber-Industrial" or "Brutalist Tech" philosophy: high contrast, sharp edges, monospaced typography, and functional minimalism.

---

## 1. Core Philosophy
- **Sharp & Square**: No rounded corners (`BorderRadius.zero`).
- **Chamfered Accents**: Use 45-degree corner cuts (chamfers) instead of rounded corners for primary buttons.
- **Terminal Aesthetic**: Use monospaced fonts to evoke a "technical" or "hacker" feel.
- **High Contrast**: Deep blacks, pure whites, and intense accent colors.

---

## 2. Color Palette

### Dark Mode (Primary)
- **Background**: `#0A0A0A` (Almost black)
- **Surface**: `#1E1E1E` (Dark gray)
- **Border**: `#3A3A3A`
- **Text Primary**: `#E5E7EB` (Off-white)
- **Text Muted**: `#6B7280` (Gray)

### Light Mode
- **Background**: `#FFFFFF`
- **Surface**: `#FFFFFF`
- **Border**: `#E5E7EB`
- **Text Primary**: `#1E1E1E`

### Accents (The "Cyber" Energy)
- **Primary (Orange)**: `#E64A00` (Main action color)
- **Success (Green)**: `#22C55E`
- **Warning (Yellow)**: `#EAB308`
- **Connection Types**:
    - Direct: Green (`#22C55E`)
    - STUN/Relay: Blue (`#3B82F6`)
    - TURN: Orange-Red (`#FF6B35`)

---

## 3. Typography
- **Headings & Branding**: `Orbitron` (Wide, futuristic, high letter-spacing).
- **Body & Data**: `JetBrains Mono` (Clear, technical, monospaced).
- **Settings**: 
    - Title: `letterSpacing: 4`, `fontWeight: 700`.
    - Body: `fontSize: 12-14`, `height: 1.5`.

---

## 4. Custom Shapes & Geometry

### The Chamfer (Corner Cut)
Instead of `RoundedRectangleBorder`, use a custom path where the **top-left corner** is cut at a 45-degree angle.
```dart
Path cyberChamferTopLeftPath(Rect r, double cut) {
  return Path()
    ..moveTo(r.left + cut, r.top)
    ..lineTo(r.right, r.top)
    ..lineTo(r.right, r.bottom)
    ..lineTo(r.left, r.bottom)
    ..lineTo(r.left, r.top + cut)
    ..close();
}
```

---

## 5. UI Components

### 5.1 Buttons (Cyber3D)
- **Style**: No drop shadows. Instead, use a **Single Outline** that lerps between a highlight and shadow color to simulate depth.
- **Interaction**: 
    - Scale down to `0.95` on press.
    - Opacity change to `0.88`.
    - Haptic feedback (`HapticFeedback.lightImpact`).

### 5.2 Input Fields
- **Background**: Solid surface color.
- **Border**: `OutlineInputBorder` with `BorderRadius.zero`.
- **Focus**: Thick primary color border (`1.5` width).

### 5.3 Backgrounds (The Grid)
Use a subtle radial glow combined with a thin grid line (`0.5` width, `24.0` step) to create a blueprint-like depth.

---

## 6. Implementation Checklist
- [ ] Is `useMaterial3` enabled?
- [ ] Are all `BorderRadius` set to `zero`?
- [ ] Is the font `JetBrains Mono` for all data?
- [ ] Does the screen use a `CyberGridBackground`?
- [ ] Are primary actions using the **Chamfered** button style?

---

## 7. Example Widget Pattern
```dart
CyberButton(
  onPressed: () => print("Action"),
  label: "INITIALIZE",
  icon: Icons.power_settings_new,
  color: CyberColors.primary, // Orange accent
)
```
