(function() {
  const modalEl = document.getElementById('giftModal');
  if (!modalEl) return;

  if (typeof bootstrap === 'undefined' || !bootstrap.Modal) {
    console.warn('Bootstrap Modal no disponible para giftModal');
    return;
  }

  const modal = new bootstrap.Modal(modalEl);
  const form = modalEl.querySelector('#gift-form');
  const mediaInput = form?.querySelector('input[name="mediaId"]');
  const emailInput = form?.querySelector('input[name="email"]');
  const messageInput = form?.querySelector('textarea[name="message"]');
  const feedbackBox = modalEl.querySelector('[data-gift-feedback]');
  const titleTarget = modalEl.querySelector('[data-gift-title]');
  const datalist = document.getElementById('gift-recipient-suggestions');

  function showFeedback(message, success) {
    if (!feedbackBox) return;
    feedbackBox.classList.remove('d-none', 'alert-success', 'alert-danger');
    feedbackBox.classList.add(success ? 'alert-success' : 'alert-danger');
    feedbackBox.textContent = message;
  }

  function resetFeedback() {
    if (!feedbackBox) return;
    feedbackBox.classList.add('d-none');
    feedbackBox.textContent = '';
  }

  async function submitGift(mediaId, email, message) {
    const payload = {
      mediaId: mediaId,
      destinatario: email,
      mensaje: message
    };

    const response = await fetch('/gift_content', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || 'No se pudo enviar el regalo');
    }
    return response.json();
  }

  form?.addEventListener('submit', async function(event) {
    event.preventDefault();
    resetFeedback();
    const mediaId = mediaInput?.value;
    const email = emailInput?.value.trim();
    const message = messageInput?.value.trim();

    if (!mediaId || !email) {
      showFeedback('Debes seleccionar un producto y un destinatario válido.', false);
      return;
    }
    try {
      await submitGift(mediaId, email, message);
      showFeedback('🎉 Regalo enviado correctamente.', true);
      form.reset();
      if (datalist) datalist.innerHTML = '';
    } catch (err) {
      console.error(err);
      showFeedback(err.message, false);
    }
  });

  document.querySelectorAll('[data-gift-button]').forEach(button => {
    button.addEventListener('click', () => {
      if (mediaInput) mediaInput.value = button.getAttribute('data-media-id') || '';
      if (titleTarget) titleTarget.textContent = button.getAttribute('data-media-title') || '';
      resetFeedback();
      modal.show();
    });
  });

  let debounceTimer = null;
  let lastQuery = '';
  async function fetchRecipients(query) {
    const response = await fetch('/api/users/search?q=' + encodeURIComponent(query));
    if (!response.ok) throw new Error('No se pudo buscar usuarios');
    const data = await response.json();
    return Array.isArray(data) ? data : [];
  }

  emailInput?.addEventListener('input', () => {
    const query = emailInput.value.trim();
    if (!datalist) return;
    if (query.length < 2) {
      datalist.innerHTML = '';
      return;
    }
    if (query === lastQuery) return;
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(async () => {
      lastQuery = query;
      try {
        const suggestions = await fetchRecipients(query);
        datalist.innerHTML = suggestions
          .map(user => `\n<option value="${user.email}">${user.name} (${user.email})</option>`)
          .join('');
      } catch (err) {
        console.error('Error buscando usuarios', err);
      }
    }, 250);
  });
})();
