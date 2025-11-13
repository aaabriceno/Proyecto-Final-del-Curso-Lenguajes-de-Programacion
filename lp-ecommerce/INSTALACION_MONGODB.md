# 🗄️ Instalación y Configuración de MongoDB

## 📥 PASO 1: Descargar MongoDB

1. Ve a: https://www.mongodb.com/try/download/community
2. Selecciona:
   - **Version:** 7.0.x (o la más reciente)
   - **Platform:** Windows
   - **Package:** MSI
3. Click en **Download**

## 📦 PASO 2: Instalar MongoDB

1. Ejecuta el instalador `.msi`
2. Selecciona **"Complete"** installation
3. **IMPORTANTE:** Marca la opción **"Install MongoDB as a Service"**
4. Marca también **"Install MongoDB Compass"** (interfaz gráfica)
5. Click en **Install**
6. Espera a que termine (puede tardar 5-10 minutos)

## ✅ PASO 3: Verificar que MongoDB está corriendo

Abre PowerShell y ejecuta:

```powershell
# Verificar que el servicio está activo
Get-Service MongoDB

# Debería mostrar:
# Status   Name               DisplayName
# ------   ----               -----------
# Running  MongoDB            MongoDB Server
```

Si dice **"Stopped"**, inícialo:

```powershell
Start-Service MongoDB
```

## 🧪 PASO 4: Probar conexión

```powershell
# Conectarse a MongoDB Shell
mongosh

# Deberías ver:
# Current Mongosh Log ID: ...
# Connecting to: mongodb://127.0.0.1:27017
# test>
```

Dentro de `mongosh`, prueba:

```javascript
// Ver bases de datos
show dbs

// Salir
exit
```

## 📊 PASO 5: (Opcional) Usar MongoDB Compass

MongoDB Compass es una interfaz gráfica para ver tus datos:

1. Abre **MongoDB Compass** desde el menú inicio
2. Click en **Connect** (conexión por defecto: localhost:27017)
3. Verás tus bases de datos y colecciones

## 🚀 PASO 6: Actualizar tu proyecto

En tu proyecto, ya está todo configurado. Solo necesitas:

### 6.1 Descargar dependencias

```powershell
cd c:\Users\PC\Proyecto-Final-del-Curso-Lenguajes-de-Programacion\lp-ecommerce
sbt update
```

Esto descargará el driver de MongoDB (puede tardar 5-10 min la primera vez).

### 6.2 Reiniciar el servidor

```powershell
sbt run
```

Ahora verás:

```
🔍 Verificando si hay datos iniciales...
✅ Conexión a MongoDB exitosa
📦 Colecciones existentes: ...
📝 Insertando datos iniciales...
✅ Datos iniciales insertados correctamente
```

## 🔍 PASO 7: Verificar datos en MongoDB

### Opción A: MongoDB Compass (GUI)
1. Abre MongoDB Compass
2. Conecta a `localhost:27017`
3. Verás la base de datos `lp_ecommerce`
4. Explora las colecciones: `users`, `media`, `categories`

### Opción B: MongoDB Shell
```powershell
mongosh

use lp_ecommerce

// Ver usuarios
db.users.find()

// Ver productos
db.media.find()

// Ver categorías
db.categories.find()

// Contar documentos
db.users.countDocuments()
```

## 📝 Datos Iniciales

El sistema crea automáticamente:

### Usuarios:
- **Admin:** `admin@lpstudios.com` / `admin123`
- **Usuario:** `user@example.com` / `user123`

### Categoría:
- Música Electrónica

### Productos:
- Summer Vibes ($9.99)
- Neon Dreams ($12.99)
- Cyberpunk 2077 ($15.99)

## 🔧 Configuración Avanzada (Opcional)

### Cambiar puerto de MongoDB

Si necesitas usar otro puerto, edita:

`c:\Program Files\MongoDB\Server\7.0\bin\mongod.cfg`

```yaml
net:
  port: 27017  # Cambiar aquí
```

Luego reinicia el servicio:

```powershell
Restart-Service MongoDB
```

### Conectar a MongoDB Atlas (Cloud)

Si quieres usar MongoDB en la nube:

1. Crea cuenta en https://www.mongodb.com/cloud/atlas
2. Crea un cluster gratuito
3. Obtén tu connection string
4. En `app/db/MongoConnection.scala`, cambia:

```scala
private val uri = "mongodb+srv://usuario:password@cluster.mongodb.net/lp_ecommerce"
```

## ❓ Problemas Comunes

### Error: "MongoTimeoutException"
**Solución:** MongoDB no está corriendo.
```powershell
Start-Service MongoDB
```

### Error: "Access denied"
**Solución:** Ejecuta PowerShell como administrador.

### Error: "Command not found: mongosh"
**Solución:** Agrega MongoDB al PATH:
1. Busca: `C:\Program Files\MongoDB\Server\7.0\bin`
2. Agrégalo a las variables de entorno PATH

### Error al compilar en sbt
**Solución:** Asegúrate de tener internet para descargar dependencias.
```powershell
sbt clean
sbt update
sbt compile
```

## 🎯 Siguiente Paso

Una vez que MongoDB esté instalado y corriendo, continuaremos con:

1. ✅ Migrar `UserRepo` a MongoDB
2. ✅ Migrar `MediaRepo` a MongoDB
3. ✅ Implementar sistema de calificaciones
4. ✅ Implementar sistema de regalos
5. ✅ Implementar rankings

---

**¿MongoDB instalado y corriendo?** ✅

Avísame cuando veas el mensaje:
```
✅ Conexión a MongoDB exitosa
```

Entonces continuaremos migrando los repositorios. 🚀
