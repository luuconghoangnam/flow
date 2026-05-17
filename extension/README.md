# Flow Download Manager - Browser Extension

Intercepts download links and sends them to the Flow Download Manager app.

## Install (Chrome/Edge/Brave)

1. Open `chrome://extensions/` (or `edge://extensions/`)
2. Enable **Developer mode** (toggle in top-right)
3. Click **Load unpacked**
4. Select this `extension` folder

## How it works

- When you hover over a download link (zip, exe, mp4, etc.), a small **Flow** button appears
- Click it to send the download to Flow app
- Large file downloads (>1MB) are automatically intercepted and sent to Flow
- The extension communicates with Flow app via `localhost:15151`

## Requirements

- Flow Desktop app must be running
- Browser Integration must be enabled in Flow Settings (enabled by default, port 15151)

## Configuration

Default port: `15151` (matches Flow app default)

To change the port, open the extension's service worker console and run:
```js
chrome.storage.local.set({ port: 15151 });
```
