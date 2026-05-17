/**
 * Flow Download Manager - Browser Extension
 * Background service worker that intercepts downloads and sends them to the app.
 */

const DEFAULT_PORT = 15151;
const SUPPORTED_EXTENSIONS = [
  '.zip', '.rar', '.7z', '.tar', '.gz', '.bz2', '.xz',
  '.exe', '.msi', '.dmg', '.deb', '.rpm', '.appimage',
  '.mp4', '.mkv', '.avi', '.mov', '.wmv', '.flv', '.webm',
  '.mp3', '.flac', '.wav', '.aac', '.ogg', '.wma',
  '.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx',
  '.iso', '.img', '.bin',
  '.apk', '.ipa',
];

const MIN_SIZE_BYTES = 1024 * 1024; // 1MB - only intercept files > 1MB

// Intercept browser downloads
chrome.downloads.onCreated.addListener(async (downloadItem) => {
  const url = downloadItem.url;
  const filename = downloadItem.filename || getFilenameFromUrl(url);
  const fileSize = downloadItem.totalBytes || 0;

  // Check if we should intercept this download
  if (!shouldIntercept(url, filename, fileSize)) {
    return;
  }

  // Cancel the browser download
  chrome.downloads.cancel(downloadItem.id);
  chrome.downloads.erase({ id: downloadItem.id });

  // Send to Flow app
  const success = await sendToFlow(url, filename);

  if (!success) {
    // If Flow is not running, show notification and let browser handle it
    chrome.notifications.create({
      type: 'basic',
      iconUrl: 'icons/icon48.png',
      title: 'Flow Download Manager',
      message: 'App is not running. Download will proceed in browser.',
    });
    // Re-download in browser
    chrome.downloads.download({ url });
  }
});

// Listen for messages from content script (link clicks)
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'DOWNLOAD_LINK') {
    sendToFlow(message.url, message.filename).then(success => {
      sendResponse({ success });
    });
    return true; // async response
  }

  if (message.type === 'CHECK_STATUS') {
    checkFlowStatus().then(status => {
      sendResponse(status);
    });
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
    console.log('Flow app not reachable:', e.message);
    return false;
  }
}

async function checkFlowStatus() {
  try {
    const port = await getPort();
    const response = await fetch(`http://localhost:${port}/`, {
      method: 'GET',
    });
    return { running: response.ok, port };
  } catch (e) {
    return { running: false, port: DEFAULT_PORT };
  }
}

async function getPort() {
  const result = await chrome.storage.local.get('port');
  return result.port || DEFAULT_PORT;
}

function shouldIntercept(url, filename, fileSize) {
  // Don't intercept blob URLs or data URLs
  if (url.startsWith('blob:') || url.startsWith('data:')) return false;

  // Check file extension
  const ext = getExtension(filename || url);
  if (ext && SUPPORTED_EXTENSIONS.includes(ext.toLowerCase())) return true;

  // Check file size (if known)
  if (fileSize > MIN_SIZE_BYTES) return true;

  return false;
}

function getExtension(str) {
  const match = str.match(/\.([a-zA-Z0-9]+)(\?|$)/);
  return match ? '.' + match[1] : null;
}

function getFilenameFromUrl(url) {
  try {
    const pathname = new URL(url).pathname;
    return pathname.split('/').pop() || 'download';
  } catch {
    return 'download';
  }
}
