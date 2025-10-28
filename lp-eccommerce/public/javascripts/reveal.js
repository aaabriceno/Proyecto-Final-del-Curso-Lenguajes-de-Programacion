document.addEventListener('scroll', () => {
  document.querySelectorAll('.reveal').forEach(el => {
    const y = el.getBoundingClientRect().top;
    if (y < window.innerHeight * 0.85) el.classList.add('on');
  });
});
