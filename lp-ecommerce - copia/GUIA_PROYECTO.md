# 📖 GUÍA COMPLETA DEL PROYECTO E-COMMERCE

## 🏗️ Arquitectura del Proyecto (MVC)

### 1️⃣ **MODELS (Modelos)** - `app/models/`
**¿Qué son?** Representan los datos de tu aplicación.

#### `User.scala`
```scala
case class User(
  id: Long,           // Identificador único
  name: String,       // Nombre del usuario
  email: String,      // Correo (usado para login)
  phone: String,      // Teléfono
  passwordHash: String, // Contraseña encriptada con BCrypt
  isAdmin: Boolean,   // ¿Es administrador?
  isActive: Boolean   // ¿Cuenta activa?
)
```
**UserRepo**: "Base de datos" en memoria (los datos se pierden al reiniciar)
- `all`: Devuelve todos los usuarios
- `findByEmail`: Busca usuario por correo
- `add`: Agrega nuevo usuario

#### `Media.scala`
```scala
case class Media(
  id: Long,           // ID del producto
  title: String,      // Nombre del producto
  description: String, // Descripción
  mtype: MediaType,   // Tipo: Image, Audio o Video
  price: BigDecimal,  // Precio
  rating: Double,     // Calificación (1-5)
  downloads: Int,     // Número de descargas
  assetPath: String   // Ruta del archivo (ej: "images/hero.jpg")
)
```

---

### 2️⃣ **CONTROLLERS (Controladores)** - `app/controllers/`
**¿Qué son?** Manejan las peticiones HTTP y coordinan todo.

#### `AuthController.scala` 🔐
- **GET /login** → Muestra formulario de login
- **POST /login** → Valida credenciales y crea sesión
- **GET /register** → Muestra formulario de registro
- **POST /register** → Crea nueva cuenta
- **GET /account** → Muestra perfil del usuario
- **GET /logout** → Cierra sesión

**Flujo de Login:**
```
Usuario envía email + password
    ↓
AuthController.login (POST)
    ↓
UserService.authenticate() 
    ↓
¿Correcto? → Crea sesión y redirige a /
¿Incorrecto? → Vuelve a /login con mensaje de error
```

#### `HomeController.scala` 🏠
- **GET /** → Muestra la página principal (`main_view.scala.html`)

#### `ShopController.scala` 🛒
- **GET /shop** → Lista todos los productos
- **GET /shop/:id** → Muestra detalle de un producto

---

### 3️⃣ **SERVICES (Servicios)** - `app/services/`
**¿Qué son?** Lógica de negocio reutilizable.

#### `UserService.scala`
- **register()**: Valida y crea usuario (hashea la contraseña con BCrypt)
- **authenticate()**: Verifica email + password para login

**¿Por qué separar esto?**
- El controlador solo coordina
- El servicio tiene la lógica compleja
- Más fácil de testear y reutilizar

---

### 4️⃣ **VIEWS (Vistas)** - `app/views/`
**¿Qué son?** Templates HTML con código Scala embebido.

#### Sintaxis básica:
```scala
@* Comentario *@
@(usuario: User)  @* Parámetros que recibe *@

<h1>Hola, @usuario.name</h1>

@if(usuario.isAdmin) {
  <p>Eres administrador</p>
}

@for(item <- items) {
  <div>@item.title</div>
}
```

#### Vistas principales:
- `main.scala.html`: Layout base (navbar, footer, etc.)
- `main_view.scala.html`: Página de inicio
- `login.scala.html`: Formulario de login
- `register.scala.html`: Formulario de registro
- `media_list.scala.html`: Lista de productos
- `media_detail.scala.html`: Detalle de producto

---

### 5️⃣ **ROUTES (Rutas)** - `conf/routes`
**¿Qué es?** Mapea URLs a funciones del controlador.

```
Método  URL          Controlador.función
GET     /            HomeController.index
GET     /login       AuthController.loginForm
POST    /login       AuthController.login
GET     /shop        ShopController.list
GET     /shop/:id    ShopController.detail(id: Long)
```

---

## 🔄 Flujo Completo de una Petición

### Ejemplo: Usuario visita `/shop`

```
1. Usuario escribe en navegador: http://localhost:9000/shop

2. Play Framework lee conf/routes:
   GET /shop → ShopController.list

3. ShopController.list ejecuta:
   - Obtiene todos los productos: MediaRepo.all
   - Renderiza la vista: Ok(views.html.media_list(items))

4. Vista media_list.scala.html:
   - Recibe la lista de productos
   - Genera HTML con los datos
   - Devuelve HTML al navegador

5. Navegador muestra la página
```

---

## 🚨 Problemas Actuales

### ❌ Problema #1: Datos se pierden al reiniciar
```scala
// En UserRepo y MediaRepo:
private var users = Vector[User](...)
```
**Solución**: Usar una base de datos real (H2, PostgreSQL, MySQL)

### ❌ Problema #2: No hay validación de formularios
```scala
// Actualmente:
val email = data.get("email").flatMap(_.headOption).getOrElse("").trim
```
**Solución**: Usar Play Forms con validaciones

### ❌ Problema #3: Sesiones inseguras
```scala
private val SessionKey = "userEmail"
```
**Solución**: Guardar ID en lugar de email, usar tokens JWT

---

## ✅ Mejoras Sugeridas

### 1. **Agregar validación de formularios**
```scala
import play.api.data._
import play.api.data.Forms._

case class LoginForm(email: String, password: String)

val loginForm = Form(
  mapping(
    "email" -> email,
    "password" -> nonEmptyText(minLength = 6)
  )(LoginForm.apply)(LoginForm.unapply)
)
```

### 2. **Agregar carrito de compras**
Crear:
- `models/Cart.scala`
- `models/CartItem.scala`
- `controllers/CartController.scala`

### 3. **Agregar transacciones/órdenes**
Crear:
- `models/Order.scala`
- `models/OrderItem.scala`
- `controllers/OrderController.scala`

### 4. **Mejorar manejo de sesiones**
```scala
// Guardar objeto completo en sesión
implicit def userFromSession(implicit req: Request[AnyContent]): Option[User] = {
  req.session.get("userId").flatMap(id => UserRepo.findById(id.toLong))
}
```

### 5. **Agregar base de datos real**
Usar Slick o Anorm:
```scala
// build.sbt
libraryDependencies += "com.typesafe.play" %% "play-slick" % "5.0.0"
libraryDependencies += "com.h2database" % "h2" % "1.4.200"
```

---

## 📁 Estructura de Archivos Explicada

```
app/
├── controllers/        ← Manejan peticiones HTTP
│   ├── AuthController.scala    (Login, Registro, Logout)
│   ├── HomeController.scala    (Página principal)
│   ├── ShopController.scala    (Tienda, productos)
│   └── PagesController.scala   (Otras páginas)
│
├── models/            ← Representan datos
│   ├── User.scala              (Usuario + UserRepo)
│   └── Media.scala             (Producto + MediaRepo)
│
├── services/          ← Lógica de negocio
│   └── UserService.scala       (Autenticación, registro)
│
└── views/             ← Templates HTML
    ├── main.scala.html         (Layout base)
    ├── login.scala.html        (Formulario login)
    └── media_list.scala.html   (Lista productos)

conf/
├── application.conf   ← Configuración de Play
└── routes             ← Mapeo de URLs

public/
├── javascripts/       ← Archivos JS del frontend
├── stylesheets/       ← Archivos CSS
└── images/            ← Imágenes estáticas
```

---

## 🎯 Próximos Pasos Recomendados

### Paso 1: Entender el flujo completo
1. Ejecuta el proyecto: `sbt run`
2. Visita `http://localhost:9000/register`
3. Crea una cuenta
4. Observa cómo fluyen los datos:
   - Formulario → AuthController → UserService → UserRepo

### Paso 2: Agregar funcionalidad de carrito
1. Crear `models/Cart.scala`
2. Crear `controllers/CartController.scala`
3. Agregar rutas en `conf/routes`
4. Crear vistas para el carrito

### Paso 3: Implementar órdenes/compras
1. Crear `models/Order.scala`
2. Guardar órdenes cuando usuario "compra"
3. Mostrar historial de compras

### Paso 4: Migrar a base de datos real
1. Agregar Slick en `build.sbt`
2. Crear tablas en `conf/evolutions`
3. Reemplazar `UserRepo` y `MediaRepo` por DAOs

---

## 🆘 Preguntas Frecuentes

**P: ¿Por qué mis datos desaparecen al reiniciar?**
R: Porque usas `Vector` en memoria. Necesitas una base de datos.

**P: ¿Qué es BCrypt?**
R: Librería para encriptar contraseñas de forma segura (no se pueden desencriptar).

**P: ¿Cómo paso datos de Controller a Vista?**
R: Como parámetros:
```scala
// Controller
Ok(views.html.media_detail(producto))

// Vista
@(producto: Media)
<h1>@producto.title</h1>
```

**P: ¿Qué diferencia hay entre Service y Controller?**
R: 
- **Controller**: Recibe petición HTTP, delega trabajo, devuelve respuesta
- **Service**: Contiene lógica de negocio reutilizable

---

## 📚 Recursos Útiles

- [Play Framework Docs](https://www.playframework.com/documentation)
- [Scala Docs](https://docs.scala-lang.org/)
- [Twirl Templates](https://www.playframework.com/documentation/2.8.x/ScalaTemplates)

---

**¡Éxito con tu proyecto! 🚀**
