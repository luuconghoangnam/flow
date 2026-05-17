/* ANIMATIONS — scroll reveal, typed text, counters, download bars */

// Scroll reveal
const revealObserver = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      const delay = entry.target.dataset.delay || 0;
      setTimeout(() => entry.target.classList.add('visible'), parseInt(delay));
    }
  });
}, { threshold: 0.1 });
document.querySelectorAll('.reveal').forEach(el => revealObserver.observe(el));

// Typed text
const phrases = ['Speed of Thought', 'Speed of Light', 'Speed of Flow'];
let phraseIdx = 0, charIdx = 0, deleting = false;
const typedEl = document.getElementById('typedText');
function typeLoop() {
  if (!typedEl) return;
  const phrase = phrases[phraseIdx];
  if (!deleting) {
    typedEl.textContent = phrase.slice(0, ++charIdx);
    if (charIdx === phrase.length) { deleting = true; setTimeout(typeLoop, 2000); return; }
  } else {
    typedEl.textContent = phrase.slice(0, --charIdx);
    if (charIdx === 0) { deleting = false; phraseIdx = (phraseIdx + 1) % phrases.length; }
  }
  setTimeout(typeLoop, deleting ? 50 : 80);
}
typeLoop();

// Animate download bars when hero is visible
setTimeout(() => {
  document.querySelectorAll('.dl-fill').forEach(bar => {
    const target = getComputedStyle(bar).getPropertyValue('--w');
    bar.style.width = '0%';
    setTimeout(() => { bar.style.width = target; }, 600);
  });
}, 800);

// Counter animation
function animateCounter(el) {
  const target = parseInt(el.dataset.target);
  let current = 0;
  const step = Math.ceil(target / 40);
  const timer = setInterval(() => {
    current = Math.min(current + step, target);
    el.textContent = current;
    if (current >= target) clearInterval(timer);
  }, 40);
}
const counterObserver = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      animateCounter(entry.target);
      counterObserver.unobserve(entry.target);
    }
  });
}, { threshold: 0.5 });
document.querySelectorAll('.counter').forEach(el => counterObserver.observe(el));

// Speed counter animation in mockup
const speedEl = document.querySelector('.speed-counter');
if (speedEl) {
  const speeds = ['0 B/s', '1.2 MB/s', '3.8 MB/s', '4.2 MB/s', '2.9 MB/s', '4.2 MB/s'];
  let si = 0;
  setInterval(() => { si = (si + 1) % speeds.length; speedEl.textContent = speeds[si]; }, 2000);
}
