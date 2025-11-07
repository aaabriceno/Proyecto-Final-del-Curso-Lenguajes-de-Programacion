// Sistema de Notificaciones con Toast
(function() {
  let lastNotificationCount = 0;
  let toastContainer = null;

  // Crear contenedor de toasts si no existe
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
    const container = createToastContainer();
    
    const toastId = 'toast-' + Date.now();
    const iconMap = {
      'balance_approved': '✅',
      'balance_rejected': '❌',
      'purchase_success': '🎉',
      'info': 'ℹ️'
    };
    
    const bgColorMap = {
      'balance_approved': 'bg-success',
      'balance_rejected': 'bg-danger',
      'purchase_success': 'bg-primary',
      'info': 'bg-info'
    };
    
    const icon = iconMap[type] || iconMap['info'];
    const bgColor = bgColorMap[type] || bgColorMap['info'];
    
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
      delay: 8000 // 8 seconds
    });
    
    toast.show();
    
    // Remover del DOM después de cerrar
    toastElement.addEventListener('hidden.bs.toast', function() {
      toastElement.remove();
    });
    
    // Reproducir sonido de notificación (opcional)
    playNotificationSound();
  }

  // Reproducir sonido de notificación (si el navegador lo permite)
  function playNotificationSound() {
    try {
      const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHO8tiJNwgZaLvt559NEAxQp+PwtmMcBjiR1/LMeSwFJHfH8N2QQAoUXrTp66hVFApGn+DyvmwhBTGH0fPTgjMGHm7A7+OZRQ0PVqzn5aVYEQlFnuHwuW0hBTOH0vLSfjQGHW/A7eWYRg0NV63o5aVYEQhGn+HxuG4iBDCH0fLSfzUGG2/B7uSZRQwOV63n5aRYEQdFn+HxuWwiBDGG0fHSfzUGGm/A7uWYRgwMVq3n5aRZEQZGnuHxuWwiBTCH0fLSfjYGGm/C7uSYRgwMVq7o5aNZEQZGnuHxuWsiBTCH0fLSfjYFGm/C7+SYRQ0LVq7o5aNZEgVGnuDxuWwhBTCH0vLRfjYFGm/C7+SYRQ0LVa7n5aRZEgVGnuDxuGwiBTCH0vLRfzYFGW/C7+SYRQ0LVK7n5aRZEgRGnt/xuWwiBDCH0vLRfjYFGW/C7uSZRQ0KVK7n5aNZEgRFnt/xuWwiBDCH0fLRfjYFGW/B7uSYRQwKVK7n5aNZEgRFnt/xuWsiBDCH0fLSfjYFGW/B7uSYRQwKVK3n5aNZEQRFnt/xuWsiBDCG0fHSfjYFGW/B7uWYRAwKVK3n5aRZEQNFnt/xuWsiBDCG0fHSfzUFGW/B7uWYRAwJVK3n5aRZEQNFnt/wuWsiBDCG0fHSfzUFGW/B7uWYRAwJVK3n5aRZEQNFnt/wuWsiBDCG0fHSfzUFGW/B7uWYRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWYRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B7uWZRAwJU63n5aRZEQNFnt/wuGwiBDCG0fHSfjYFGG/B');
      audio.volume = 0.3;
      audio.play().catch(e => console.log('No se pudo reproducir sonido:', e));
    } catch (e) {
      // Silenciar error si no se puede reproducir
    }
  }

  // Verificar nuevas notificaciones Y actualizar saldo
  function checkNotifications() {
    fetch('/notifications')
      .then(response => response.json())
      .then(data => {
        const currentCount = data.count || 0;
        
        // Si hay nuevas notificaciones, mostrar toast
        if (currentCount > lastNotificationCount) {
          // Mostrar solo las nuevas notificaciones
          const newNotifications = data.notifications.slice(0, currentCount - lastNotificationCount);
          
          newNotifications.forEach(notif => {
            showToast(notif.message, notif.type);
            
            // Si es notificación de balance, actualizar saldo inmediatamente
            if (notif.type === 'balance_approved' || notif.type === 'balance_rejected') {
              setTimeout(() => updateBalance(), 500);
            }
            
            // Marcar como leída automáticamente después de mostrarla
            setTimeout(() => {
              markAsRead(notif.id);
            }, 2000);
          });
        }
        
        lastNotificationCount = currentCount;
        
        // Actualizar badge en el navbar
        updateNotificationBadge(currentCount);
        
        // Actualizar saldo cada vez que se verifican notificaciones
        updateBalance();
      })
      .catch(error => console.log('Error al verificar notificaciones:', error));
  }

  // Actualizar saldo en tiempo real
  function updateBalance() {
    fetch('/user/balance')
      .then(response => response.json())
      .then(data => {
        // Buscar elementos que muestran el saldo
        const balanceElements = document.querySelectorAll('[data-balance]');
        balanceElements.forEach(elem => {
          const newBalance = parseFloat(data.balance).toFixed(2);
          const currentBalance = elem.textContent.replace(/[^0-9.]/g, '');
          
          // Solo actualizar si cambió
          if (currentBalance !== newBalance) {
            elem.textContent = '$' + newBalance;
            
            // Efecto visual de actualización (flash verde)
            elem.style.transition = 'background-color 0.5s';
            elem.style.backgroundColor = '#28a745';
            elem.style.color = 'white';
            elem.style.padding = '2px 6px';
            elem.style.borderRadius = '4px';
            
            setTimeout(() => {
              elem.style.backgroundColor = 'transparent';
              elem.style.color = '';
            }, 1500);
          }
        });
        
        // Actualizar total gastado
        const spentElements = document.querySelectorAll('[data-total-spent]');
        spentElements.forEach(elem => {
          elem.textContent = '$' + parseFloat(data.totalSpent).toFixed(2);
        });
        
        // Actualizar badge VIP si aplica
        const vipBadges = document.querySelectorAll('[data-vip-badge]');
        vipBadges.forEach(elem => {
          if (data.isVip) {
            elem.style.display = 'inline-block';
          } else {
            elem.style.display = 'none';
          }
        });
      })
      .catch(error => console.log('Error al actualizar saldo:', error));
  }

  // Actualizar badge de notificaciones
  function updateNotificationBadge(count) {
    const btn = document.getElementById('notificationBtn');
    if (btn) {
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
  }

  // Marcar notificación como leída
  function markAsRead(notificationId) {
    fetch(`/notifications/${notificationId}/read`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Csrf-Token': document.querySelector('input[name="csrfToken"]')?.value || 'nocheck'
      }
    }).catch(error => console.log('Error al marcar notificación:', error));
  }

  // Iniciar polling de notificaciones cada 10 segundos
  function startNotificationPolling() {
    // Verificar inmediatamente
    checkNotifications();
    
    // Luego cada 10 segundos
    setInterval(checkNotifications, 10000);
  }

  // Iniciar cuando el DOM esté listo
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startNotificationPolling);
  } else {
    startNotificationPolling();
  }

  // Event listener para el botón de notificaciones
  document.addEventListener('click', function(e) {
    if (e.target.id === 'notificationBtn' || e.target.closest('#notificationBtn')) {
      e.preventDefault();
      
      // Marcar todas como leídas
      fetch('/notifications/mark-all-read', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Csrf-Token': document.querySelector('input[name="csrfToken"]')?.value || 'nocheck'
        }
      }).then(() => {
        updateNotificationBadge(0);
        lastNotificationCount = 0;
      });
    }
  });
})();
