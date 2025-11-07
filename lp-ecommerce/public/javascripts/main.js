// [IDF-ROOT-0001] Verifica sesión actual y ajusta la navbar.
document.addEventListener("DOMContentLoaded", async () => {
  try {
    const res = await fetch("/get_user_role");
    const data = await res.json();

    const role = data.role || "Invitado";
    updateNavbar(role);
  } catch (err) {
    console.error("Error verificando sesión:", err);
    updateNavbar("Invitado");
  }
});

function updateNavbar(role) {
  const loginBtn = document.querySelector('a[href="/login"]');
  const registerBtn = document.querySelector('a[href="/register"]');
  const logoutBtn = document.querySelector('a[href="/logout"], a[href="/login"][style]');
  const accountBtn = document.querySelector('a[href="/user/account"]');

  if (role === "Cliente" || role === "Administrador") {
    if (loginBtn) loginBtn.style.display = "none";
    if (registerBtn) registerBtn.style.display = "none";
    if (logoutBtn) logoutBtn.style.display = "inline-block";
    if (accountBtn) accountBtn.style.display = "inline-block";
  } else {
    if (loginBtn) loginBtn.style.display = "inline-block";
    if (registerBtn) registerBtn.style.display = "inline-block";
    if (logoutBtn) logoutBtn.style.display = "none";
    if (accountBtn) accountBtn.style.display = "none";
  }
}
