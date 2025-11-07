// Sistema de Notificaciones con Toast
(function() {
  let lastNotificationCount = 0;
  let toastContainer = null;

  // Crear contenedor de toasts
  function createToastContainer() {
    if (!toastContainer) {
      toastContainer = document.createElement('div');
      toastContainer.className = 'toast-container position-fixed top-0 end-0 p-3';
      toastContainer.style.zIndex = '9999';
      document.body.appendChild(toastContainer);
    }
    return toastContainer;
  }

  // Mostrar un toast
  function showToast(message, type = 'info') {
    // Si no está Bootstrap Toast disponible, salimos silenciosamente
    if (!window.bootstrap || !bootstrap.Toast) {
      console.warn('Bootstrap Toast no disponible, no se pueden mostrar notificaciones visuales.');
      return;
    }

    const container = createToastContainer();
    const toastId = 'toast-' + Date.now();

    const iconMap = {
      balance_approved: '✅',
      balance_rejected: '❌',
      purchase_success: '🎉',
      info: 'ℹ️'
    };

    const bgColorMap = {
      balance_approved: 'bg-success',
      balance_rejected: 'bg-danger',
      purchase_success: 'bg-primary',
      info: 'bg-info'
    };

    const icon = iconMap[type] || iconMap.info;
    const bgColor = bgColorMap[type] || bgColorMap.info;

    const toastHTML = `
      <div id="${toastId}" class="toast" role="alert" aria-live="assertive" aria-atomic="true">
        <div class="toast-header ${bgColor} text-white">
          <strong class="me-auto">${icon} Notificación</strong>
          <button type="button" class="btn-close btn-close-white" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
        <div class="toast-body">
          ${message}
        </div>
      </div>
    `;

    container.insertAdjacentHTML('beforeend', toastHTML);

    const toastElement = document.getElementById(toastId);
    const toast = new bootstrap.Toast(toastElement, {
      animation: true,
      autohide: true,
      delay: 8000
    });

    toast.show();

    toastElement.addEventListener('hidden.bs.toast', () => {
      toastElement.remove();
    });

    playNotificationSound();
  }

  // Sonido notificación (silencioso si falla)
  function playNotificationSound() {
    try {
      const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACB...');
      audio.volume = 0.3;
      audio.play().catch(() => {});
    } catch (e) {
      // ignorar
    }
  }

  // Verificar nuevas notificaciones + actualizar saldo
  function checkNotifications() {
    fetch('/notifications')
      .then(res => res.json())
      .then(data => {
        const notifications = Array.isArray(data.notifications) ? data.notifications : [];
        const currentCount = data.count ?? notifications.length;

        // Determinar cuántas son nuevas basado en diferencia simple
        if (currentCount > lastNotificationCount && notifications.length > 0) {
          const diff = currentCount - lastNotificationCount;

          // Nota: asumimos que notifications[0..diff-1] son las nuevas.
          const newNotifications = notifications.slice(0, diff);

          newNotifications.forEach(notif => {
            showToast(notif.message, notif.type);

            if (notif.type === 'balance_approved' || notif.type === 'balance_rejected') {
              setTimeout(updateBalance, 500);
            }

            // Marcar como leída
            if (notif.id) {
              setTimeout(() => markAsRead(notif.id), 2000);
            }
          });
        }

        lastNotificationCount = currentCount;
        updateNotificationBadge(currentCount);
        updateBalance();
      })
      .catch(err => {
        console.log('Error al verificar notificaciones:', err);
      });
  }

  // Actualizar saldo + total gastado + VIP
  function updateBalance() {
    fetch('/user/balance')
      .then(res => res.json())
      .then(data => {
        if (!data || typeof data.balance === 'undefined') return;

        const newBalance = parseFloat(data.balance).toFixed(2);

        document.querySelectorAll('[data-balance]').forEach(elem => {
          const currentBalance = elem.textContent.replace(/[^0-9.]/g, '');
          if (currentBalance !== newBalance) {
            elem.textContent = '$' + newBalance;
            elem.style.transition = 'background-color 0.5s';
            elem.style.backgroundColor = '#28a745';
            elem.style.color = '#fff';
            elem.style.padding = '2px 6px';
            elem.style.borderRadius = '4px';
            setTimeout(() => {
              elem.style.backgroundColor = 'transparent';
              elem.style.color = '';
            }, 1500);
          }
        });

        if (typeof data.totalSpent !== 'undefined') {
          document.querySelectorAll('[data-total-spent]').forEach(elem => {
            elem.textContent = '$' + parseFloat(data.totalSpent).toFixed(2);
          });
        }

        document.querySelectorAll('[data-vip-badge]').forEach(elem => {
          elem.style.display = data.isVip ? 'inline-block' : 'none';
        });
      })
      .catch(err => {
        console.log('Error al actualizar saldo:', err);
      });
  }

  // Badge de notificaciones
  function updateNotificationBadge(count) {
    const btn = document.getElementById('notificationBtn');
    if (!btn) return;

    let badge = btn.querySelector('.badge');

    if (count > 0) {
      if (!badge) {
        badge = document.createElement('span');
        badge.className = 'position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger';
        btn.appendChild(badge);
      }
      badge.textContent = count;
    } else if (badge) {
      badge.remove();
    }
  }

  // Marcar una notificación como leída
  function markAsRead(notificationId) {
    if (!notificationId) return;

    fetch(`/notifications/${notificationId}/read`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Csrf-Token': document.querySelector('input[name="csrfToken"]')?.value || 'nocheck'
      }
    }).catch(err => console.log('Error al marcar notificación:', err));
  }

  // Polling
  function startNotificationPolling() {
    checkNotifications();
    setInterval(checkNotifications, 10000);
  }

  // Arranque seguro
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startNotificationPolling);
  } else {
    startNotificationPolling();
  }

  // Click en botón de notificaciones → marcar todas como leídas
  document.addEventListener('click', function(e) {
    const btn = e.target.id === 'notificationBtn'
      ? e.target
      : e.target.closest('#notificationBtn');

    if (btn) {
      e.preventDefault();
      fetch('/notifications/mark-all-read', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Csrf-Token': document.querySelector('input[name="csrfToken"]')?.value || 'nocheck'
        }
      }).then(() => {
        updateNotificationBadge(0);
        lastNotificationCount = 0;
      }).catch(err => console.log('Error al marcar todas leídas:', err));
    }
  });
})();
