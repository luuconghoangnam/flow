/* UI — nav scroll, cursor glow, faq toggle, form, copy cmd */

// Cursor glow
const cursorGlow = document.getElementById('cursorGlow');
document.addEventListener('mousemove', e => {
  if (cursorGlow) { cursorGlow.style.left = e.clientX + 'px'; cursorGlow.style.top = e.clientY + 'px'; }
});

// Nav scroll progress
const navProgress = document.getElementById('navProgress');
window.addEventListener('scroll', () => {
  const scrolled = window.scrollY / (document.body.scrollHeight - window.innerHeight);
  if (navProgress) navProgress.style.width = (scrolled * 100) + '%';
});

// FAQ toggle
function toggleFaq(btn) {
  const item = btn.closest('.faq-item');
  const isOpen = item.classList.contains('open');
  document.querySelectorAll('.faq-item.open').forEach(i => i.classList.remove('open'));
  if (!isOpen) item.classList.add('open');
}
window.toggleFaq = toggleFaq;

// Copy install command
function copyCmd() {
  const text = document.getElementById('cmdText')?.textContent;
  if (!text) return;
  navigator.clipboard.writeText(text).then(() => {
    const btn = document.querySelector('.cmd-copy');
    if (btn) { btn.textContent = 'Copied!'; btn.style.color = 'var(--green)'; setTimeout(() => { btn.textContent = 'Copy'; btn.style.color = ''; }, 2000); }
  });
}
window.copyCmd = copyCmd;

// Contact form (Formspree)
const form = document.getElementById('contactForm');
const statusEl = document.getElementById('formStatus');
const submitBtn = document.getElementById('submitBtn');
if (form) {
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    submitBtn.disabled = true;
    submitBtn.classList.add('loading');
    submitBtn.querySelector('.btn-submit-text').textContent = 'Sending';
    try {
      const res = await fetch(form.action, {
        method: 'POST',
        body: new FormData(form),
        headers: { 'Accept': 'application/json' }
      });
      if (res.ok) {
        statusEl.textContent = '✓ Message sent!';
        statusEl.className = 'form-status success';
        form.reset();
      } else {
        throw new Error('Failed');
      }
    } catch {
      statusEl.textContent = '✗ Failed. Try again.';
      statusEl.className = 'form-status error';
    } finally {
      submitBtn.disabled = false;
      submitBtn.classList.remove('loading');
      submitBtn.querySelector('.btn-submit-text').textContent = 'Send Message';
      setTimeout(() => { statusEl.textContent = ''; statusEl.className = 'form-status'; }, 5000);
    }
  });
}

// Smooth active nav link highlight
const sections = document.querySelectorAll('section[id]');
const navLinks = document.querySelectorAll('.nav-links a');
const sectionObserver = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      navLinks.forEach(a => {
        a.style.color = a.getAttribute('href') === '#' + entry.target.id ? 'var(--orange)' : '';
      });
    }
  });
}, { threshold: 0.4 });
sections.forEach(s => sectionObserver.observe(s));
