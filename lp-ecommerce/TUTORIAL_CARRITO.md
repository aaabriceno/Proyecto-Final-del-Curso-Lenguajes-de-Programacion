# 🚀 Tutorial Práctico: Agregar Carrito de Compras

## 📝 Objetivo
Agregar funcionalidad de carrito de compras a tu e-commerce.

---

## Paso 1: Crear el Modelo (Model)

### Archivo: `app/models/Cart.scala`

```scala
package models

case class CartItem(
  media: Media,      // El producto
  quantity: Int      // Cantidad
) {
  def subtotal: BigDecimal = media.price * quantity
}

case class Cart(items: List[CartItem] = List.empty) {
  
  // Agregar producto al carrito
  def add(media: Media, quantity: Int = 1): Cart = {
    items.find(_.media.id == media.id) match {
      case Some(existing) =>
        // Ya existe, aumentar cantidad
        val updated = items.map { item =>
          if (item.media.id == media.id)
            item.copy(quantity = item.quantity + quantity)
          else
            item
        }
        Cart(updated)
      
      case None =>
        // No existe, agregar nuevo
        Cart(items :+ CartItem(media, quantity))
    }
  }
  
  // Remover producto
  def remove(mediaId: Long): Cart =
    Cart(items.filterNot(_.media.id == mediaId))
  
  // Actualizar cantidad
  def updateQuantity(mediaId: Long, newQuantity: Int): Cart = {
    if (newQuantity <= 0) remove(mediaId)
    else Cart(items.map { item =>
      if (item.media.id == mediaId)
        item.copy(quantity = newQuantity)
      else
        item
    })
  }
  
  // Total del carrito
  def total: BigDecimal =
    items.map(_.subtotal).sum
  
  // Cantidad de items
  def itemCount: Int =
    items.map(_.quantity).sum
}

// Repositorio temporal en sesión
object CartRepo {
  private val carts = scala.collection.mutable.Map[String, Cart]()
  
  def get(sessionId: String): Cart =
    carts.getOrElse(sessionId, Cart())
  
  def save(sessionId: String, cart: Cart): Unit =
    carts(sessionId) = cart
  
  def clear(sessionId: String): Unit =
    carts.remove(sessionId)
}
```

**¿Qué hace?**
- `CartItem`: Representa un producto en el carrito con su cantidad
- `Cart`: Maneja la lógica del carrito (agregar, remover, calcular total)
- `CartRepo`: Guarda carritos en memoria (uno por sesión)

---

## Paso 2: Crear el Controlador (Controller)

### Archivo: `app/controllers/CartController.scala`

```scala
package controllers

import javax.inject._
import play.api.mvc._
import models._

@Singleton
class CartController @Inject()(cc: MessagesControllerComponents)
  extends MessagesAbstractController(cc) {

  private val SessionKey = "userEmail"
  
  // Helper para obtener ID de sesión
  private def getSessionId(implicit req: RequestHeader): String =
    req.session.get(SessionKey).getOrElse(req.session.hashCode.toString)

  // GET /cart - Ver carrito
  def view = Action { implicit req =>
    val cart = CartRepo.get(getSessionId)
    Ok(views.html.cart(cart))
  }

  // POST /cart/add/:id - Agregar producto
  def add(mediaId: Long) = Action { implicit req =>
    MediaRepo.find(mediaId) match {
      case Some(media) =>
        val sessionId = getSessionId
        val cart = CartRepo.get(sessionId)
        
        // Obtener cantidad del formulario (default 1)
        val quantity = req.body.asFormUrlEncoded
          .flatMap(_.get("quantity"))
          .flatMap(_.headOption)
          .flatMap(q => scala.util.Try(q.toInt).toOption)
          .getOrElse(1)
        
        val updatedCart = cart.add(media, quantity)
        CartRepo.save(sessionId, updatedCart)
        
        Redirect(routes.CartController.view)
          .flashing("success" -> s"${media.title} agregado al carrito")
      
      case None =>
        Redirect(routes.ShopController.list)
          .flashing("error" -> "Producto no encontrado")
    }
  }

  // POST /cart/remove/:id - Remover producto
  def remove(mediaId: Long) = Action { implicit req =>
    val sessionId = getSessionId
    val cart = CartRepo.get(sessionId)
    val updatedCart = cart.remove(mediaId)
    CartRepo.save(sessionId, updatedCart)
    
    Redirect(routes.CartController.view)
      .flashing("success" -> "Producto eliminado del carrito")
  }

  // POST /cart/update/:id - Actualizar cantidad
  def updateQuantity(mediaId: Long) = Action { implicit req =>
    val quantity = req.body.asFormUrlEncoded
      .flatMap(_.get("quantity"))
      .flatMap(_.headOption)
      .flatMap(q => scala.util.Try(q.toInt).toOption)
      .getOrElse(1)
    
    val sessionId = getSessionId
    val cart = CartRepo.get(sessionId)
    val updatedCart = cart.updateQuantity(mediaId, quantity)
    CartRepo.save(sessionId, updatedCart)
    
    Redirect(routes.CartController.view)
  }

  // POST /cart/clear - Vaciar carrito
  def clear = Action { implicit req =>
    CartRepo.clear(getSessionId)
    Redirect(routes.CartController.view)
      .flashing("success" -> "Carrito vaciado")
  }

  // GET /cart/count - Obtener cantidad de items (AJAX)
  def count = Action { implicit req =>
    val cart = CartRepo.get(getSessionId)
    Ok(cart.itemCount.toString)
  }
}
```

**¿Qué hace cada función?**
- `view`: Muestra el carrito
- `add`: Agrega un producto
- `remove`: Elimina un producto
- `updateQuantity`: Cambia la cantidad
- `clear`: Vacía el carrito
- `count`: Devuelve cantidad (para badge en navbar)

---

## Paso 3: Agregar Rutas (Routes)

### Archivo: `conf/routes` (agregar al final)

```
# Carrito de compras
GET     /cart              controllers.CartController.view
POST    /cart/add/:id      controllers.CartController.add(id: Long)
POST    /cart/remove/:id   controllers.CartController.remove(id: Long)
POST    /cart/update/:id   controllers.CartController.updateQuantity(id: Long)
POST    /cart/clear        controllers.CartController.clear
GET     /cart/count        controllers.CartController.count
```

---

## Paso 4: Crear la Vista (View)

### Archivo: `app/views/cart.scala.html`

```scala
@(cart: models.Cart)(implicit req: RequestHeader, flash: Flash)

@main("Carrito de Compras") {
  <div class="container">
    <h1>🛒 Mi Carrito</h1>
    
    @* Mensajes flash *@
    @flash.get("success").map { msg =>
      <div class="alert alert-success">@msg</div>
    }
    
    @if(cart.items.isEmpty) {
      <div class="empty-cart">
        <p>Tu carrito está vacío</p>
        <a href="@routes.ShopController.list" class="btn">Ir a la tienda</a>
      </div>
    } else {
      <table class="cart-table">
        <thead>
          <tr>
            <th>Producto</th>
            <th>Precio</th>
            <th>Cantidad</th>
            <th>Subtotal</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          @for(item <- cart.items) {
            <tr>
              <td>
                <div class="product-info">
                  <h3>@item.media.title</h3>
                  <p>@item.media.description</p>
                </div>
              </td>
              <td>$@item.media.price</td>
              <td>
                <form method="POST" action="@routes.CartController.updateQuantity(item.media.id)">
                  <input type="number" name="quantity" value="@item.quantity" min="1" max="99">
                  <button type="submit" class="btn-small">Actualizar</button>
                </form>
              </td>
              <td>$@item.subtotal</td>
              <td>
                <form method="POST" action="@routes.CartController.remove(item.media.id)">
                  <button type="submit" class="btn-danger">❌ Eliminar</button>
                </form>
              </td>
            </tr>
          }
        </tbody>
        <tfoot>
          <tr class="total-row">
            <td colspan="3"><strong>Total:</strong></td>
            <td colspan="2"><strong>$@cart.total</strong></td>
          </tr>
        </tfoot>
      </table>
      
      <div class="cart-actions">
        <form method="POST" action="@routes.CartController.clear" style="display:inline;">
          <button type="submit" class="btn-secondary">Vaciar carrito</button>
        </form>
        <a href="@routes.ShopController.list" class="btn-secondary">Seguir comprando</a>
        <button class="btn-primary">Proceder al pago</button>
      </div>
    }
  </div>
}
```

---

## Paso 5: Modificar Vista de Producto

### Archivo: `app/views/media_detail.scala.html` (agregar botón)

```scala
@(media: models.Media)(implicit req: RequestHeader)

@main(media.title) {
  <div class="product-detail">
    <h1>@media.title</h1>
    <p>@media.description</p>
    <p class="price">Precio: $@media.price</p>
    <p>⭐ @media.rating / 5.0</p>
    <p>📥 @media.downloads descargas</p>
    
    @* Formulario para agregar al carrito *@
    <form method="POST" action="@routes.CartController.add(media.id)">
      <label>Cantidad:</label>
      <input type="number" name="quantity" value="1" min="1" max="99">
      <button type="submit" class="btn-primary">🛒 Agregar al carrito</button>
    </form>
  </div>
}
```

---

## Paso 6: Agregar Badge al Navbar

### Archivo: `app/views/navbar.scala.html` (modificar)

```scala
<nav class="navbar">
  <a href="@routes.HomeController.index">Inicio</a>
  <a href="@routes.ShopController.list">Tienda</a>
  
  @* Badge del carrito *@
  <a href="@routes.CartController.view" class="cart-link">
    🛒 Carrito
    <span class="cart-badge" id="cart-count">0</span>
  </a>
  
  @req.session.get("userEmail") match {
    case Some(email) => {
      <a href="@routes.AuthController.account">Mi Cuenta</a>
      <a href="@routes.AuthController.logout">Cerrar Sesión</a>
    }
    case None => {
      <a href="@routes.AuthController.loginForm">Iniciar Sesión</a>
      <a href="@routes.AuthController.registerForm">Registrarse</a>
    }
  }
</nav>

<script>
  // Actualizar contador del carrito cada 2 segundos
  function updateCartCount() {
    fetch('/cart/count')
      .then(response => response.text())
      .then(count => {
        document.getElementById('cart-count').textContent = count;
        if (count > 0) {
          document.getElementById('cart-count').style.display = 'inline';
        }
      });
  }
  
  updateCartCount();
  setInterval(updateCartCount, 2000);
</script>
```

---

## Paso 7: Agregar CSS

### Archivo: `public/stylesheets/cart.css`

```css
.cart-table {
  width: 100%;
  border-collapse: collapse;
  margin: 20px 0;
}

.cart-table th,
.cart-table td {
  padding: 15px;
  text-align: left;
  border-bottom: 1px solid #ddd;
}

.cart-table th {
  background-color: #f5f5f5;
  font-weight: bold;
}

.product-info h3 {
  margin: 0 0 5px 0;
  font-size: 16px;
}

.product-info p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.total-row {
  font-size: 18px;
  background-color: #f9f9f9;
}

.cart-actions {
  margin-top: 20px;
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.cart-badge {
  background-color: red;
  color: white;
  border-radius: 50%;
  padding: 2px 6px;
  font-size: 12px;
  margin-left: 5px;
  display: none; /* Ocultar si está en 0 */
}

.empty-cart {
  text-align: center;
  padding: 50px;
}

.btn {
  padding: 10px 20px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
}

.btn-primary {
  background-color: #007bff;
  color: white;
}

.btn-secondary {
  background-color: #6c757d;
  color: white;
}

.btn-danger {
  background-color: #dc3545;
  color: white;
  padding: 5px 10px;
  font-size: 12px;
}

.btn-small {
  padding: 5px 10px;
  font-size: 12px;
}

input[type="number"] {
  width: 60px;
  padding: 5px;
  margin-right: 5px;
}
```

---

## ✅ Resultado Final

### URLs disponibles:
- `http://localhost:9000/cart` - Ver carrito
- `http://localhost:9000/shop` - Ver productos
- `http://localhost:9000/shop/1` - Detalle de producto

### Funcionalidades:
✅ Agregar productos al carrito  
✅ Ver carrito con productos  
✅ Actualizar cantidades  
✅ Eliminar productos  
✅ Vaciar carrito  
✅ Badge en navbar con cantidad  
✅ Calcular total automáticamente  

---

## 🧪 Cómo Probar

1. **Ejecutar el proyecto:**
   ```bash
   sbt run
   ```

2. **Ir a la tienda:**
   - Visita: `http://localhost:9000/shop`

3. **Agregar producto:**
   - Haz clic en un producto
   - Cambia la cantidad
   - Haz clic en "Agregar al carrito"

4. **Ver carrito:**
   - Haz clic en el ícono del carrito
   - Deberías ver tu producto

5. **Probar funcionalidades:**
   - Cambia la cantidad y haz clic en "Actualizar"
   - Haz clic en "Eliminar"
   - Agrega más productos
   - Haz clic en "Vaciar carrito"

---

## 🔄 Próximas Mejoras

### 1. Guardar carrito en base de datos
```scala
// En lugar de CartRepo en memoria, usar Slick/Anorm
class CartDAO @Inject()(db: Database) {
  def save(userId: Long, cart: Cart): Future[Unit]
  def get(userId: Long): Future[Cart]
}
```

### 2. Asociar carrito a usuario
```scala
// En lugar de sessionId, usar userId
private def getUserId(implicit req: RequestHeader): Option[Long] =
  req.session.get("userId").map(_.toLong)
```

### 3. Proceso de checkout
```scala
// Crear OrderController.scala
def checkout = Action { implicit req =>
  val cart = CartRepo.get(getSessionId)
  // Crear orden
  // Procesar pago
  // Vaciar carrito
  // Enviar email de confirmación
}
```

### 4. Persistir carrito entre sesiones
```scala
// Guardar en cookie o localStorage
// Al hacer login, recuperar carrito guardado
```

---

**¡Ahora tienes un carrito de compras funcional! 🎉**
