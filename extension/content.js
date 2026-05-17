/**
 * Flow Download Manager - Content Script
 * Shows an overlay button when hovering over download links.
 * Clicking the overlay sends the link to Flow app.
 */

let overlay = null;
let currentLink = null;
let hideTimeout = null;

// Create overlay element
function createOverlay() {
  if (overlay) return overlay;

  overlay = document.createElement('div');
  overlay.id = 'flow-dm-overlay';
  overlay.innerHTML = `
    <div class="flow-dm-btn">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
        <path d="M12 3v12m0 0l-4-4m4 4l4-4M5 19h14" stroke="white" stroke-width="2.5" stroke-linecap="square"/>
      </svg>
      <span>Flow</span>
    </div>
  `;

  overlay.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (currentLink) {
      chrome.runtime.sendMessage({
        type: 'DOWNLOAD_LINK',
        url: currentLink.href,
        filename: getFilenameFromUrl(currentLink.href),
      });
      showFeedback();
    }
  });

  overlay.addEventListener('mouseenter', () => {
    clearTimeout(hideTimeout);
  });

  overlay.addEventListener('mouseleave', () => {
    hideOverlay();
  });

  document.body.appendChild(overlay);
  return overlay;
}

function showOverlay(link, rect) {
  createOverlay();
  clearTimeout(hideTimeout);

  currentLink = link;
  overlay.style.top = `${rect.top + window.scrollY - 30}px`;
  overlay.style.left = `${rect.left + window.scrollX}px`;
  overlay.classList.add('flow-dm-visible');
}

function hideOverlay() {
  hideTimeout = setTimeout(() => {
    if (overlay) {
      overlay.classList.remove('flow-dm-visible');
      currentLink = null;
    }
  }, 300);
}

function showFeedback() {
  if (overlay) {
    overlay.querySelector('.flow-dm-btn').textContent = '✓ Sent';
    setTimeout(() => {
      if (overlay) {
        overlay.querySelector('.flow-dm-btn').innerHTML = `
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M12 3v12m0 0l-4-4m4 4l4-4M5 19h14" stroke="white" stroke-width="2.5" stroke-linecap="square"/>
          </svg>
          <span>Flow</span>
        `;
      }
      hideOverlay();
    }, 1500);
  }
}

function isDownloadLink(link) {
  if (!link || !link.href) return false;
  const href = link.href.toLowerCase();

  // Skip javascript, mailto, anchor links
  if (href.startsWith('javascript:') || href.startsWith('mailto:') || href.startsWith('#')) return false;

  // Check for download attribute
  if (link.hasAttribute('download')) return true;

  // Check file extensions
  const downloadExts = [
    '.zip', '.rar', '.7z', '.tar', '.gz', '.exe', '.msi', '.dmg',
    '.mp4', '.mkv', '.avi', '.mov', '.webm', '.mp3', '.flac', '.wav',
    '.pdf', '.iso', '.apk', '.deb', '.rpm', '.appimage',
  ];

  for (const ext of downloadExts) {
    if (href.includes(ext)) return true;
  }

  return false;
}

function getFilenameFromUrl(url) {
  try {
    const pathname = new URL(url).pathname;
    return decodeURIComponent(pathname.split('/').pop()) || 'download';
  } catch {
    return 'download';
  }
}

// Listen for mouse hover on links
document.addEventListener('mouseover', (e) => {
  const link = e.target.closest('a');
  if (link && isDownloadLink(link)) {
    const rect = link.getBoundingClientRect();
    showOverlay(link, rect);
  }
});

document.addEventListener('mouseout', (e) => {
  const link = e.target.closest('a');
  if (link) {
    hideOverlay();
  }
});
