# 🔌 Conectar MongoDB Compass a tu base de datos local

## PASO 1: Abrir MongoDB Compass

1. Abre **MongoDB Compass**
2. Verás la pantalla de conexiones

## PASO 2: Nueva Conexión

Si ves conexiones antiguas, ignóralas. Crea una nueva:

1. Click en **"New Connection"** (botón verde arriba)
2. En el campo **URI**, pega esto:

```
mongodb://localhost:27017
```

3. Click en **"Connect"**

## PASO 3: Explorar tu Base de Datos

Ahora verás:

```
MongoDB Compass
│
├─ admin (base de datos del sistema)
├─ config (configuración)
├─ local (datos locales)
└─ lp_ecommerce ← ¡ESTA ES TU BASE DE DATOS! 🎯
   │
   ├─ categories (1 documento)
   ├─ media (3 documentos)
   └─ users (2 documentos)
```

## PASO 4: Ver tus datos

1. Click en **`lp_ecommerce`** (tu base de datos)
2. Verás las **colecciones** (equivalente a tablas en SQL)
3. Click en cualquier colección para ver los documentos (registros)

### Ejemplo: Ver usuarios

```
lp_ecommerce > users > Documents
```

Verás:
```json
{
  "_id": 1,
  "name": "Administrador",
  "email": "admin@lpstudios.com",
  "password": "admin123",
  "balance": 1000.0,
  "isAdmin": true
}

{
  "_id": 2,
  "name": "Usuario Ejemplo",
  "email": "user@example.com",
  "password": "user123",
  "balance": 100.0,
  "isAdmin": false
}
```

---

## 📦 ESTRUCTURA DE TU BASE DE DATOS

MongoDB funciona así:

```
MongoDB Server (localhost:27017)
│
└─ lp_ecommerce (Base de Datos)
   │
   ├─ users (Colección)
   │  ├─ Documento 1: Admin
   │  └─ Documento 2: Usuario
   │
   ├─ media (Colección) ← PRODUCTOS
   │  ├─ Documento 1: Summer Vibes
   │  ├─ Documento 2: Neon Dreams
   │  └─ Documento 3: Cyberpunk 2077
   │
   ├─ categories (Colección)
   │  └─ Documento 1: Música Electrónica
   │
   ├─ promotions (Colección - vacía por ahora)
   ├─ downloads (Colección - vacía)
   ├─ carts (Colección - vacía)
   ├─ ratings (Colección - vacía) ← Para calificaciones
   └─ gifts (Colección - vacía) ← Para regalos
```

---

## 🔄 EQUIVALENCIA SQL vs MongoDB

Si vienes de SQL Server, piensa así:

| SQL Server | MongoDB |
|------------|---------|
| Base de datos (lp_ecommerce) | Base de datos (lp_ecommerce) |
| Tabla (users) | Colección (users) |
| Fila/Registro | Documento (JSON) |
| Columna | Campo |
| PRIMARY KEY (id) | _id |

---

## ✅ VERIFICAR QUE FUNCIONA

En MongoDB Compass:

1. Conecta a `mongodb://localhost:27017`
2. Click en **`lp_ecommerce`**
3. Click en **`users`**
4. Deberías ver 2 documentos (usuarios)

Si NO ves la base de datos `lp_ecommerce`, es porque:
- El servidor no está corriendo (verifica: `Get-Service MongoDB`)
- La aplicación no se inició correctamente

---

## 🔧 ELIMINAR CONEXIÓN ANTIGUA

Si quieres limpiar esa conexión vieja:

1. En MongoDB Compass, ve a la lista de conexiones
2. Hover sobre "Cluster0.9cej8..."
3. Click en el ícono de **basura** 🗑️
4. Confirma

No afecta nada, es solo una conexión guardada.

---

## 📝 RESUMEN

- **Connection One** ← Tu servidor local (localhost:27017)
- **Cluster0...** ← Conexión antigua a MongoDB Atlas (puedes borrarla)
- **lp_ecommerce** ← Tu base de datos actual con todos los datos
- **Colecciones** ← Equivalente a tablas (users, media, categories, etc.)

---

¿Puedes ver la base de datos `lp_ecommerce` en MongoDB Compass ahora? 

Si NO la ves, dime qué conexiones aparecen y te ayudo a conectar correctamente. 🚀
