# 🛍️ LP E‑Commerce en Scala

Proyecto final del curso **Lenguajes de Programación (CCOMP7‑1, UCSP 2025)**.  
Es una tienda en línea implementada en **Scala 2.13** sin framework web, usando:

- Servidor HTTP propio (`ServerSocket`) y router manual.
- **MongoDB** (local o Atlas) para persistencia.
- Frontend con **HTML + Bootstrap 5 + JavaScript**.
- Panel de administración completo (usuarios, productos, categorías, promociones, recargas, estadísticas).

---

## 👥 Equipo

- **Anthony Briceño** – Desarrollo backend / base de datos / despliegue.
- **Paolo Mostajo** – Frontend, vistas y experiencia de usuario.

> El proyecto fue iniciado también con la participación de Alexander Carpio.

---

## ⚙️ Tecnologías principales

- **Scala 2.13**
- **SBT** como build tool
- **MongoDB** (Atlas o instancia local)
- **Jakarta Mail** para envío de correos (boletas)
- **Bootstrap 5** + JavaScript para el frontend

La estructura completa del código y módulos se detalla en `DOCUMENTACION.md`.

---

## 🚀 Cómo ejecutar el proyecto

### 1. Requisitos

- **Java 11+** instalado (`java -version`).
- **SBT 1.8+** instalado (`sbt about`).
- Una instancia de **MongoDB** accesible (local o Atlas).

### 2. Configurar la base de datos

En `app/db/MongoConnection.scala` se define la URI que se usará:

- `uriLocal` → `mongodb://localhost:27017`
- `uriAtlas` → URI de tu cluster de Atlas

Por defecto el código usa `uriAtlas`. Ajusta esa constante o cambia a `uriLocal`
según dónde tengas MongoDB.

La base de datos utilizada se llama `lp_ecommerce` y las colecciones se crean
automáticamente al iniciar la aplicación.

### 3. Arrancar el servidor

```bash
sbt run
```

El servidor HTTP se levanta en `http://localhost:9000`.

---

## 🔐 Credenciales iniciales

Si el sistema detecta que no hay usuarios, crea dos cuentas de ejemplo
(ver `MongoConnection.insertInitialData`):

- **Admin**
  - Email: `admin@lpstudios.com`
  - Contraseña: `admin123`
- **Usuario ejemplo**
  - Email: `user@example.com`
  - Contraseña: `user123`

Con la cuenta de administrador puedes entrar al panel `/admin` y gestionar
usuarios, productos, categorías, promociones, recargas, etc.

---

## 📧 Envío de correos (opcional)

Para que el sistema envíe boletas por correo de forma real, configura estas
variables de entorno antes de ejecutar `sbt run`:

- `SMTP_HOST` – host del servidor SMTP
- `SMTP_PORT` – puerto (típicamente `587`) (opcional)
- `SMTP_USER` – usuario/cuenta SMTP
- `SMTP_PASS` – contraseña o token SMTP
- `SMTP_FROM` – correo remitente (si se omite, usa `SMTP_USER`)
- `SMTP_TLS` – `true`/`false` (por defecto `true`)

Si no se configuran, el sistema entra en **modo demo** y solo imprime el
contenido del correo en la consola.

---


