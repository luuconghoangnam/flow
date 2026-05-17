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

// Track IDs we've already handled to avoid double-processing
const handledIds = new Set();

chrome.storage.local.get(['enabled', 'port'], (result) => {
  isEnabled = result.enabled !== false;
});
chrome.storage.onChanged.addListener((changes) => {
  if (changes.enabled !== undefined) isEnabled = changes.enabled.newValue;
});

/**
 * BEST METHOD: onDeterminingFilename fires BEFORE the save dialog.
 * Returning true from the listener tells Chrome we're handling it.
 * We cancel the download immediately — no save dialog appears.
 */
chrome.downloads.onDeterminingFilename.addListener((downloadItem, suggest) => {
  if (!isEnabled) return false;

  const url = downloadItem.url;
  if (!url || url.startsWith('blob:') || url.startsWith('data:')) return false;

  const filename = downloadItem.filename || getFilenameFromUrl(url);
  if (!shouldIntercept(url, filename, downloadItem.fileSize || 0)) return false;

  // Mark as handled so onCreated doesn't double-process
  handledIds.add(downloadItem.id);

  // Cancel immediately — this is what prevents the save dialog
  chrome.downloads.cancel(downloadItem.id, () => {
    chrome.downloads.erase({ id: downloadItem.id });
    handledIds.delete(downloadItem.id);
  });

  // Send to Flow app
  sendToFlow(url, filename).then(success => {
    if (!success) {
      showNotification('Flow app is not running', 'Open Flow and try again.');
      // Re-download in browser as fallback
      chrome.downloads.download({ url });
    }
  });

  // Returning true signals we're handling this download
  return true;
});

/**
 * FALLBACK: onCreated catches downloads that onDeterminingFilename missed
 * (e.g. downloads triggered programmatically without a filename phase).
 */
chrome.downloads.onCreated.addListener((downloadItem) => {
  if (!isEnabled) return;
  if (handledIds.has(downloadItem.id)) return; // already handled above

  const url = downloadItem.url;
  if (!url || url.startsWith('blob:') || url.startsWith('data:')) return;

  const filename = downloadItem.filename || getFilenameFromUrl(url);
  if (!shouldIntercept(url, filename, downloadItem.totalBytes || 0)) return;

  handledIds.add(downloadItem.id);
  chrome.downloads.cancel(downloadItem.id, () => {
    chrome.downloads.erase({ id: downloadItem.id });
    handledIds.delete(downloadItem.id);
  });

  sendToFlow(url, filename).then(success => {
    if (!success) {
      showNotification('Flow app is not running', 'Download will proceed in browser.');
      chrome.downloads.download({ url });
    }
  });
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
        items: [{ type: 'http', link: url, headers: {}, downloadPage: '' }],
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
