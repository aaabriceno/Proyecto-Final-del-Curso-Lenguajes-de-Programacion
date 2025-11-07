document.addEventListener("DOMContentLoaded", () => {
  const userInfoDiv = document.getElementById("user-info");
  const userList = document.getElementById("user-list");
  const requestBalanceBtn = document.getElementById("request-balance-btn");
  const closeAccountBtn = document.getElementById("close-account-btn");
  const balanceForm = document.getElementById("balance-form");
  const submitBalanceBtn = document.getElementById("submit-balance");
  const closeForm = document.getElementById("withdraw-form");
  const submitCloseBtn = document.getElementById("submit-withdraw");
  const downloadsList = document.getElementById("downloads-list");

  // --- Helper genérico para peticiones ---
  async function request(url, method = "GET", body = null) {
    const options = { method, headers: { "Content-Type": "application/json" } };
    if (body) options.body = JSON.stringify(body);

    const res = await fetch(url, options);
    return await res.json();
  }

  // [IDF-0019] Carga información del usuario logueado
  request("/user_data")
    .then(data => {
      if (!data.username) throw new Error("Datos inválidos del usuario");
      userInfoDiv.innerHTML = `
        <p><strong>Usuario:</strong> ${data.username}</p>
        <p><strong>Nombre Completo:</strong> ${data.fullname}</p>
        <p><strong>Email:</strong> ${data.email}</p>
      `;
    })
    .catch(err => {
      userInfoDiv.innerHTML = "<p class='text-danger'>Error al cargar datos del usuario.</p>";
      console.error(err);
    });

  // Toggle del formulario de recarga
  requestBalanceBtn?.addEventListener("click", () => {
    balanceForm.style.display = balanceForm.style.display === "none" ? "block" : "none";
  });

  // [IDF-0004] Retiro de saldo a tarjeta
  submitCloseBtn?.addEventListener("click", async () => {
    const tarjeta = document.getElementById("withdraw_card").value.trim();
    const cardType = document.getElementById("withdraw_card_type").value;

    if (!tarjeta || tarjeta.length < 6) {
      alert("Ingrese un número de tarjeta válido.");
      return;
    }

    try {
      const data = await request("/withdraw_balance", "POST", { tarjeta, cardType });
      if (data.success) {
        alert("Saldo retirado con éxito.");
        closeForm.style.display = "none";
        window.location.href = "/user_account.html";
      } else {
        alert(data.message || "No se pudo realizar el retiro.");
      }
    } catch (err) {
      console.error(err);
      alert("Error de conexión con el servidor.");
    }
  });

  // [IDF-0011] Solicita saldo
  submitBalanceBtn?.addEventListener("click", async () => {
    const tarjeta = document.getElementById("tarjeta").value.trim();
    const cantidad = parseFloat(document.getElementById("cantidad").value);
    const cardType = document.getElementById("card_type").value;

    if (!tarjeta || isNaN(cantidad) || cantidad <= 0) {
      alert("Complete todos los campos correctamente.");
      return;
    }

    try {
      const data = await request("/request_balance", "POST", { tarjeta, cantidad, cardType });
      if (data.success) {
        alert("Solicitud de saldo enviada correctamente.");
        balanceForm.style.display = "none";
      } else {
        alert(data.message || "Error al solicitar saldo.");
      }
    } catch (err) {
      console.error(err);
      alert("Error de conexión con el servidor.");
    }
  });

  // [IDF-0003] Cierre de cuenta
  closeAccountBtn?.addEventListener("click", async () => {
    if (!confirm("¿Estás seguro de cerrar tu cuenta?")) return;

    try {
      const data = await request("/close_account", "GET");
      if (data.success) {
        alert("Cuenta cerrada correctamente.");
        window.location.href = "/";
      } else {
        alert("No se pudo cerrar la cuenta. Retira tu saldo antes.");
        closeForm.style.display = closeForm.style.display === "none" ? "block" : "none";
      }
    } catch (err) {
      console.error(err);
      alert("Error al cerrar la cuenta.");
    }
  });

  // [IDF-0020] Cargar compras del usuario
  request("/get_user_downloads")
    .then(data => {
      downloadsList.innerHTML = '';

      if (!Array.isArray(data) || data.length === 0) {
        downloadsList.innerHTML = '<li>No hay contenidos descargados.</li>';
        return;
      }

      const fragment = document.createDocumentFragment();

      data.forEach(item => {
        const li = document.createElement("li");
        li.classList.add("recarga-item");

        const link = document.createElement("a");
        link.href = `item_view.html?id=${item.id}`;
        link.textContent = item.title;

        const h4 = document.createElement("h4");
        h4.appendChild(link);
        h4.insertAdjacentText("beforeend", ` (${item.type})`);

        const p = document.createElement("p");
        p.innerHTML = `
          <strong>Autor:</strong> ${item.author} |
          <strong>Puntuación:</strong> ${item.rating} |
          <strong>Tipo:</strong> ${item.tipo_compra}
          ${item.descargas > 0 ? ` | <strong>Descargas:</strong> ${item.descargas}` : ""}
        `;

        li.appendChild(h4);
        li.appendChild(p);
        fragment.appendChild(li);
      });

      downloadsList.appendChild(fragment);
    })
    .catch(err => {
      console.error(err);
      downloadsList.innerHTML = '<li class="text-danger">Error al cargar contenidos.</li>';
    });
});
