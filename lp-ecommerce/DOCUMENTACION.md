# Documentación del Proyecto E-Commerce en Scala

## Descripción General
Este proyecto es un sistema de comercio electrónico implementado desde cero en Scala, sin usar frameworks web externos. Utiliza únicamente la librería estándar de Scala, `java.net.ServerSocket` para el servidor HTTP, y MongoDB para la persistencia de datos.

**Características principales:**
- Servidor HTTP manual (sin Play Framework, Akka HTTP, etc.)
- Autenticación de usuarios con sesiones
- Panel de administración completo
- Gestión de productos, categorías jerárquicas, promociones
- Carrito de compras y sistema de pagos
- API JSON para integraciones
- Base de datos MongoDB

## Arquitectura del Proyecto

### Estructura de Carpetas
```
lp-ecommerce/
├── app/                          # Código fuente principal
│   ├── controllers/              # Controladores HTTP
│   │   ├── AdminController.scala # Panel de admin
│   │   ├── AuthController.scala  # Autenticación
│   │   ├── HomeController.scala  # Páginas públicas
│   │   ├── ShopController.scala  # Tienda y carrito
│   │   └── UserController.scala  # Perfil de usuario
│   ├── db/                       # Conexión a base de datos
│   │   └── MongoConnection.scala # Configuración MongoDB
│   ├── http/                     # Servidor HTTP manual
│   │   ├── HttpRequest.scala     # Parsing de requests
│   │   ├── HttpResponse.scala    # Generación de responses
│   │   └── HttpServer.scala      # Servidor principal
│   ├── models/                   # Modelos de datos
│   │   ├── Media.scala           # Modelo de productos
│   │   ├── User.scala            # Modelo de usuarios
│   │   ├── Category.scala        # Modelo de categorías
│   │   ├── Promotion.scala       # Modelo de promociones
│   │   └── BalanceRequest.scala  # Modelo de solicitudes de saldo
│   ├── scripts/                  # Scripts de migración
│   │   ├── ReorganizeCategories.scala
│   │   └── UpdateProductsAndPromotions.scala
│   ├── services/                 # Servicios y lógica de negocio
│   │   └── Main.scala            # Punto de entrada
│   ├── session/                  # Gestión de sesiones
│   │   └── SessionManager.scala
│   └── views/                    # Vistas HTML
├── public/                       # Archivos estáticos
│   ├── images/                   # Imágenes de productos
│   ├── javascripts/              # Scripts JS
│   └── stylesheets/              # CSS
├── conf/                         # Configuración
│   ├── application.conf          # Config Play-like
│   └── routes                    # Definición de rutas
└── project/                      # Configuración SBT
```

## Flujo de la Aplicación

### 1. Inicio del Servidor (`Main.scala`)
El archivo `Main.scala` es el punto de entrada que ejecuta `sbt run`. Su flujo es:

1. **Mostrar banner** con información del proyecto
2. **Conectar a MongoDB** y verificar conexión
3. **Inicializar datos** si la BD está vacía (productos, usuarios de ejemplo)
4. **Ejecutar scripts de migración**:
   - `ReorganizeCategories.run()`: Organiza categorías en árbol jerárquico
   - `UpdateProductsAndPromotions.run()`: Actualiza productos con nuevas categorías
5. **Limpiar datos corruptos** (opcional, para desarrollo)
6. **Actualizar imágenes de productos** (setea `coverImage` en productos existentes)
7. **Configurar cierre limpio** (Ctrl+C)
8. **Iniciar servidor HTTP** en puerto 9000

### 2. Servidor HTTP (`HttpServer.scala`)
- Implementa un servidor HTTP básico usando `java.net.ServerSocket`
- Escucha en puerto 9000
- Parsea requests HTTP y los enruta a controladores
- Maneja respuestas HTTP con headers, cookies, etc.

### 3. Enrutamiento (`routes` y `HttpServer.scala`)
Las rutas se definen en `conf/routes` y se mapean a métodos de controladores:

**Rutas Públicas:**
- `GET /` → `HomeController.index`
- `GET /login` → `AuthController.loginForm`
- `POST /login` → `AuthController.login`
- `GET /register` → `AuthController.registerForm`
- `POST /register` → `AuthController.register`
- `GET /shop` → `ShopController.index`
- `GET /api/media` → `AdminController.mediaJson` (público)

**Rutas de Usuario (requieren login):**
- `GET /user/account` → `UserController.account`
- `POST /cart/add` → `ShopController.addToCart`
- `GET /cart` → `ShopController.viewCart`

**Rutas de Admin (requieren admin):**
- `GET /admin` → `AdminController.dashboard`
- `GET /admin/media` → `AdminController.media`
- `POST /admin/media` → `AdminController.createMedia`
- `GET /admin/users` → `AdminController.users`
- `GET /api/users` → `AdminController.usersJson`

## Controladores Principales

### AuthController
**Responsabilidades:**
- Formularios de login/registro
- Validación de credenciales
- Gestión de sesiones con cookies
- Verificación de permisos (admin/usuario)

**Métodos clave:**
- `login()`: Autentica usuario y crea sesión
- `requireAuth()`: Verifica sesión activa
- `requireAdmin()`: Verifica permisos de admin

### HomeController
**Responsabilidades:**
- Página principal con navbar dinámico
- Detección de sesión para mostrar contenido personalizado

**Métodos clave:**
- `index()`: Muestra home con navbar según rol del usuario

### ShopController
**Responsabilidades:**
- Listado de productos en tienda
- Gestión del carrito de compras
- Proceso de compra/pago

**Métodos clave:**
- `index()`: Lista productos con filtros y promociones
- `addToCart()`: Agrega productos al carrito
- `viewCart()`: Muestra carrito actual
- `checkout()`: Procesa compra

### UserController
**Responsabilidades:**
- Perfil de usuario
- Historial de compras
- Gestión de saldo

**Métodos clave:**
- `account()`: Muestra información del usuario
- `downloads()`: Historial de descargas

### AdminController
**Responsabilidades:**
- Panel completo de administración
- CRUD de productos, categorías, promociones, usuarios
- API JSON para frontend
- Estadísticas y reportes

**Métodos clave:**
- `dashboard()`: Dashboard principal
- `media()`: Gestión de productos
- `createMedia()`: Crear producto
- `updateMedia()`: Editar producto
- `categoriesJson()`: API de categorías
- `promotions()`: Gestión de promociones
- `usersJson()`: API de usuarios

## Modelos de Datos

### Media (Producto)
**Campos principales:**
- `id`: Long (único)
- `title`: String
- `description`: String
- `productType`: ProductType (digital/físico)
- `categoryId`: Option[Long]
- `assetPath`: String (ruta al archivo/contenido)
- `price`: BigDecimal
- `stock`: Int
- `rating`: Double
- `downloads`: Int

**Funciones clave:**
- `mediaToDoc()`: Convierte a documento MongoDB
- `docToMedia()`: Convierte de documento MongoDB

### User
**Campos principales:**
- `id`: Long
- `name`: String
- `email`: String
- `password`: String (hasheado)
- `isAdmin`: Boolean
- `balance`: BigDecimal
- `totalSpent`: BigDecimal

### Category
**Campos principales:**
- `id`: Long
- `name`: String
- `parentId`: Option[Long] (para jerarquía)
- `description`: String
- `productType`: String

**Funciones clave:**
- `getBreadcrumb()`: Obtiene ruta completa en árbol

### Promotion
**Campos principales:**
- `id`: Long
- `name`: String
- `discountPercent`: Int
- `startDate/endDate`: LocalDateTime
- `targetType`: PromotionTarget (Product/Category)
- `targetIds`: Vector[Long]

## Base de Datos MongoDB

### Colecciones
- `media`: Productos
- `users`: Usuarios
- `categories`: Categorías
- `promotions`: Promociones
- `balance_requests`: Solicitudes de carga de saldo
- `sessions`: Sesiones activas

### Conexión (`MongoConnection.scala`)
- Configura cliente MongoDB
- Inicializa datos de ejemplo
- Proporciona acceso a colecciones
- Maneja migraciones

## Vistas y Frontend

### Estructura
- Archivos HTML en `app/views/`
- CSS en `public/stylesheets/`
- JavaScript en `public/javascripts/`
- Imágenes en `public/images/`

### Características
- Navbar dinámico según rol
- Formularios con validación
- AJAX para operaciones asíncronas
- Bootstrap para estilos

## Scripts de Migración

### ReorganizeCategories
- Reorganiza categorías en estructura de árbol
- Asegura integridad referencial

### UpdateProductsAndPromotions
- Actualiza productos con nuevas categorías
- Migra promociones a nuevo formato

## Cómo Ejecutar el Proyecto

### Prerrequisitos
1. **Scala y SBT**: Instalar desde https://www.scala-lang.org/download/
2. **MongoDB**: Seguir `INSTALACION_MONGODB.md`
3. **Dependencias**: Ejecutar `sbt compile`

### Ejecución
```bash
# Iniciar MongoDB
Start-Service MongoDB  # Windows PowerShell

# Ejecutar servidor
sbt run
```

### Acceso
- **Aplicación**: http://localhost:9000
- **Admin**: Usuario admin con email `admin@example.com`, password `admin123`

## Funcionalidades Implementadas

### Completadas ✅
- Sistema de usuarios y autenticación
- Gestión completa de productos
- Categorías jerárquicas
- Promociones con descuentos
- Carrito de compras básico
- Panel de admin completo
- API JSON para productos
- Gestión de saldo y solicitudes

### Pendientes ❌ (según PDF original)
- Historial de descargas con descuentos
- Sistema de regalos entre usuarios
- Calificaciones de productos (1-10)
- Rankings de productos más descargados/calificados
- Consultas avanzadas (últimos 10 descargados, etc.)
- Cierre de cuenta de usuario

## Notas de Desarrollo

### Decisiones Arquitectónicas
- **Sin frameworks**: Implementación desde cero para aprendizaje
- **MongoDB**: Base de datos NoSQL para flexibilidad
- **Sesiones con cookies**: Gestión manual de estado
- **JSON manual**: Sin librerías externas para serialización

### Patrones Usados
- **MVC**: Modelos, Vistas, Controladores
- **Repository**: Capa de acceso a datos
- **Singleton**: Objetos Scala para servicios
- **Try/Success/Failure**: Manejo de errores funcional

### Consideraciones de Seguridad
- Passwords hasheados
- Validación de inputs
- Verificación de permisos
- Escape de HTML/JSON para prevenir XSS

---

*Este documento se generó automáticamente analizando el código fuente. Para actualizaciones, modificar este archivo o regenerarlo con herramientas de análisis de código.*