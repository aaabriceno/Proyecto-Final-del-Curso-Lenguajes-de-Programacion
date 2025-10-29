# 🐛 Errores Comunes y Soluciones

## 📋 Índice
1. [Errores de Compilación](#errores-de-compilación)
2. [Errores de Rutas](#errores-de-rutas)
3. [Errores de Vistas](#errores-de-vistas)
4. [Errores de Sesión](#errores-de-sesión)
5. [Errores de Base de Datos](#errores-de-base-de-datos)
6. [Errores de Dependencias](#errores-de-dependencias)

---

## 1. Errores de Compilación

### ❌ Error: "not found: value routes"

```scala
// ❌ INCORRECTO
Redirect(HomeController.index)

// ✅ CORRECTO
Redirect(routes.HomeController.index)
```

**Solución:** Siempre usar `routes.` antes del controlador.

---

### ❌ Error: "not found: type MessagesControllerComponents"

```scala
// ❌ INCORRECTO
class AuthController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc)

// ✅ CORRECTO
class AuthController @Inject()(cc: MessagesControllerComponents)
  extends MessagesAbstractController(cc)
```

**Solución:** Usar `MessagesControllerComponents` cuando usas formularios o i18n.

---

### ❌ Error: "type mismatch; found: Unit required: play.api.mvc.Result"

```scala
// ❌ INCORRECTO
def login = Action { req =>
  println("Login")  // No devuelve Result
}

// ✅ CORRECTO
def login = Action { req =>
  println("Login")
  Ok("Login page")  // Devuelve Result
}
```

**Solución:** Toda acción debe devolver un `Result` (Ok, Redirect, NotFound, etc.)

---

### ❌ Error: "could not find implicit value for parameter request"

```scala
// ❌ INCORRECTO - Falta implicit
def index = Action { req =>
  Ok(views.html.main_view())
}

// ✅ CORRECTO
def index = Action { implicit req =>  // ← Agregar implicit
  Ok(views.html.main_view())
}
```

**Solución:** Agregar `implicit` antes de `req` cuando las vistas lo necesiten.

---

## 2. Errores de Rutas

### ❌ Error: "Action not found"

```
# ❌ INCORRECTO - Falta parámetro de tipo
GET /shop/:id  controllers.ShopController.detail(id)

# ✅ CORRECTO
GET /shop/:id  controllers.ShopController.detail(id: Long)
```

**Solución:** Especificar el tipo del parámetro en `routes`.

---

### ❌ Error: "The uri path syntax is invalid"

```
# ❌ INCORRECTO - Espacios extra
GET   /shop      controllers.ShopController.list

# ✅ CORRECTO - Usar tabulación o espacios consistentes
GET     /shop              controllers.ShopController.list
```

**Solución:** Usar tabulación entre columnas en el archivo `routes`.

---

### ❌ Error: "duplicate key: GET /login"

```
# ❌ INCORRECTO - Ruta duplicada
GET /login  controllers.AuthController.loginForm
GET /login  controllers.AuthController.otherMethod

# ✅ CORRECTO - Rutas únicas
GET  /login   controllers.AuthController.loginForm
POST /login   controllers.AuthController.login
```

**Solución:** Cada combinación de método + URL debe ser única.

---

## 3. Errores de Vistas

### ❌ Error: "not found: value main"

```scala
@* ❌ INCORRECTO - No se importa main *@
@(usuario: User)
@main("Título") {
  <h1>Hola</h1>
}
```

**Solución:** Asegurarse de que `main.scala.html` existe en `app/views/`.

---

### ❌ Error: "not enough arguments for method apply"

```scala
@* Vista main.scala.html espera parámetros *@
@(title: String)(content: Html)

@* ❌ INCORRECTO - Falta title *@
@main {
  <h1>Contenido</h1>
}

@* ✅ CORRECTO *@
@main("Mi Título") {
  <h1>Contenido</h1>
}
```

**Solución:** Pasar todos los parámetros requeridos.

---

### ❌ Error: "could not find implicit value for parameter flash"

```scala
@* ❌ INCORRECTO *@
@()
@flash.get("success")

@* ✅ CORRECTO *@
@()(implicit flash: Flash)
@flash.get("success")
```

**Solución:** Declarar parámetros implícitos en la vista.

---

## 4. Errores de Sesión

### ❌ Error: Los datos de sesión se pierden

```scala
// ❌ INCORRECTO - No se guarda la sesión
def login = Action { req =>
  Redirect(routes.HomeController.index)
}

// ✅ CORRECTO - Guardar en sesión
def login = Action { req =>
  Redirect(routes.HomeController.index)
    .addingToSession("userEmail" -> email)
}
```

**Solución:** Usar `.addingToSession()` para guardar datos.

---

### ❌ Error: Flash message no aparece

```scala
// ❌ INCORRECTO - Flash sin usar en vista
def login = Action { req =>
  Redirect(routes.HomeController.index)
    .flashing("success" -> "Login exitoso")
}

// Vista no muestra el flash
@()
<h1>Inicio</h1>
```

```scala
// ✅ CORRECTO - Mostrar flash en vista
@()(implicit flash: Flash)

@flash.get("success").map { msg =>
  <div class="alert-success">@msg</div>
}

<h1>Inicio</h1>
```

**Solución:** Agregar código en la vista para mostrar flash messages.

---

## 5. Errores de Base de Datos

### ❌ Error: Datos desaparecen al reiniciar

```scala
// ❌ PROBLEMA - Datos en memoria
object UserRepo {
  private var users = Vector[User]()
}
```

**Solución:** Usar base de datos real (Slick, Anorm) o archivo persistente.

---

### ❌ Error: "duplicate key value violates unique constraint"

```scala
// ❌ PROBLEMA - Email duplicado
UserRepo.add("Juan", "juan@example.com", "555-1234", hash)
UserRepo.add("Pedro", "juan@example.com", "555-5678", hash)  // ← Error!
```

```scala
// ✅ SOLUCIÓN - Validar antes de agregar
def register(email: String, ...): Either[String, User] = {
  if (UserRepo.findByEmail(email).isDefined)
    Left("El correo ya está registrado")
  else
    Right(UserRepo.add(...))
}
```

**Solución:** Validar duplicados antes de insertar.

---

## 6. Errores de Dependencias

### ❌ Error: "object jbcrypt is not a member of package org.mindrot"

```scala
// build.sbt no tiene BCrypt
libraryDependencies ++= Seq(
  guice
)
```

```scala
// ✅ SOLUCIÓN - Agregar dependencia
libraryDependencies ++= Seq(
  guice,
  "org.mindrot" % "jbcrypt" % "0.4"  // ← Agregar esto
)
```

**Solución:** Agregar dependencia en `build.sbt` y ejecutar `sbt reload`.

---

### ❌ Error: "Server access Error: Connection refused"

```bash
# Puerto 9000 ya está en uso
```

**Solución:**
```bash
# Opción 1: Matar proceso en puerto 9000 (Windows)
netstat -ano | findstr :9000
taskkill /PID [número_proceso] /F

# Opción 2: Usar otro puerto
sbt "run 9001"
```

---

## 7. Errores de Formularios

### ❌ Error: Datos del formulario no se reciben

```scala
// ❌ INCORRECTO - No se parsea el body
def login = Action { req =>
  val email = req.body  // ← Esto no funciona
}
```

```scala
// ✅ CORRECTO
def login = Action { req =>
  val data = req.body.asFormUrlEncoded.getOrElse(Map.empty)
  val email = data.get("email").flatMap(_.headOption).getOrElse("")
}
```

**Solución:** Usar `asFormUrlEncoded` para leer datos del formulario.

---

### ❌ Error: Formulario envía GET en lugar de POST

```html
<!-- ❌ INCORRECTO - Falta method -->
<form action="/login">
  <input name="email">
  <button>Login</button>
</form>
```

```html
<!-- ✅ CORRECTO -->
<form method="POST" action="/login">
  <input name="email">
  <button type="submit">Login</button>
</form>
```

**Solución:** Agregar `method="POST"` al formulario.

---

## 8. Errores de Navegación

### ❌ Error: "Action Not Found" después de enviar formulario

```html
<!-- ❌ INCORRECTO - Ruta incorrecta -->
<form method="POST" action="/loginUser">
  ...
</form>
```

```
# routes no tiene POST /loginUser
POST /login  controllers.AuthController.login
```

**Solución:** Asegurarse de que la ruta en `action` coincida con `routes`.

```html
<!-- ✅ CORRECTO -->
<form method="POST" action="/login">
  ...
</form>
```

---

## 9. Errores de JavaScript

### ❌ Error: "Uncaught ReferenceError: $ is not defined"

```javascript
// ❌ INCORRECTO - jQuery no cargado
$(document).ready(function() {
  ...
});
```

```html
<!-- ✅ SOLUCIÓN - Cargar jQuery primero -->
<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script src="@routes.Assets.versioned("javascripts/main.js")"></script>
```

---

### ❌ Error: Archivo JS no se carga

```html
<!-- ❌ INCORRECTO - Ruta incorrecta -->
<script src="/javascripts/main.js"></script>
```

```html
<!-- ✅ CORRECTO - Usar routes.Assets -->
<script src="@routes.Assets.versioned("javascripts/main.js")"></script>
```

---

## 10. Debugging Tips

### 🔍 Ver qué datos recibe el controller

```scala
def login = Action { implicit req =>
  println("=" * 50)
  println("BODY: " + req.body)
  println("FORM DATA: " + req.body.asFormUrlEncoded)
  println("SESSION: " + req.session.data)
  println("=" * 50)
  ...
}
```

### 🔍 Ver variables en la vista

```scala
@(usuario: User)

@* Imprimir variables para debug *@
<!--
  Usuario ID: @usuario.id
  Usuario Email: @usuario.email
  Usuario Admin: @usuario.isAdmin
-->

<h1>Hola, @usuario.name</h1>
```

### 🔍 Ver logs en consola del navegador

```javascript
console.log("Valor de variable:", miVariable);
console.table(arrayDeObjetos);
console.error("Error:", error);
```

### 🔍 Verificar rutas disponibles

```bash
# En la terminal de sbt
sbt "show routes"
```

---

## 📝 Checklist de Debugging

Cuando algo no funciona, revisar:

- [ ] ¿La ruta está definida en `conf/routes`?
- [ ] ¿El método HTTP es correcto? (GET vs POST)
- [ ] ¿El controlador devuelve un `Result`?
- [ ] ¿Los parámetros implícitos están declarados?
- [ ] ¿El formulario tiene `method="POST"`?
- [ ] ¿El `action` del formulario coincide con `routes`?
- [ ] ¿Los nombres de los campos coinciden (`name="email"` en HTML)?
- [ ] ¿Se guarda la sesión con `.addingToSession()`?
- [ ] ¿La vista muestra los flash messages?
- [ ] ¿Las dependencias están en `build.sbt`?

---

## 🆘 Recursos de Ayuda

### Documentación oficial:
- [Play Framework](https://www.playframework.com/documentation)
- [Scala Docs](https://docs.scala-lang.org/)
- [Twirl Templates](https://github.com/playframework/twirl)

### Stack Overflow:
- Buscar: "play framework [tu error]"
- Tag: `playframework`, `scala`

### Logs:
- Ver `logs/application.log` para errores
- Console de sbt muestra errores de compilación
- Console del navegador (F12) muestra errores JS

---

**¡Con esta guía deberías poder resolver la mayoría de errores comunes! 🚀**
