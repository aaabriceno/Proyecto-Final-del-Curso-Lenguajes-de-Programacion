# 🎨 Arquitectura Visual del Proyecto

## 📊 Diagrama MVC - Flujo Completo

```
┌─────────────────────────────────────────────────────────────────┐
│                         NAVEGADOR                                │
│  Usuario escribe: http://localhost:9000/login                   │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                      conf/routes                                 │
│  GET  /login  →  controllers.AuthController.loginForm           │
│  POST /login  →  controllers.AuthController.login               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              CONTROLLER (AuthController.scala)                   │
│                                                                  │
│  def loginForm = Action { implicit req =>                       │
│    Ok(views.html.login())  // Renderiza vista                   │
│  }                                                               │
│                                                                  │
│  def login = Action { implicit req =>                           │
│    // 1. Extrae datos del formulario                            │
│    val email = req.body...                                      │
│    val password = req.body...                                   │
│                                                                  │
│    // 2. Llama al servicio                                      │
│    userService.authenticate(email, password) match {            │
│      case Some(user) => /* Crear sesión */                      │
│      case None => /* Error */                                   │
│    }                                                             │
│  }                                                               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              SERVICE (UserService.scala)                         │
│                                                                  │
│  def authenticate(email: String, password: String) = {          │
│    // 1. Busca usuario en el repositorio                        │
│    UserRepo.findByEmail(email)                                  │
│                                                                  │
│    // 2. Verifica contraseña con BCrypt                         │
│    .filter(u => BCrypt.checkpw(password, u.passwordHash))       │
│  }                                                               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                MODEL (User.scala)                                │
│                                                                  │
│  case class User(                                                │
│    id: Long,                                                     │
│    name: String,                                                 │
│    email: String,                                                │
│    passwordHash: String,                                         │
│    isAdmin: Boolean                                              │
│  )                                                               │
│                                                                  │
│  object UserRepo {                                               │
│    private var users = Vector[User](...)                        │
│                                                                  │
│    def findByEmail(email: String): Option[User] =               │
│      users.find(_.email == email)                               │
│  }                                                               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                  VIEW (login.scala.html)                         │
│                                                                  │
│  @()                                                             │
│  @main("Login") {                                                │
│    <form method="POST" action="/login">                         │
│      <input name="email" type="email">                          │
│      <input name="password" type="password">                    │
│      <button>Iniciar Sesión</button>                            │
│    </form>                                                       │
│  }                                                               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
              HTML enviado al NAVEGADOR
```

---

## 🔄 Ciclo de Vida de una Petición

### Ejemplo 1: GET /shop (Ver productos)

```
1. Usuario → Navegador
   └─→ http://localhost:9000/shop

2. Play Framework → routes
   └─→ GET /shop → ShopController.list

3. ShopController
   ├─→ items = MediaRepo.all        // Obtiene todos los productos
   └─→ Ok(views.html.media_list(items))  // Renderiza vista

4. media_list.scala.html
   ├─→ Recibe lista de productos
   └─→ Genera HTML con @for(item <- items) { ... }

5. HTML → Navegador
   └─→ Usuario ve la lista de productos
```

---

### Ejemplo 2: POST /login (Iniciar sesión)

```
1. Usuario → Formulario
   ├─→ Email: admin@lpstudios.com
   └─→ Password: admin123

2. Formulario → POST /login

3. AuthController.login
   ├─→ Extrae email y password del formulario
   └─→ Llama a userService.authenticate(email, password)

4. UserService.authenticate
   ├─→ Busca usuario: UserRepo.findByEmail(email)
   └─→ Verifica password: BCrypt.checkpw(password, hash)

5. ¿Usuario encontrado y password correcta?
   ├─→ SÍ: Crear sesión y redirigir a /
   │        .addingToSession("userEmail" -> email)
   │
   └─→ NO: Volver a /login con mensaje de error
            .flashing("error" -> "Credenciales inválidas")
```

---

## 📂 Estructura de Carpetas Explicada

```
lp-ecommerce/
│
├── app/                          ← Código fuente principal
│   │
│   ├── controllers/              ← Manejan peticiones HTTP
│   │   ├── AuthController.scala      🔐 Login, Registro, Logout
│   │   ├── HomeController.scala      🏠 Página principal
│   │   ├── ShopController.scala      🛒 Tienda de productos
│   │   └── PagesController.scala     📄 Páginas adicionales
│   │
│   ├── models/                   ← Clases de datos
│   │   ├── User.scala                👤 Usuario + UserRepo (BD)
│   │   └── Media.scala               🎬 Producto + MediaRepo (BD)
│   │
│   ├── services/                 ← Lógica de negocio
│   │   └── UserService.scala         ⚙️ Autenticación, registro
│   │
│   └── views/                    ← Templates HTML
│       ├── main.scala.html           📐 Layout base (navbar, footer)
│       ├── main_view.scala.html      🏠 Página de inicio
│       ├── login.scala.html          🔑 Formulario de login
│       ├── register.scala.html       📝 Formulario de registro
│       ├── media_list.scala.html     📋 Lista de productos
│       └── media_detail.scala.html   🔍 Detalle de producto
│
├── conf/                         ← Configuración
│   ├── application.conf              ⚙️ Config de Play, BD, etc.
│   ├── routes                        🛣️ Mapeo de URLs
│   └── messages                      🌐 Traducciones (i18n)
│
├── public/                       ← Archivos estáticos
│   ├── javascripts/                  💻 Archivos JS
│   ├── stylesheets/                  🎨 Archivos CSS
│   └── images/                       🖼️ Imágenes
│
├── target/                       ← Archivos compilados (ignorar)
├── project/                      ← Config de SBT (ignorar)
├── build.sbt                     📦 Dependencias del proyecto
└── build.sc                      🔨 Configuración de compilación
```

---

## 🔗 Relaciones entre Componentes

```
┌─────────────────────────────────────────────────────────────────┐
│                        TU APLICACIÓN                             │
└─────────────────────────────────────────────────────────────────┘

                           ┌─────────┐
                           │ routes  │
                           │         │
                           └────┬────┘
                                │
                ┌───────────────┼───────────────┐
                │               │               │
                ▼               ▼               ▼
         ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
         │   Auth      │ │    Home     │ │    Shop     │
         │ Controller  │ │ Controller  │ │ Controller  │
         └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
                │               │               │
                ▼               │               ▼
         ┌─────────────┐        │        ┌─────────────┐
         │    User     │        │        │   Media     │
         │   Service   │        │        │    Repo     │
         └──────┬──────┘        │        └─────────────┘
                │               │
                ▼               │
         ┌─────────────┐        │
         │    User     │        │
         │    Repo     │        │
         └─────────────┘        │
                                │
                ┌───────────────┼───────────────┐
                │               │               │
                ▼               ▼               ▼
         ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
         │   login     │ │  main_view  │ │ media_list  │
         │  .scala.html│ │ .scala.html │ │ .scala.html │
         └─────────────┘ └─────────────┘ └─────────────┘
```

---

## 🎯 Flujo de Datos: Registro de Usuario

```
┌─────────────────────────────────────────────────────────────────┐
│  PASO 1: Usuario llena formulario en register.scala.html        │
│  ┌────────────────────────────────────────┐                     │
│  │ Nombre:    Juan Pérez                  │                     │
│  │ Email:     juan@example.com            │                     │
│  │ Teléfono:  555-1234                    │                     │
│  │ Password:  mipassword123               │                     │
│  │ [ Registrar ]                          │                     │
│  └────────────────────────────────────────┘                     │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ POST /register
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  PASO 2: AuthController.register recibe datos                   │
│                                                                  │
│  val name  = "Juan Pérez"                                       │
│  val email = "juan@example.com"                                 │
│  val phone = "555-1234"                                         │
│  val pass  = "mipassword123"                                    │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ Llama a UserService
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  PASO 3: UserService.register valida y crea usuario             │
│                                                                  │
│  1. ¿Ya existe juan@example.com?                                │
│     └─→ UserRepo.findByEmail("juan@example.com")               │
│                                                                  │
│  2. No existe, encripta password:                               │
│     └─→ hash = BCrypt.hashpw("mipassword123", BCrypt.gensalt()) │
│                                                                  │
│  3. Crea usuario:                                               │
│     └─→ UserRepo.add("Juan Pérez", "juan@...", "555-1234", hash)│
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ Devuelve Right(usuario)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  PASO 4: AuthController crea sesión y redirige                  │
│                                                                  │
│  Redirect(routes.AuthController.account)                        │
│    .addingToSession("userEmail" -> "juan@example.com")          │
│    .flashing("success" -> "Registro exitoso")                   │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  PASO 5: Usuario redirigido a /account (ya autenticado)         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔒 Seguridad: Hashing de Contraseñas

```
┌─────────────────────────────────────────────────────────────────┐
│               ❌ NUNCA HAGAS ESTO ❌                             │
│                                                                  │
│  case class User(                                                │
│    ...                                                           │
│    password: String  // ← Contraseña en texto plano             │
│  )                                                               │
│                                                                  │
│  // Si alguien hackea tu BD, ve todas las contraseñas          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│               ✅ CORRECTO (BCrypt) ✅                            │
│                                                                  │
│  case class User(                                                │
│    ...                                                           │
│    passwordHash: String  // ← Hash irreversible                 │
│  )                                                               │
│                                                                  │
│  // Al registrar:                                               │
│  val hash = BCrypt.hashpw("admin123", BCrypt.gensalt())         │
│  // hash = "$2a$10$xyzABC..." (no se puede desencriptar)        │
│                                                                  │
│  // Al hacer login:                                             │
│  BCrypt.checkpw("admin123", hash)  // ← Compara sin desencriptar│
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 Dependencias Importantes (build.sbt)

```scala
libraryDependencies ++= Seq(
  guice,                    // ← Inyección de dependencias
  
  // BCrypt para encriptar contraseñas
  "org.mindrot" % "jbcrypt" % "0.4",
  
  // Templates Twirl (views/*.scala.html)
  "com.typesafe.play" %% "play-twirl" % "1.5.1",
  
  // Testing
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
)
```

---

## 🛠️ Comandos Útiles

```bash
# Ejecutar proyecto (modo desarrollo)
sbt run
# → http://localhost:9000

# Compilar proyecto
sbt compile

# Ejecutar tests
sbt test

# Abrir consola de Scala
sbt console

# Limpiar archivos compilados
sbt clean

# Ver dependencias
sbt dependencyTree
```

---

## 🐛 Debugging Tips

### Ver qué datos recibe el controller:
```scala
def login = Action { implicit req =>
  val data = req.body.asFormUrlEncoded.getOrElse(Map.empty)
  println(s"[DEBUG] Datos recibidos: $data")  // ← Imprime en consola
  ...
}
```

### Ver sesión actual:
```scala
def account = Action { implicit req =>
  println(s"[DEBUG] Sesión: ${req.session.data}")  // ← Ver sesión
  ...
}
```

### Ver flash messages:
```scala
@()(implicit req: RequestHeader, flash: Flash)

@flash.get("success").map { msg =>
  <div class="alert-success">@msg</div>
}

@flash.get("error").map { msg =>
  <div class="alert-error">@msg</div>
}
```

---

**¡Con esto deberías tener mucho más claro cómo funciona tu proyecto! 🎓**
