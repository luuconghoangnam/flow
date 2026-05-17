/**
 * Flow Download Manager - Background Service Worker
 * 
 * Intercepts downloads BEFORE browser shows save dialog by:
 * 1. Listening to downloads.onCreated → immediately cancel + send to Flow
 * 2. Content script catches link clicks with download-like URLs
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

// Load settings
chrome.storage.local.get(['enabled', 'port'], (result) => {
  isEnabled = result.enabled !== false;
});

chrome.storage.onChanged.addListener((changes) => {
  if (changes.enabled) isEnabled = changes.enabled.newValue;
});

/**
 * PRIMARY INTERCEPTION: catch download the moment browser creates it.
 * We cancel it immediately (before save dialog appears) and send to Flow.
 */
chrome.downloads.onCreated.addListener((downloadItem) => {
  if (!isEnabled) return;

  const url = downloadItem.url;
  if (!url || url.startsWith('blob:') || url.startsWith('data:')) return;

  const filename = downloadItem.filename || getFilenameFromUrl(url);
  
  if (!shouldIntercept(url, filename, downloadItem.totalBytes || 0)) return;

  // IMMEDIATELY cancel - this prevents the save dialog from appearing
  chrome.downloads.cancel(downloadItem.id, () => {
    chrome.downloads.erase({ id: downloadItem.id });
  });

  // Send to Flow
  sendToFlow(url, filename).then(success => {
    if (!success) {
      // Flow not running - notify user and re-download normally
      showNotification('Flow app is not running', 'Download will proceed in browser.');
      chrome.downloads.download({ url });
    }
  });
});

/**
 * Listen for messages from content script
 */
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'DOWNLOAD_LINK') {
    sendToFlow(message.url, message.filename).then(success => {
      sendResponse({ success });
    });
    return true;
  }

  if (message.type === 'CHECK_STATUS') {
    checkFlowStatus().then(status => {
      sendResponse(status);
    });
    return true;
  }

  if (message.type === 'GET_SETTINGS') {
    chrome.storage.local.get(['enabled', 'port'], (result) => {
      sendResponse({
        enabled: result.enabled !== false,
        port: result.port || DEFAULT_PORT,
      });
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
    const response = await fetch(`http://localhost:${port}/add`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        items: [{
          type: 'http',
          link: url,
          headers: {},
          downloadPage: '',
        }],
        options: {
          silentAdd: false,
          silentStart: false,
        }
      }),
    });
    return response.ok;
  } catch (e) {
    return false;
  }
}

async function checkFlowStatus() {
  try {
    const port = await getPort();
    const controller = new AbortController();
    setTimeout(() => controller.abort(), 2000);
    const response = await fetch(`http://localhost:${port}/`, {
      method: 'GET',
      signal: controller.signal,
    });
    return { running: true, port };
  } catch (e) {
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
  if (fileSize > 1024 * 1024) return true; // > 1MB
  return false;
}

function getExtension(str) {
  const match = str.match(/\.([a-zA-Z0-9]{1,10})(\?|#|$)/);
  return match ? match[1] : null;
}

function getFilenameFromUrl(url) {
  try {
    const pathname = new URL(url).pathname;
    return decodeURIComponent(pathname.split('/').pop()) || 'download';
  } catch {
    return 'download';
  }
}

function showNotification(title, message) {
  chrome.notifications.create({
    type: 'basic',
    iconUrl: 'icons/icon48.png',
    title,
    message,
  });
}
