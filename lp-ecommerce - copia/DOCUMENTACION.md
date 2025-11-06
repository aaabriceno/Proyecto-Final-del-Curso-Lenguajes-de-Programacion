# Documentación del Sistema E-Commerce LP Studios

## Descripción General

Sistema de comercio electrónico desarrollado en Scala con Play Framework 2.8.x. Permite la venta de contenido multimedia (audio, video, imágenes) con gestión de usuarios, sistema de saldo virtual, carrito de compras, notificaciones en tiempo real y sistema de promociones.

---

## Arquitectura del Sistema

### Stack Tecnológico
- **Backend**: Scala 2.13 con Play Framework 2.8.x
- **Frontend**: HTML5, Bootstrap 5.3.3, JavaScript ES6+
- **Persistencia**: In-memory (Vector) con sincronización para concurrencia
- **Autenticación**: Session-based (clave: "userEmail")
- **API REST**: Endpoints JSON para notificaciones y saldo

### Patrones de Diseño
- **Repository Pattern**: Cada modelo tiene su repositorio (UserRepo, MediaRepo, etc.)
- **MVC**: Separación clara entre Models, Views y Controllers
- **Action Composition**: UserAction y AdminAction para autorización
- **Synchronized Blocks**: Control de concurrencia en operaciones críticas

---

## Módulo 1: Gestión de Usuarios

### Modelo User (app/models/User.scala)

**Campos principales:**
- `id: Long` - Identificador único
- `email: String` - Email único (usado para login)
- `password: String` - Contraseña hasheada
- `name: String` - Nombre completo
- `balance: BigDecimal` - Saldo virtual disponible
- `totalSpent: BigDecimal` - Total gastado histórico
- `isAdmin: Boolean` - Flag de administrador
- `createdAt: LocalDateTime` - Fecha de registro

**Métodos clave:**
- `isVip: Boolean` - Retorna true si totalSpent >= 100
- `vipDiscount: BigDecimal` - Calcula 20% de descuento si es VIP

**UserRepo - Operaciones:**
- `create(email, password, name)` - Registro de nuevo usuario
- `findByEmail(email)` - Búsqueda por email (login)
- `authenticate(email, password)` - Validación de credenciales
- `addBalance(userId, amount)` - Agregar saldo (usado en aprobación de recargas)
- `deductBalance(userId, amount)` - Deducir saldo (usado en checkout)
- `updateTotalSpent(userId, amount)` - Actualizar gasto total
- `all` - Listar todos los usuarios
- `delete(id)` - Eliminar usuario

**Funcionalidad VIP:**
El sistema otorga automáticamente estatus VIP cuando un usuario alcanza $100 en compras totales. Los usuarios VIP reciben 20% de descuento en todas sus compras, PERO las promociones tienen prioridad (si hay promoción activa, se aplica esa en lugar del descuento VIP).

**Usuarios semilla:**
- Admin: admin@lp.com / admin123 (isAdmin=true, balance=$1000)
- Usuario normal: user@lp.com / user123 (balance=$200)

---

## Módulo 2: Sistema de Autenticación

### AuthController (app/controllers/AuthController.scala)

**Endpoints públicos:**
- `GET /login` - Muestra formulario de login
- `POST /login` - Procesa login y crea sesión con clave "userEmail"
- `GET /register` - Muestra formulario de registro
- `POST /register` - Crea nuevo usuario
- `GET /logout` - Destruye sesión y redirige a login

**Endpoints protegidos (requieren login):**
- `GET /account` - Página de cuenta del usuario (saldo, historial, descargas)
- `GET /notifications` - API JSON que retorna notificaciones no leídas
- `POST /notifications/:id/read` - Marca notificación como leída
- `POST /notifications/mark-all-read` - Marca todas como leídas
- `GET /user/balance` - API JSON que retorna balance, totalSpent, isVip

**Action Helpers:**
- `UserAction` - Wrapper que verifica sesión y obtiene usuario logueado
- `getLoggedUser(request)` - Obtiene usuario desde sesión usando "userEmail"

**Flujo de autenticación:**
1. Usuario ingresa email y password en /login
2. AuthController.authenticate valida credenciales con UserRepo
3. Si es correcto, guarda email en session("userEmail")
4. Redirecciona según rol: admin a /admin, usuario a /shop
5. En requests posteriores, UserAction lee session("userEmail") y carga el User

---

## Módulo 3: Gestión de Contenido Multimedia

### Modelo Media (app/models/Media.scala)

**Campos principales:**
- `id: Long` - Identificador único
- `title: String` - Título del producto
- `description: String` - Descripción
- `price: BigDecimal` - Precio base
- `mtype: MediaType` - Tipo: Audio, Video o Image
- `assetPath: String` - Ruta del archivo (ej: "media/audio/song.mp3")
- `categoryId: Option[Long]` - Categoría asignada (opcional)
- `stock: Int` - Unidades disponibles (agregado en Fase 1)
- `createdAt: LocalDateTime` - Fecha de creación

**Métodos de stock:**
- `hasStock(quantity)` - Verifica si hay suficiente stock
- `isLowStock` - Retorna true si stock <= 10
- `isOutOfStock` - Retorna true si stock == 0
- `stockStatus` - Retorna texto descriptivo del estado

**Métodos de promociones:**
- `activePromotion: Option[Promotion]` - Busca mejor promoción activa
- `hasActivePromotion: Boolean` - Indica si tiene promoción
- `promotionDiscount: Option[Int]` - Porcentaje de descuento
- `finalPrice(user: Option[User]): BigDecimal` - Calcula precio final con esta lógica:
  1. Si hay promoción activa: aplica descuento de promoción
  2. Si NO hay promoción pero user es VIP: aplica 20% descuento
  3. Si no aplica nada: retorna precio base
- `discountText: Option[String]` - Genera texto del descuento aplicado

**MediaRepo - Operaciones:**
- `create(title, description, price, mtype, assetPath, categoryId, stock)` - Crear producto
- `update(id, ...)` - Actualizar producto
- `delete(id)` - Eliminar producto
- `find(id)` - Buscar por ID
- `all` - Listar todos
- `searchAdvanced(query, typeFilter, categoryFilter)` - Búsqueda con filtros
- `reduceStock(id, quantity): Either[String, Media]` - SYNCHRONIZED - Reduce stock atómicamente

**Funcionalidad crítica de stock:**
El método `reduceStock` usa SYNCHRONIZED para evitar race conditions cuando múltiples usuarios compran el último producto simultáneamente. Retorna Either[String, Media]: Left con error si no hay stock, Right con media actualizado si ok.

**Productos semilla:**
- "Concierto en Vivo" - Audio, $15, 25 unidades
- "Tutorial de Scala" - Video, $30, 50 unidades
- "Wallpaper HD" - Imagen, $5, 8 unidades (low stock)

---

## Módulo 4: Sistema de Categorías Jerárquico

### Modelo Category (app/models/Category.scala)

**Campos:**
- `id: Long` - Identificador
- `name: String` - Nombre de la categoría
- `parentId: Option[Long]` - ID de categoría padre (None = raíz)

**CategoryRepo - Operaciones especiales:**
- `getRoots` - Retorna solo categorías principales (sin padre)
- `getChildren(parentId)` - Retorna subcategorías directas
- `getAllDescendants(categoryId)` - RECURSIVO - Retorna todos los descendientes (hijos, nietos, etc.)
- `getParents(categoryId)` - Retorna cadena de ancestros para breadcrumbs
- `countProducts(categoryId)` - Cuenta productos en categoría y subcategorías
- `hasProducts(categoryId)` - Verifica si tiene productos asignados
- `delete(id)` - Elimina si no tiene productos ni subcategorías

**Ejemplo de jerarquía:**
```
Música (id=1)
  ├─ Rock (id=2, parentId=1)
  └─ Jazz (id=3, parentId=1)
Educación (id=4)
  └─ Programación (id=5, parentId=4)
```

**Funcionalidad de promociones:**
Cuando una promoción se aplica a una categoría, automáticamente incluye todas sus subcategorías usando `getAllDescendants()`.

---

## Módulo 5: Sistema de Recargas de Saldo

### Modelo BalanceRequest (app/models/BalanceRequest.scala)

**Estados posibles:**
- `Pending` - Recién creada, esperando revisión
- `Approved` - Aprobada por admin, saldo agregado
- `Rejected` - Rechazada por admin

**Campos:**
- `id: Long`
- `userId: Long` - Usuario solicitante
- `amount: BigDecimal` - Monto solicitado
- `status: RequestStatus` - Estado actual
- `createdAt: LocalDateTime` - Fecha de solicitud
- `processedAt: Option[LocalDateTime]` - Fecha de procesamiento

**BalanceRequestRepo - Operaciones:**
- `create(userId, amount)` - Crear nueva solicitud en estado Pending
- `approve(id)` - Cambia a Approved, agrega saldo a usuario, crea notificación
- `reject(id)` - Cambia a Rejected, crea notificación
- `findByUser(userId)` - Historial de solicitudes del usuario
- `getPending` - Lista solicitudes pendientes (para admin)

**Flujo completo:**
1. Usuario desde /account solicita recarga de $50
2. Se crea BalanceRequest con status=Pending
3. Admin ve solicitud en /admin/balance-requests
4. Admin aprueba: sistema ejecuta `approve(id)` que:
   - Cambia status a Approved
   - Ejecuta UserRepo.addBalance(userId, 50)
   - Crea Notification tipo BalanceApproved
5. Usuario recibe notificación toast en tiempo real

---

## Módulo 6: Sistema de Descargas/Compras

### Modelo Download (app/models/Download.scala)

**Campos:**
- `id: Long`
- `userId: Long` - Comprador
- `mediaId: Long` - Producto comprado
- `quantity: Int` - Cantidad comprada
- `price: BigDecimal` - Precio unitario pagado
- `discount: BigDecimal` - Descuento total aplicado
- `purchaseDate: LocalDateTime` - Fecha de compra

**DownloadRepo - Operaciones:**
- `add(userId, mediaId, quantity, price, discount)` - Registrar compra
- `findByUser(userId)` - Historial de compras con JOIN a Media
- `hasUserPurchased(userId, mediaId)` - Verifica si usuario compró producto

**Relación con checkout:**
En ShopController.checkout, después de validar stock y deducir saldo, se crea un Download por cada producto del carrito con el precio final (incluyendo descuentos de promoción).

---

## Módulo 7: Carrito de Compras

### Modelo CartItem (app/models/Cart.scala)

**Campos:**
- `id: Long`
- `userId: Long` - Dueño del carrito
- `mediaId: Long` - Producto en carrito
- `quantity: Int` - Cantidad deseada
- `dateAdded: LocalDateTime` - Fecha de agregado

**CartRepo - Operaciones:**
- `addOrUpdate(userId, mediaId, quantity): Either[String, CartItem]` - Agrega producto o suma cantidad si ya existe
- `updateQuantity(id, newQuantity)` - Actualiza cantidad de ítem
- `remove(id)` - Elimina ítem del carrito
- `getByUser(userId): Vector[(CartItem, Media)]` - Retorna carrito con JOIN a Media
- `clearByUser(userId)` - Vacía carrito (usado después de checkout)
- `getTotal(userId, user): BigDecimal` - Calcula total con promociones/VIP
- `countItems(userId)` - Cuenta productos en carrito (para badge)

**Funcionalidad importante:**
El carrito NO reserva stock. Es temporal. El stock solo se reduce en checkout dentro de bloque SYNCHRONIZED para garantizar atomicidad.

---

## Módulo 8: Sistema de Notificaciones en Tiempo Real

### Modelo Notification (app/models/Notification.scala)

**Tipos de notificación:**
- `BalanceApproved` - Recarga aprobada
- `BalanceRejected` - Recarga rechazada
- `PurchaseSuccess` - Compra exitosa
- `Info` - Información general

**Campos:**
- `id: Long`
- `userId: Long` - Destinatario
- `message: String` - Mensaje a mostrar
- `notificationType: NotificationType`
- `read: Boolean` - Indica si fue leída
- `createdAt: LocalDateTime`

**NotificationRepo - Operaciones:**
- `create(userId, message, notifType)` - Crear nueva notificación
- `getUnread(userId)` - Obtener no leídas
- `markAsRead(id)` - Marcar como leída
- `markAllAsRead(userId)` - Marcar todas como leídas
- `cleanOld()` - Elimina notificaciones mayores a 30 días

**Sistema de polling (public/javascripts/notifications.js):**

```javascript
// Cada 10 segundos ejecuta:
function checkNotifications() {
    fetch('/notifications')
        .then(res => res.json())
        .then(data => {
            // Actualiza badge con count
            // Para cada nueva notificación: muestra toast
            // Reproduce sonido
            // Auto-marca como leída después de 2s
        });
}
```

**Características del toast:**
- Aparece en esquina superior derecha
- Sonido de notificación (WAV base64 embebido)
- Auto-hide después de 8 segundos
- Bootstrap Toast API
- Icono según tipo de notificación

---

## Módulo 9: Actualización de Saldo en Tiempo Real

### Endpoint /user/balance (AuthController)

Retorna JSON:
```json
{
  "balance": 250.50,
  "totalSpent": 120.00,
  "isVip": true
}
```

### JavaScript (notifications.js - función updateBalance)

```javascript
// Cada 10 segundos:
function updateBalance() {
    fetch('/user/balance')
        .then(res => res.json())
        .then(data => {
            // Busca elementos con data-balance
            // Si balance cambió: actualiza texto + efecto flash verde
            // Actualiza totalSpent
            // Muestra/oculta badge VIP según isVip
        });
}
```

**Efecto visual:**
Cuando el saldo cambia (ej: admin aprueba recarga), el elemento con `data-balance` parpadea en verde durante 1 segundo usando CSS animation.

**Elementos afectados:**
- user_account.scala.html: span del saldo
- user_downloads.scala.html: h3 del saldo
- Cualquier elemento con atributo `data-balance="true"`

---

## Módulo 10: Sistema de Promociones

### Modelo Promotion (app/models/Promotion.scala)

**Campos principales:**
- `id: Long`
- `name: String` - Nombre (ej: "Black Friday Música")
- `description: String`
- `discountPercent: Int` - Porcentaje de descuento (1-99)
- `startDate: LocalDateTime` - Inicio de vigencia
- `endDate: LocalDateTime` - Fin de vigencia
- `targetType: PromotionTarget` - A qué se aplica
- `targetIds: Vector[Long]` - IDs de productos/categorías/tipos afectados
- `isActive: Boolean` - Flag para pausar/reanudar

**PromotionTarget (enum):**
- `Product` - Aplica a productos específicos (targetIds = IDs de Media)
- `Category` - Aplica a categorías (targetIds = IDs de Category, incluye subcategorías)
- `MediaType` - Aplica a tipos (targetIds: 1=Audio, 2=Video, 3=Imagen)
- `All` - Aplica a todos los productos (targetIds vacío)

**Métodos importantes:**
- `isCurrentlyActive: Boolean` - Verifica que NOW esté entre startDate-endDate Y isActive=true
- `applyDiscount(price): BigDecimal` - Calcula precio con descuento
- `hoursRemaining: Long` - Horas hasta que termine
- `daysRemaining: Long` - Días hasta que termine
- `status: String` - "Activa", "Próxima", "Finalizada", "Pausada"

**PromotionRepo - Operaciones clave:**
- `create(name, description, discountPercent, startDate, endDate, targetType, targetIds)` - Crear promoción
- `getActive` - Retorna solo promociones actualmente activas
- `getBestPromotionFor(media): Option[Promotion]` - LÓGICA DE PRIORIDAD:
  1. Busca promoción tipo Product con media.id
  2. Si no, busca tipo Category con categoryId (incluye ancestros con getParents)
  3. Si no, busca tipo MediaType con media.mtype
  4. Si no, busca tipo All
  5. Retorna la primera encontrada (mayor prioridad)
- `update(id, ...)` - Actualizar promoción
- `toggleActive(id)` - Pausar/reanudar sin eliminar
- `countActive`, `countUpcoming`, `countExpired` - Estadísticas para dashboard

**Promociones semilla:**
1. "Black Friday Música" - 30% OFF en categoría Música, activa ahora mismo
2. "Cyber Monday Videos" - 50% OFF en tipo Video, inicia en 5 días

**Interacción con Media.finalPrice:**
```scala
def finalPrice(user: Option[User]): BigDecimal = {
  activePromotion match {
    case Some(promo) => promo.applyDiscount(price)  // PRIORIDAD 1
    case None => 
      user match {
        case Some(u) if u.isVip => price * 0.80     // PRIORIDAD 2 (VIP)
        case _ => price                              // Sin descuento
      }
  }
}
```

**Regla de oro:** Las promociones SIEMPRE tienen prioridad sobre el descuento VIP.

---

## Módulo 11: Panel de Administración

### AdminController (app/controllers/AdminController.scala)

**Seguridad:**
Todos los endpoints usan `AdminAction` que verifica `user.isAdmin`. Si no es admin, redirige a /login.

**Gestión de Productos:**
- `GET /admin/media` - Lista todos los productos
- `GET /admin/media/new` - Formulario nuevo producto
- `POST /admin/media/create` - Crear producto (incluye stock)
- `GET /admin/media/:id/edit` - Formulario editar
- `POST /admin/media/:id/edit` - Actualizar producto
- `POST /admin/media/:id/delete` - Eliminar producto

**Gestión de Categorías:**
- `GET /admin/categories` - Lista categorías con árbol jerárquico
- `GET /admin/categories/new` - Formulario nueva categoría
- `POST /admin/categories/create` - Crear categoría
- `POST /admin/categories/:id/delete` - Eliminar (solo si no tiene productos ni hijos)

**Gestión de Usuarios:**
- `GET /admin/users` - Lista todos los usuarios
- `POST /admin/users/:id/delete` - Eliminar usuario

**Gestión de Recargas:**
- `GET /admin/balance-requests` - Lista solicitudes pendientes
- `POST /admin/balance-requests/:id/approve` - Aprobar recarga
- `POST /admin/balance-requests/:id/reject` - Rechazar recarga

**Gestión de Promociones:**
- `GET /admin/promotions` - Lista promociones con filtros (activas/próximas/todas)
- `GET /admin/promotions/new` - Formulario nueva promoción con date pickers
- `POST /admin/promotions/create` - Crear promoción (parsea LocalDateTime)
- `GET /admin/promotions/:id/edit` - Formulario editar
- `POST /admin/promotions/:id/edit` - Actualizar promoción
- `POST /admin/promotions/:id/delete` - Eliminar promoción
- `POST /admin/promotions/:id/toggle` - Pausar/reanudar promoción

**Dashboard:**
- `GET /admin` - Página principal con tarjetas:
  - Gestión de Productos (con contador)
  - Gestión de Categorías (con árbol)
  - Gestión de Promociones (con contadores activas/próximas)
  - Gestión de Usuarios (con contador)
  - Solicitudes de Recarga (con contador pendientes)
  - Estadísticas Avanzadas

---

## Módulo 12: Tienda Pública (ShopController)

### Endpoints públicos:

**GET /shop - Lista de productos**
- Parámetros opcionales: `q` (búsqueda), `type` (audio/video/image), `category` (ID)
- Llama a `MediaRepo.searchAdvanced` con filtros
- Retorna `media_list.scala.html` con productos, categorías, usuario opcional
- Muestra badges de stock: "AGOTADO", "ÚLTIMA UNIDAD", "ÚLTIMAS X UNIDADES", "DISPONIBLE"
- Muestra badges de promoción: "🔥 -30% OFF" con countdown si quedan <=48h
- Precio tachado + precio final si hay promoción

**GET /shop/:id - Detalle de producto**
- Muestra información completa del producto
- Audio/Video reproducible en navegador
- Imágenes visualizables

### Endpoints protegidos (requieren login):

**POST /cart/add - Agregar al carrito**
- Recibe: mediaId, quantity
- Valida que producto exista
- Llama a `CartRepo.addOrUpdate` (suma cantidad si ya existe)
- Retorna Either[String, CartItem]
- Redirecciona a /cart con mensaje de éxito/error

**GET /cart - Ver carrito**
- Obtiene `CartRepo.getByUser(userId)` con JOIN a Media
- Muestra tabla con: producto, precio unitario, cantidad (editable), stock, subtotal
- Sidebar con resumen:
  - Subtotal original
  - Descuento por promociones (si aplica)
  - Total a pagar
  - Saldo disponible
  - Botón "Finalizar Compra" o "Solicitar Recarga" según saldo

**POST /cart/update/:id - Actualizar cantidad**
- Recibe: quantity
- Valida que quantity <= stock
- Actualiza CartItem
- Auto-submit con onChange en input

**POST /cart/remove/:id - Eliminar del carrito**
- Elimina CartItem
- Redirecciona a /cart

**POST /cart/checkout - Finalizar compra (CRÍTICO)**

Flujo completo:
```scala
def checkout = UserAction { user => implicit req =>
  val cartItems = CartRepo.getByUser(user.id)
  
  // 1. Validar carrito no vacío
  if (cartItems.isEmpty) return error
  
  // 2. Calcular total usando finalPrice (incluye promociones)
  val total = cartItems.map { case (item, media) => 
    media.finalPrice(Some(user)) * item.quantity 
  }.sum
  
  // 3. Validar balance suficiente
  if (user.balance < total) return error
  
  // 4. SYNCHRONIZED BLOCK (evita race conditions)
  this.synchronized {
    // 4a. Validar stock disponible para TODOS los items
    val stockErrors = cartItems.flatMap { case (item, media) =>
      if (!media.hasStock(item.quantity)) 
        Some(s"${media.title}: solo quedan ${media.stock} unidades")
      else None
    }
    if (stockErrors.nonEmpty) return Left(error)
    
    // 4b. Reducir stock de todos los productos
    cartItems.foreach { case (item, media) =>
      MediaRepo.reduceStock(media.id, item.quantity)
    }
    
    // 4c. Deducir balance
    UserRepo.deductBalance(user.id, total)
    
    // 4d. Registrar compras en Download
    cartItems.foreach { case (item, media) =>
      val finalPrice = media.finalPrice(Some(user))
      val originalPrice = media.price
      val totalDiscount = (originalPrice - finalPrice) * item.quantity
      
      DownloadRepo.add(user.id, media.id, item.quantity, finalPrice, totalDiscount)
    }
    
    // 4e. Actualizar totalSpent
    UserRepo.updateTotalSpent(user.id, total)
    
    // 4f. Limpiar carrito
    CartRepo.clearByUser(user.id)
  }
  
  // 5. Redireccionar a cuenta con mensaje de éxito
  Redirect(routes.AuthController.account).flashing("success" -> s"Compra exitosa: $total")
}
```

**Por qué es SYNCHRONIZED:**
Imagina dos usuarios (A y B) comprando el último producto al mismo tiempo:
1. A y B leen stock=1 simultáneamente
2. Sin SYNCHRONIZED: ambos pasan validación
3. Ambos ejecutan reduceStock y balance se cobra doble
4. CON SYNCHRONIZED: solo uno entra al bloque, el otro espera
5. El primero reduce stock a 0 y compra
6. El segundo entra, valida stock=0, recibe error "Stock insuficiente"

---

## Vistas Principales

### Navbar (app/views/navbar.scala.html)

**Para usuarios anónimos:**
- Logo LP Studios
- Tienda
- Login
- Registro

**Para usuarios logueados:**
- Logo LP Studios
- Tienda
- Mi Cuenta
- Carrito (con badge contador de items)
- Notificaciones (con badge contador de no leídas)
- Logout

**Para administradores:**
- Agrega: Panel Admin

**Implementación de badges:**
```html
<a href="/cart" class="btn btn-outline-light position-relative">
  <i class="bi bi-cart"></i>
  @if(cartItemCount > 0) {
    <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger">
      @cartItemCount
    </span>
  }
</a>
```

### Tienda (app/views/media_list.scala.html)

**Sidebar izquierdo (filtros):**
- Buscador por texto
- Filtro por tipo: Todos, Imágenes, Audio, Video
- Filtro por categoría: árbol jerárquico con contadores

**Grid de productos:**
- Cards responsivas: col-12 col-sm-6 col-lg-4
- Media preview: imagen/audio/video según tipo
- Título y descripción
- Categoría con link
- Badge de stock con colores:
  - Verde "DISPONIBLE (X unidades)" si stock > 10
  - Naranja "ÚLTIMAS X UNIDADES" si stock <= 10
  - Naranja "ÚLTIMA UNIDAD" si stock = 1
  - Rojo "AGOTADO" si stock = 0
- Badge de promoción "🔥 -30% OFF" si hasActivePromotion
- Countdown "Termina en 2d 5h" si promoción con <= 48h restantes
- Precio: original tachado + final en rojo si hay promoción
- Botones: "Ver detalles" (ojo) + "Agregar al Carrito" (deshabilitado si agotado)

### Carrito (app/views/cart.scala.html)

**Tabla de productos:**
- Columna producto: título, descripción, badge promoción, badge stock
- Columna precio unitario: original tachado + final si hay promoción
- Columna cantidad: input editable con auto-submit onChange
- Columna stock: badge con unidades disponibles
- Columna subtotal: precio final * cantidad
- Columna acciones: botón eliminar con confirmación

**Sidebar resumen:**
- Subtotal original (sin descuentos)
- Descuento por promociones (suma de todos los descuentos)
- Total a pagar (con descuentos aplicados)
- Saldo actual del usuario
- Validación: si saldo < total, muestra alerta y botón "Solicitar Recarga"
- Si saldo suficiente: botón "Finalizar Compra" con confirmación

### Cuenta de usuario (app/views/user_account.scala.html)

**Sección 1: Información**
- Nombre del usuario
- Email
- Fecha de registro
- Saldo actual (con data-balance para actualización en tiempo real)
- Total gastado (con data-total-spent)
- Badge VIP si totalSpent >= 100 (con data-vip-badge)

**Sección 2: Solicitar recarga**
- Formulario con input de monto
- Botón "Solicitar Recarga"
- Historial de solicitudes con estados (Pendiente/Aprobada/Rechazada)

**Sección 3: Historial de compras**
- Tabla con: fecha, producto, cantidad, precio unitario, descuento, total
- Link para descargar archivo
- Total general gastado

### Panel Admin (app/views/admin_dashboard.scala.html)

**Grid de tarjetas (cards):**

1. Gestión de Productos
   - Contador total de productos
   - Botones: "Ver productos", "Nuevo producto"

2. Gestión de Categorías
   - Árbol jerárquico de categorías
   - Botón: "Ver categorías"

3. Gestión de Promociones (tarjeta roja)
   - Contador de activas
   - Contador de próximas
   - Botón: "Ver promociones"

4. Gestión de Usuarios
   - Contador total de usuarios
   - Botón: "Ver usuarios"

5. Solicitudes de Recarga
   - Contador de pendientes
   - Botón: "Ver solicitudes"

6. Estadísticas Avanzadas
   - Botón: "Ver estadísticas completas"

### Lista de promociones (app/views/admin_promotions.scala.html)

**Tarjetas estadísticas:**
- Activas (verde)
- Próximas (amarillo)
- Finalizadas (gris)

**Pestañas:**
- Activas: solo promociones con isCurrentlyActive = true
- Próximas: isActive=true y NOW < startDate
- Todas: sin filtro

**Tabla de promociones:**
- Estado: badge según status (Activa/Próxima/Finalizada/Pausada)
- Nombre y descripción
- Descuento: badge "-30%"
- Tipo: badge según targetType (Todos/Productos/Categorías/Tipos)
- Fechas: inicio y fin formateadas
- Tiempo restante: cálculo dinámico
  - Si activa y >48h: "X días"
  - Si activa y <=48h: "Xd Xh" en amarillo
  - Si activa y <=24h: "Xh restantes" en rojo negrita
  - Si próxima: "Inicia en X días"
- Acciones:
  - Botón editar (lápiz)
  - Botón pausar/reanudar (pause/play)
  - Botón eliminar (basura) con confirmación

### Formulario de promoción (app/views/admin_promotion_form.scala.html)

**Sección 1: Información básica**
- Nombre (required)
- Descripción
- Descuento % (1-99, required) con preview en tiempo real

**Sección 2: Vigencia**
- Fecha inicio (datetime-local, required)
- Fecha fin (datetime-local, required)
- Validación JavaScript: fin > inicio

**Sección 3: Alcance**
- Selector targetType:
  - Todos los productos
  - Productos específicos (multiselect de productos)
  - Categorías específicas (multiselect de categorías)
  - Tipos de media (checkboxes: Audio/Video/Imagen)
- Lógica JavaScript que muestra/oculta selectores según targetType
- Validación: debe seleccionar al menos 1 ítem si no es "Todos"

**Sección 4: Estado (solo en edición)**
- Checkbox "Promoción activa" (permite pausar sin eliminar)

**Preview:**
- Badge "🔥 -X% OFF" que se actualiza en tiempo real con el input de descuento

**JavaScript importante:**
```javascript
// Actualiza campo hidden targetIds antes de submit
document.getElementById('promotionForm').addEventListener('submit', function(e) {
  const targetType = document.getElementById('targetType').value;
  let ids = [];
  
  if (targetType === 'Product') {
    ids = Array.from(document.getElementById('productIds').selectedOptions).map(o => o.value);
  } else if (targetType === 'Category') {
    ids = Array.from(document.getElementById('categoryIds').selectedOptions).map(o => o.value);
  } else if (targetType === 'MediaType') {
    ids = Array.from(document.querySelectorAll('input[name="mediatypeIds"]:checked')).map(c => c.value);
  }
  
  document.getElementById('targetIds').value = ids.join(',');
});
```

---

## Flujos de Usuario Completos

### Flujo 1: Compra con Promoción

1. Usuario ingresa a /shop
2. Ve producto "Concierto en Vivo" con badge "🔥 -30% OFF"
3. Precio original $15 tachado, precio final $10.50
4. Countdown "Termina en 1d 5h"
5. Click "Agregar al Carrito"
6. Sistema ejecuta CartRepo.addOrUpdate(userId, mediaId, 1)
7. Badge del carrito cambia a "1"
8. Usuario va a /cart
9. Ve producto en tabla con precio $10.50 (promoción aplicada)
10. Resumen muestra:
    - Subtotal original: $15.00
    - Descuento por promociones: -$4.50
    - Total a pagar: $10.50
11. Usuario tiene $200 de saldo (suficiente)
12. Click "Finalizar Compra"
13. Confirmación JavaScript
14. POST a /cart/checkout:
    - SYNCHRONIZED block
    - Valida stock=25 (ok)
    - Reduce stock a 24
    - Deducir $10.50 de balance (ahora $189.50)
    - Crea Download con price=$10.50, discount=$4.50
    - Actualiza totalSpent += $10.50
    - Limpia carrito
15. Redirecciona a /account con flash "Compra exitosa"
16. JavaScript de notifications.js detecta cambio de balance en próximo poll
17. Saldo parpadea en verde y actualiza a $189.50

### Flujo 2: Recarga de Saldo

1. Usuario con $5 de saldo quiere comprar producto de $30
2. Intenta checkout: recibe error "Saldo insuficiente. Necesitas $30 pero tienes $5"
3. Click botón "Solicitar Recarga"
4. Redirecciona a /account
5. Ingresa monto $50 en formulario
6. POST a /balance-request/create
7. Sistema crea BalanceRequest(userId, 50, Pending)
8. Admin recibe notificación (implementar en futuro)
9. Admin va a /admin/balance-requests
10. Ve solicitud con estado "Pendiente"
11. Click "Aprobar"
12. POST a /admin/balance-requests/:id/approve:
    - Cambia status a Approved
    - Ejecuta UserRepo.addBalance(userId, 50)
    - Crea Notification(userId, "Tu solicitud de recarga de $50 ha sido APROBADA", BalanceApproved)
13. En máximo 10 segundos, JavaScript de usuario ejecuta checkNotifications()
14. Detecta nueva notificación
15. Muestra toast en esquina superior derecha
16. Reproduce sonido
17. Badge de notificaciones cambia a "1"
18. Actualiza badge de saldo con efecto flash verde: $55 ($5 + $50)
19. Marca notificación como leída después de 2s

### Flujo 3: Admin Crea Promoción

1. Admin ingresa a /admin
2. Click en tarjeta "Gestión de Promociones"
3. Redirecciona a /admin/promotions
4. Click "Nueva Promoción"
5. Formulario en /admin/promotions/new:
   - Nombre: "Fin de Semana Videos"
   - Descripción: "Descuento especial en todos los videos"
   - Descuento: 25% (preview muestra "🔥 -25% OFF")
   - Fecha inicio: 2025-11-08 00:00
   - Fecha fin: 2025-11-10 23:59
   - Aplicar a: "Tipos de media"
   - Selecciona checkbox "Video"
6. JavaScript valida que fin > inicio
7. Submit ejecuta JavaScript que:
   - Lee checked checkboxes [2] (Video = id 2)
   - Asigna targetIds = "2"
8. POST a /admin/promotions/create
9. AdminController parsea:
   - startDate = LocalDateTime.parse("2025-11-08T00:00")
   - endDate = LocalDateTime.parse("2025-11-10T23:59")
   - targetType = PromotionTarget.from("MediaType") = MediaType
   - targetIds = "2".split(",") = [2L]
10. PromotionRepo.create(...)
11. Redirecciona a /admin/promotions con flash "Promoción creada"
12. Usuario público entra a /shop el 2025-11-08
13. Ve producto "Tutorial de Scala" (Video) con badge "🔥 -25% OFF"
14. Precio $30 → $22.50
15. Usuario VIP (20% descuento) NO recibe descuento VIP porque promoción tiene prioridad
16. Compra a $22.50 en lugar de $24 (VIP)

---

## Conceptos Avanzados Implementados

### 1. Control de Concurrencia con SYNCHRONIZED

**Problema:** Dos usuarios compran último producto simultáneamente
**Solución:** SYNCHRONIZED block en checkout y reduceStock

```scala
// MediaRepo.reduceStock
def reduceStock(id: Long, quantity: Int): Either[String, Media] = this.synchronized {
  find(id) match {
    case Some(media) if media.hasStock(quantity) =>
      val updated = media.copy(stock = media.stock - quantity)
      medias = medias.filterNot(_.id == id) :+ updated
      Right(updated)
    case Some(_) =>
      Left("Stock insuficiente")
    case None =>
      Left("Producto no encontrado")
  }
}
```

**Por qué funciona:**
- `this.synchronized` bloquea el objeto MediaRepo
- Solo 1 thread puede ejecutar reduceStock a la vez
- Los demás esperan en cola
- Garantiza que stock nunca sea negativo

### 2. Polling para Tiempo Real sin WebSockets

**Estrategia:** Polling cada 10 segundos con JavaScript

**Ventajas:**
- Simple de implementar
- No requiere WebSocket server
- Funciona con Play Framework sin configuración adicional

**Implementación:**
```javascript
setInterval(() => {
  checkNotifications();  // GET /notifications
  updateBalance();       // GET /user/balance
}, 10000);
```

**Optimización:**
- Solo hace fetch si usuario está logueado
- Solo actualiza DOM si hay cambios
- Usa data attributes para identificar elementos

### 3. Sistema de Prioridades en Descuentos

**Regla:** Promoción > VIP

**Implementación en Media.finalPrice:**
```scala
def finalPrice(user: Option[User]): BigDecimal = {
  PromotionRepo.getBestPromotionFor(this) match {
    case Some(promo) => 
      // PRIORIDAD 1: Promoción
      price * (BigDecimal(100 - promo.discountPercent) / 100)
    case None =>
      user match {
        case Some(u) if u.totalSpent >= 100 =>
          // PRIORIDAD 2: VIP (20%)
          price * 0.80
        case _ =>
          // Sin descuento
          price
      }
  }
}
```

**Por qué esta lógica:**
- Evita doble descuento (promoción + VIP = 50% total)
- Promociones temporales son más importantes que estatus permanente
- Simplifica cálculos en checkout

### 4. Promociones Jerárquicas en Categorías

**Problema:** Promoción en "Música" debe incluir "Rock" y "Jazz"

**Solución:** CategoryRepo.getAllDescendants recursivo

```scala
def getAllDescendants(categoryId: Long): Vector[Category] = {
  val children = getChildren(categoryId)
  children ++ children.flatMap(c => getAllDescendants(c.id))
}
```

**Uso en PromotionRepo.getBestPromotionFor:**
```scala
case PromotionTarget.Category =>
  val categoryIds = media.categoryId.toVector ++ 
    media.categoryId.toVector.flatMap(CategoryRepo.getParents).map(_.id)
  
  getActive.find { promo =>
    promo.targetType == PromotionTarget.Category &&
    categoryIds.exists(promo.targetIds.contains)
  }
```

**Flujo:**
1. Producto "Canción Rock" tiene categoryId = 2 (Rock)
2. Rock tiene parentId = 1 (Música)
3. getParents(2) retorna [Category(1, "Música")]
4. categoryIds = [2, 1]
5. Promoción con targetIds=[1] (Música) coincide
6. Descuento se aplica

### 5. Validación Atómica en Checkout

**Desafío:** Validar stock de 5 productos Y reducir stock ATÓMICAMENTE

**Solución:** Validación primero, acción después, todo en SYNCHRONIZED

```scala
this.synchronized {
  // FASE 1: Validación completa (no modifica datos)
  val stockErrors = cartItems.flatMap { case (item, media) =>
    if (!media.hasStock(item.quantity)) Some(error) else None
  }
  
  if (stockErrors.nonEmpty) {
    return Left(stockErrors)  // Sale sin modificar nada
  }
  
  // FASE 2: Modificación (solo si pasó validación)
  cartItems.foreach { case (item, media) =>
    MediaRepo.reduceStock(media.id, item.quantity)
  }
  UserRepo.deductBalance(user.id, total)
  // ... resto de operaciones
  
  Right(())
}
```

**Por qué es importante:**
- Si 1 de 5 productos no tiene stock, NO se compra ninguno
- Evita estado inconsistente (algunos comprados, otros no)
- Garantiza transacción "todo o nada"

### 6. Action Composition para Autorización

**Problema:** Repetir validación de admin en cada método

**Solución:** AdminAction wrapper

```scala
def AdminAction(f: User => Request[AnyContent] => Result): Action[AnyContent] = {
  Action { request =>
    getLoggedUser(request) match {
      case Some(user) if user.isAdmin =>
        f(user)(request)
      case Some(_) =>
        Redirect(routes.AuthController.login).flashing("error" -> "Acceso denegado")
      case None =>
        Redirect(routes.AuthController.login).flashing("error" -> "Debes iniciar sesión")
    }
  }
}
```

**Uso:**
```scala
def listPromotions = AdminAction { user => implicit request =>
  // user ya está validado como admin
  val promotions = PromotionRepo.all
  Ok(views.html.admin_promotions(promotions))
}
```

**Beneficios:**
- DRY (Don't Repeat Yourself)
- Seguridad centralizada
- Fácil de modificar (ej: agregar logging)

---

## Puntos Críticos de Seguridad

### 1. CSRF Protection
Todos los formularios POST incluyen:
```html
@helper.CSRF.formField
```

### 2. Session-based Authentication
- Sesión almacena solo "userEmail" (no password)
- Session se destruye en logout
- Timeout automático después de inactividad

### 3. Validación de Balance
- Checkout valida balance ANTES y DENTRO del synchronized
- No permite saldo negativo

### 4. Validación de Stock
- Checkout valida stock ANTES de reducir
- reduceStock retorna Either para manejar errores

### 5. Autorización por Rol
- AdminAction verifica isAdmin
- UserAction verifica login
- Rutas públicas no requieren autenticación

---

## Limitaciones Actuales

### 1. Persistencia In-Memory
- Datos se pierden al reiniciar servidor
- No escalable para producción
- Solución futura: Migrar a H2 o PostgreSQL

### 2. Concurrencia Básica
- SYNCHRONIZED funciona para 1 instancia
- En cluster se necesita lock distribuido (Redis, Hazelcast)

### 3. Sin Paginación
- Listar todos los productos puede ser lento con miles de items
- Solución futura: Agregar paginación con offset/limit

### 4. Sin Caché
- Cada request calcula promociones activas desde cero
- Solución futura: Caché con invalidación automática

### 5. Polling cada 10s
- No es tiempo real verdadero
- Solución futura: WebSockets con Akka Streams

---

## Próximas Fases Planificadas

### Fase 4: Sistema de Regalos
- Comprar producto para otro usuario
- Generar código de regalo canjeable
- Notificación al destinatario

### Fase 5: Sistema de Calificaciones
- Puntuar productos 1-10 (solo si se compró)
- Promedio visible en tienda
- Comentarios opcionales

### Fase 6: Reportes y Rankings
- Top 10 compradores del mes
- Top 10 productos más vendidos
- Gráficos de ingresos
- Alertas de inventario bajo
- Exportar a CSV/PDF

### Fase 7: Cierre de Cuenta
- Soft delete con isActive=false
- Reactivación por admin
- Historial preservado

### Fase 8: Migración a Base de Datos
- H2 embebido o PostgreSQL
- Evolutions para schema
- Slick o Anorm para queries
- Índices en columnas frecuentes

---

## Comandos Útiles

### Desarrollo
```bash
# Iniciar servidor en modo dev
sbt run

# Compilar sin ejecutar
sbt compile

# Limpiar compilación
sbt clean

# Ejecutar tests
sbt test
```

### Estructura del Proyecto
```
lp-ecommerce/
├── app/
│   ├── controllers/     # Lógica de negocio
│   ├── models/          # Modelos y repositorios
│   ├── views/           # Templates Scala HTML
│   └── services/        # Servicios auxiliares
├── conf/
│   ├── application.conf # Configuración
│   ├── routes          # Rutas URL
│   └── messages        # i18n
├── public/
│   ├── javascripts/    # JS del cliente
│   ├── stylesheets/    # CSS
│   └── media/          # Archivos multimedia
└── test/               # Tests unitarios
```

---

## Conclusión

Este sistema implementa un e-commerce completo con características avanzadas:

- **Autenticación segura** con session-based auth
- **Gestión de productos** multimedia con categorías jerárquicas
- **Sistema de saldo virtual** con recargas aprobadas por admin
- **Carrito de compras** con validación atómica de stock
- **Notificaciones en tiempo real** con polling y toasts
- **Sistema de promociones** con prioridades y fechas de vigencia
- **Panel administrativo** completo para gestión
- **Control de concurrencia** para evitar race conditions
- **Responsive design** con Bootstrap 5

Todo construido con Scala, Play Framework y JavaScript vanilla, sin dependencias complejas ni frameworks pesados en el frontend.
