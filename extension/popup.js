// Popup script - checks Flow app status and manages toggle

const statusDot = document.getElementById('statusDot');
const statusText = document.getElementById('statusText');
const toggle = document.getElementById('toggle');
const portDisplay = document.getElementById('portDisplay');

// Check status
chrome.runtime.sendMessage({ type: 'CHECK_STATUS' }, (response) => {
  if (response && response.running) {
    statusDot.classList.add('connected');
    statusText.textContent = 'Connected to Flow';
  } else {
    statusText.textContent = 'Flow app not running';
  }
  portDisplay.textContent = response?.port || '15151';
});

// Load toggle state
chrome.runtime.sendMessage({ type: 'GET_SETTINGS' }, (response) => {
  if (response) {
    toggle.classList.toggle('on', response.enabled);
    portDisplay.textContent = response.port;
  }
});

// Toggle click
toggle.addEventListener('click', () => {
  const isOn = toggle.classList.toggle('on');
  chrome.runtime.sendMessage({ type: 'SET_ENABLED', value: isOn });
});
