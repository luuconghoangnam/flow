/**
 * Flow Download Manager - Background Service Worker
 *
 * Intercept strategy (in order of priority):
 * 1. onDeterminingFilename — fires BEFORE save dialog, can suggest filename
 *    → We cancel here to prevent save dialog entirely
 * 2. onCreated fallback — catches anything onDeterminingFilename missed
 * 3. Content script — catches link clicks before browser even starts download
 */

const DEFAULT_PORT = 15151;

const DOWNLOAD_EXTENSIONS = new Set([
  'zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'zst',
  'exe', 'msi', 'dmg', 'deb', 'rpm', 'appimage', 'pkg',
  'mp4', 'mkv', 'avi', 'mov', 'wmv', 'flv', 'webm', 'm4v',
  'mp3', 'flac', 'wav', 'aac', 'ogg', 'wma', 'm4a',
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
  'iso', 'img', 'bin', 'torrent',
  'apk', 'ipa', 'crx',
]);

let isEnabled = true;
const urlsToSkipInterception = new Set();


chrome.storage.local.get(['enabled', 'port'], (result) => {
  isEnabled = result.enabled !== false;
});
chrome.storage.onChanged.addListener((changes) => {
  if (changes.enabled !== undefined) isEnabled = changes.enabled.newValue;
});

/**
 * ONLY METHOD: onDeterminingFilename fires BEFORE save dialog.
 * Returning true tells Chrome we're handling this download.
 * onCreated is NOT used — it causes duplicate dialogs.
 */
chrome.downloads.onDeterminingFilename.addListener((downloadItem, suggest) => {
  if (!isEnabled) return false;

  const url = downloadItem.url;
  if (!url || url.startsWith('blob:') || url.startsWith('data:')) return false;

  // Prevent infinite loops if we are falling back to browser download
  if (urlsToSkipInterception.has(url)) {
    urlsToSkipInterception.delete(url);
    return false;
  }

  const filename = downloadItem.filename || getFilenameFromUrl(url);
  if (!shouldIntercept(url, filename, downloadItem.fileSize || 0)) return false;

  // Cancel immediately — prevents save dialog
  chrome.downloads.cancel(downloadItem.id, () => {
    chrome.downloads.erase({ id: downloadItem.id });
  });

  // Send to Flow app
  sendToFlow(url, filename).then(success => {
    if (!success) {
      showNotification('Flow app is not running', 'Open Flow and try again.');
      urlsToSkipInterception.add(url);
      chrome.downloads.download({ url });
    }
  });

  // Resolve Chrome's suggest callback immediately to prevent Chrome from hanging/waiting
  suggest();
  return true; // signals Chrome we handled it
});

/**
 * Messages from content script and popup
 */
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === 'DOWNLOAD_LINK') {
    sendToFlow(message.url, message.filename).then(success => {
      sendResponse({ success });
    });
    return true;
  }
  if (message.type === 'CHECK_STATUS') {
    checkFlowStatus().then(status => sendResponse(status));
    return true;
  }
  if (message.type === 'GET_SETTINGS') {
    chrome.storage.local.get(['enabled', 'port'], (result) => {
      sendResponse({ enabled: result.enabled !== false, port: result.port || DEFAULT_PORT });
    });
    return true;
  }
  if (message.type === 'SET_ENABLED') {
    isEnabled = message.value;
    chrome.storage.local.set({ enabled: message.value });
    sendResponse({ ok: true });
    return true;
  }
});

async function sendToFlow(url, filename) {
  try {
    const port = await getPort();
    const res = await fetch(`http://localhost:${port}/add`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        items: [{
          type: 'http',
          link: url,
          headers: {},
          downloadPage: '',
          suggestedName: filename
        }],
        options: { silentAdd: false, silentStart: false },
      }),
    });
    return res.ok;
  } catch {
    return false;
  }
}

async function checkFlowStatus() {
  try {
    const port = await getPort();
    const controller = new AbortController();
    setTimeout(() => controller.abort(), 2000);
    await fetch(`http://localhost:${port}/`, { method: 'GET', signal: controller.signal });
    return { running: true, port };
  } catch {
    return { running: false, port: await getPort() };
  }
}

async function getPort() {
  const result = await chrome.storage.local.get('port');
  return result.port || DEFAULT_PORT;
}

function shouldIntercept(url, filename, fileSize) {
  const ext = getExtension(filename || url);
  if (ext && DOWNLOAD_EXTENSIONS.has(ext.toLowerCase())) return true;
  if (fileSize > 1024 * 1024) return true;
  return false;
}

function getExtension(str) {
  const match = str.match(/\.([a-zA-Z0-9]{1,10})(\?|#|$)/);
  return match ? match[1] : null;
}

function getFilenameFromUrl(url) {
  try {
    return decodeURIComponent(new URL(url).pathname.split('/').pop()) || 'download';
  } catch {
    return 'download';
  }
}

function showNotification(title, message) {
  chrome.notifications.create({
    type: 'basic', iconUrl: 'icons/icon48.png', title, message,
  });
}
