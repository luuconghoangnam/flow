// Flow Download Manager - Popup Script

const dot = document.getElementById('dot');
const statusText = document.getElementById('statusText');
const toggle = document.getElementById('toggle');
const portDisplay = document.getElementById('portDisplay');

// Check connection status
async function checkStatus() {
  try {
    const result = await chrome.storage.local.get(['port', 'enabled']);
    const port = result.port || 15151;
    const enabled = result.enabled !== false;

    portDisplay.textContent = port;
    toggle.classList.toggle('on', enabled);

    const response = await fetch(`http://localhost:${port}/`, { method: 'GET' });
    if (response.ok) {
      dot.classList.add('connected');
      statusText.classList.add('connected');
      statusText.textContent = 'Connected';
    } else {
      throw new Error('Not OK');
    }
  } catch (e) {
    dot.classList.remove('connected');
    statusText.classList.remove('connected');
    statusText.textContent = 'App not running';
  }
}

// Toggle auto-intercept
toggle.addEventListener('click', async () => {
  const result = await chrome.storage.local.get('enabled');
  const newState = result.enabled === false;
  await chrome.storage.local.set({ enabled: newState });
  toggle.classList.toggle('on', newState);
});

checkStatus();
