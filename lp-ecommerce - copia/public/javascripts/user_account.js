document.addEventListener("DOMContentLoaded", () => {
    const userInfoDiv = document.getElementById("user-info");
    const userList = document.getElementById("user-list");
    const requestBalanceBtn = document.getElementById("request-balance-btn");
    const closeAccountBtn = document.getElementById("close-account-btn");
    const balanceForm = document.getElementById("balance-form");
    const submitBalanceBtn = document.getElementById("submit-balance");
    const closeforms = document.getElementById("withdraw-form");
    const submitcloseBtn = document.getElementById("submit-withdraw");

    // [IDF-0019] solicita la información del cliente actualmente logueado.
    fetch("/user_data")
        .then(res => res.json())
        .then(data => {
            console.log(data);
            userInfoDiv.innerHTML = `
                <p><strong>Usuario:</strong> ${data.username}</p>
                <p><strong>Nombre Completo:</strong> ${data.fullname}</p>
                <p><strong>Email:</strong> ${data.email}</p>
            `;

    
            // const items = ["Contenido A", "Contenido B", "Contenido C"];
            // items.forEach(item => {
            //     const li = document.createElement("li");
            //     li.textContent = item;
            //     li.style.cursor = "pointer";
            //     li.addEventListener("click", () => alert(`Has seleccionado: ${item}`));
            //     userList.appendChild(li);
            // });
        })
        .catch(err => {
            userInfoDiv.innerHTML = "<p style='color:red'>Error al cargar datos del usuario.</p>";
            console.error(err);
        });

    requestBalanceBtn.addEventListener("click", () => {
        balanceForm.style.display = balanceForm.style.display === "none" ? "block" : "none";
    });

    // [IDF-0004] envia la peticion al servidor de enviar todo el credito del cliente a cierta tarjeta.
    submitcloseBtn.addEventListener("click", () => {
        const tarjeta = document.getElementById("withdraw_card").value;
        const cardType = document.getElementById("withdraw_card_type").value;

        if (!tarjeta) {
            alert("Complete todos los campos correctamente.");
            return;
        }
        
        fetch("/withdraw_balance", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ tarjeta,cardType })
        })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    alert("Saldo retirado con éxito.");
                    closeforms.style.display = "none";
                    window.location.href = "/user_account.html";
                }
            })
            .catch(err => {
                alert("Ocurrió un error al procesar la solicitud.");
                console.error(err);
            });
    });

    // [IDF-0011] solicita saldo al servidor.
    submitBalanceBtn.addEventListener("click", () => {
        const tarjeta = document.getElementById("tarjeta").value;
        const cantidad = parseFloat(document.getElementById("cantidad").value);
        const cardType = document.getElementById("card_type").value;

        if (!tarjeta || isNaN(cantidad) || cantidad <= 0) {
            alert("Complete todos los campos correctamente.");
            return;
        }

        fetch("/request_balance", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ tarjeta, cantidad,cardType })
        })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    alert("Saldo solicitado con éxito.");
                    balanceForm.style.display = "none";
                } else {
                    alert(data.message);
                }
            })
            .catch(err => {
                alert("Ocurrió un error al procesar la solicitud.");
                console.error(err);
            });
    });

    // [IDF-0003] verifica si es posible cerrar la cuenta de un cliente.
    closeAccountBtn.addEventListener("click", () => {
        if (confirm("¿Estás seguro de cerrar tu cuenta?")) {
            fetch("/close_account", { method: "GET" })
                .then(res => res.json())
                .then(data => {
                    if (data.success) {
                        alert("Cuenta cerrada correctamente.");
                        window.location.href = "/";
                    } else {
                        alert("No se pudo cerrar la cuenta, retire su saldo antes de cerrar la cuenta.");
                        closeforms.style.display = closeforms.style.display === "none" ? "block" : "none";
                    }
                })
                .catch(err => {
                    alert("Error al cerrar la cuenta.");
                    console.error(err);
                });
        }
    });

    const downloadsList = document.getElementById("downloads-list");
    // [IDF-0020] solicita las compras del cliente actualmente logueado.
    fetch("/get_user_downloads")
    .then(res => res.json())
    .then(data => {
        downloadsList.innerHTML = ''; // Limpiar lista

        if (data.length === 0) {
            downloadsList.innerHTML = '<li>No hay contenidos descargados.</li>';
            return;
        }

        data.forEach(item => {
            const li = document.createElement("li");
            li.classList.add("recarga-item");

            // Construir el contenido base
            let html = `
                <a href="item_view.html?id=${item.id}"><h4>${item.title}</a>
                (${item.type})</h4>
                <p><strong>Autor:</strong> ${item.author} | 
                <strong>Puntuación:</strong> ${item.rating} | 
                <strong>Tipo:</strong> ${item.tipo_compra}`;

            // Agregar descargas si es mayor a 0
            if (item.descargas > 0) {
                html += ` | <strong>Descargas:</strong> ${item.descargas}`;
            }

            html += `</p>`;

            li.innerHTML = html;
            downloadsList.appendChild(li);
        });
    })
    .catch(err => {
        downloadsList.innerHTML = '<li style="color:red">Error al cargar contenidos.</li>';
        console.error(err);
    });
});
