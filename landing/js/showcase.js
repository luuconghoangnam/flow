/* SHOWCASE CAROUSEL — auto-play with progress bar */
(function() {
  const slides = document.querySelectorAll('.showcase-slide');
  const dotsWrap = document.getElementById('showcaseDots');
  const labelEl = document.getElementById('scLabel');
  const descEl = document.getElementById('scDesc');
  const progressFill = document.getElementById('showcaseProgressFill');
  if (!slides.length) return;

  const INTERVAL = 4000; // ms per slide
  let current = 0;
  let timer = null;
  let progressTimer = null;

  // Build dots
  slides.forEach((_, i) => {
    const dot = document.createElement('div');
    dot.className = 'sc-dot' + (i === 0 ? ' active' : '');
    dot.addEventListener('click', () => goTo(i));
    dotsWrap.appendChild(dot);
  });

  function updateCaption(idx) {
    const slide = slides[idx];
    if (labelEl) labelEl.textContent = slide.dataset.label || '';
    if (descEl) descEl.textContent = slide.dataset.desc || '';
  }

  function goTo(idx) {
    slides[current].classList.remove('active');
    dotsWrap.children[current].classList.remove('active');
    current = (idx + slides.length) % slides.length;
    slides[current].classList.add('active');
    dotsWrap.children[current].classList.add('active');
    updateCaption(current);
    resetProgress();
  }

  function resetProgress() {
    if (progressFill) {
      progressFill.style.transition = 'none';
      progressFill.style.width = '0%';
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          progressFill.style.transition = `width ${INTERVAL}ms linear`;
          progressFill.style.width = '100%';
        });
      });
    }
  }

  function startAuto() {
    clearInterval(timer);
    timer = setInterval(() => goTo(current + 1), INTERVAL);
  }

  // Expose nav for buttons
  window.showcaseNav = function(dir) {
    goTo(current + dir);
    startAuto(); // reset timer on manual nav
  };

  // Pause on hover
  const wrap = document.querySelector('.showcase-carousel-wrap');
  if (wrap) {
    wrap.addEventListener('mouseenter', () => clearInterval(timer));
    wrap.addEventListener('mouseleave', () => startAuto());
  }

  // Init
  updateCaption(0);
  resetProgress();
  startAuto();
})();
