# Plan de Migración: De Play Framework a Servidor HTTP Manual

**Fecha inicio**: 6 de noviembre de 2025
**Razón**: Requisito de profesora - no usar frameworks

## Estado del Respaldo

✅ **Respaldo completo en**: `lp-ecommerce - copia`
✅ **Código funcional**: Sistema completo con Play Framework
✅ **Fases completadas**: 3/7 (Usuarios, Productos, Promociones)

---

## Estrategia de Migración

### **Código que NO cambia (85%)**
- ✅ Todos los modelos (`User`, `Media`, `Category`, `Promotion`, etc.)
- ✅ Todos los repositorios (`UserRepo`, `MediaRepo`, etc.)
- ✅ Toda la lógica de negocio
- ✅ Sistema de concurrencia (SYNCHRONIZED)
- ✅ Algoritmos de promociones
- ✅ Archivos estáticos (CSS, JS, imágenes)

### **Código que REESCRIBIMOS (15%)**
- ❌ Servidor HTTP (Play → java.net.ServerSocket)
- ❌ Sistema de routing (conf/routes → pattern matching)
- ❌ Controllers (Play Actions → funciones que retornan HttpResponse)
- ❌ Templates (Twirl → funciones String)
- ❌ Sesiones (Play Sessions → Map manual)
- ❌ CSRF (Play CSRF → tokens manuales)

---

## Cronograma

| Día | Tarea | Horas |
|-----|-------|-------|
| **1** | Servidor HTTP básico + Request parser | 4-5h |
| **2** | Response generator + Router básico | 4-5h |
| **3** | Migrar modelos/repos + SessionManager | 3-4h |
| **4** | AuthController + vistas login/register | 4-5h |
| **5** | ShopController + vista tienda | 4-5h |
| **6** | AdminController + vistas admin | 5-6h |
| **7** | CSRF + archivos estáticos | 3-4h |
| **8-10** | Testing, debugging, polish | 6-8h |

**Total estimado**: 10-14 días de trabajo intensivo

---

## Arquitectura Nueva

```
HTTP Request (raw socket)
    ↓
HttpServer.scala (ServerSocket)
    ↓
HttpRequest.scala (parser)
    ↓
Router.scala (pattern matching)
    ↓
Controller (función handler)
    ↓
View (función String)
    ↓
HttpResponse.scala (generator)
    ↓
Socket output (raw bytes)
```

---

## Dependencias Permitidas

```scala
// build.sbt (SOLO Scala stdlib)
scalaVersion := "2.13.12"

// NO más dependencias externas
// Todo con java.net.*, java.io.*, scala.collection.*
```

---

## Próximos Pasos

1. ✅ Crear estructura de carpetas
2. ⏳ Implementar HttpServer básico
3. ⏳ Implementar HttpRequest parser
4. ⏳ Implementar HttpResponse generator
5. ⏳ Implementar Router
6. ⏳ Migrar modelos/repositorios
7. ⏳ Implementar SessionManager
8. ⏳ Migrar controllers
9. ⏳ Convertir templates
10. ⏳ Testing completo

---

**Nota**: Todo el código de negocio (modelos, repos, algoritmos) se COPIA directamente desde `lp-ecommerce - copia` sin modificaciones.
