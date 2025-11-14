package scripts

import models.{Category, CategoryRepo}

/**
 * Script para reorganizar las categorías con una estructura jerárquica correcta
 */
object ReorganizeCategories {
  
  def main(args: Array[String]): Unit = {
    run()
  }
  
  def run(): Unit = {
    println("🗂️  Reorganizando categorías...")
    
    // 1. Eliminar todas las categorías existentes
    println("🗑️  Eliminando categorías antiguas...")
    CategoryRepo.deleteAll()
    
    // 2. Crear estructura jerárquica
    println("✨ Creando nueva estructura de categorías...")
    
    // ==================================================
    // CATEGORÍAS DIGITALES (Contenido descargable)
    // ==================================================
    
    // NIVEL 0: Raíz principal DIGITAL
    val multimedia = Category(1, "Multimedia", None, "Contenido digital multimedia", "digital")
    CategoryRepo.create(multimedia)
    
    // NIVEL 1: Categorías principales
    val audio = Category(10, "Audio", Some(1), "Contenido de audio y música", "digital")
    val video = Category(20, "Video", Some(1), "Contenido de video", "digital")
    val diseno = Category(30, "Diseño", Some(1), "Recursos de diseño gráfico", "digital")
    
    CategoryRepo.create(audio)
    CategoryRepo.create(video)
    CategoryRepo.create(diseno)
    
    // NIVEL 2: Subcategorías de Audio
    val musica = Category(11, "Música", Some(10), "Música en diversos géneros", "digital")
    val efectosSonido = Category(12, "Efectos de Sonido", Some(10), "FX y SFX para proyectos", "digital")
    
    CategoryRepo.create(musica)
    CategoryRepo.create(efectosSonido)
    
    // NIVEL 3: Géneros de Música
    val rock = Category(111, "Rock", Some(11), "Música rock y subgéneros", "digital")
    val electronica = Category(112, "Electrónica", Some(11), "Música electrónica y EDM", "digital")
    val lofi = Category(113, "LoFi", Some(11), "Música LoFi y chill", "digital")
    val pop = Category(114, "Pop", Some(11), "Música pop", "digital")
    val reggaeton = Category(115, "Reggaeton", Some(11), "Reggaeton y música urbana", "digital")
    
    CategoryRepo.create(rock)
    CategoryRepo.create(electronica)
    CategoryRepo.create(lofi)
    CategoryRepo.create(pop)
    CategoryRepo.create(reggaeton)
    
    // NIVEL 2: Subcategorías de Video
    val peliculas = Category(21, "Películas", Some(20), "Largometrajes y películas", "digital")
    val cortos = Category(22, "Cortos", Some(20), "Videos cortos y contenido breve", "digital")
    val documentales = Category(23, "Documentales", Some(20), "Documentales y contenido educativo", "digital")
    val videoclips = Category(24, "Videoclips", Some(20), "Videoclips musicales", "digital")
    
    CategoryRepo.create(peliculas)
    CategoryRepo.create(cortos)
    CategoryRepo.create(documentales)
    CategoryRepo.create(videoclips)
    
    // NIVEL 2: Subcategorías de Diseño
    val posters = Category(31, "Posters", Some(30), "Diseño de posters y carteles", "digital")
    val ilustraciones = Category(32, "Ilustraciones", Some(30), "Ilustraciones y arte digital", "digital")
    val plantillas = Category(33, "Plantillas", Some(30), "Plantillas y recursos gráficos", "digital")
    val iconos = Category(34, "Íconos", Some(30), "Packs de íconos", "digital")
    
    CategoryRepo.create(posters)
    CategoryRepo.create(ilustraciones)
    CategoryRepo.create(plantillas)
    CategoryRepo.create(iconos)
    
    // ==================================================
    // 🖥️ CATEGORÍAS HARDWARE (Productos físicos)
    // ==================================================
    
    // NIVEL 0: Raíz principal HARDWARE
    val tecnologia = Category(2, "Tecnología", None, "Productos tecnológicos físicos", "hardware")
    CategoryRepo.create(tecnologia)
    
    // NIVEL 1: Smartphones
    val smartphones = Category(50, "Smartphones", Some(2), "Teléfonos inteligentes", "hardware")
    CategoryRepo.create(smartphones)
    
    // NIVEL 2: Marcas de Smartphones
    val android = Category(51, "Android", Some(50), "Smartphones Android", "hardware")
    val iphone = Category(52, "iPhone", Some(50), "Smartphones Apple iPhone", "hardware")
    
    CategoryRepo.create(android)
    CategoryRepo.create(iphone)
    
    // NIVEL 3: Marcas Android
    val samsung = Category(511, "Samsung", Some(51), "Smartphones Samsung", "hardware")
    val xiaomi = Category(512, "Xiaomi", Some(51), "Smartphones Xiaomi", "hardware")
    val motorola = Category(513, "Motorola", Some(51), "Smartphones Motorola", "hardware")
    
    CategoryRepo.create(samsung)
    CategoryRepo.create(xiaomi)
    CategoryRepo.create(motorola)
    
    // NIVEL 3: Modelos iPhone
    val iphone15 = Category(521, "iPhone 15", Some(52), "iPhone 15 Series", "hardware")
    val iphone14 = Category(522, "iPhone 14", Some(52), "iPhone 14 Series", "hardware")
    
    CategoryRepo.create(iphone15)
    CategoryRepo.create(iphone14)
    
    // NIVEL 1: Computadoras
    val computadoras = Category(60, "Computadoras", Some(2), "Computadoras y laptops", "hardware")
    CategoryRepo.create(computadoras)
    
    // NIVEL 2: Tipos de Computadoras
    val laptops = Category(61, "Laptops", Some(60), "Laptops y notebooks", "hardware")
    val desktop = Category(62, "Desktop", Some(60), "Computadoras de escritorio", "hardware")
    
    CategoryRepo.create(laptops)
    CategoryRepo.create(desktop)
    
    // NIVEL 3: Categorías de Laptops
    val gaming = Category(611, "Gaming", Some(61), "Laptops para gaming", "hardware")
    val oficina = Category(612, "Oficina", Some(61), "Laptops para oficina", "hardware")
    
    CategoryRepo.create(gaming)
    CategoryRepo.create(oficina)
    
    // NIVEL 1: Accesorios
    val accesorios = Category(70, "Accesorios", Some(2), "Accesorios tecnológicos", "hardware")
    CategoryRepo.create(accesorios)
    
    // NIVEL 2: Tipos de Accesorios
    val audioHardware = Category(71, "Audio", Some(70), "Accesorios de audio", "hardware")
    val cables = Category(72, "Cables", Some(70), "Cables y conectores", "hardware")
    
    CategoryRepo.create(audioHardware)
    CategoryRepo.create(cables)
    
    // NIVEL 3: Accesorios de Audio
    val audifonos = Category(711, "Audífonos", Some(71), "Audífonos y earbuds", "hardware")
    val parlantes = Category(712, "Parlantes", Some(71), "Parlantes y altavoces", "hardware")
    
    CategoryRepo.create(audifonos)
    CategoryRepo.create(parlantes)
    
    println("✅ Categorías reorganizadas exitosamente!")
    println(s"📊 Total de categorías creadas: ${CategoryRepo.all.size}")
    
    // Mostrar estructura
    println("\n📂 Estructura de categorías:")
    mostrarEstructura()
  }
  
  def mostrarEstructura(): Unit = {
    val todas = CategoryRepo.all
    
    def mostrarCategoria(cat: Category, nivel: Int): Unit = {
      val indent = "  " * nivel
      val prefix = if (nivel == 0) "📦" else if (nivel == 1) "📁" else if (nivel == 2) "📂" else "📄"
      val typeIcon = if (cat.productType == "digital") "💾" else "🖥️"
      println(s"$indent$prefix ${cat.name} (ID: ${cat.id}) $typeIcon")
      
      // Mostrar hijas
      val hijas = todas.filter(_.parentId.contains(cat.id))
      hijas.foreach(hija => mostrarCategoria(hija, nivel + 1))
    }
    
    // Mostrar desde la raíz
    todas.filter(_.parentId.isEmpty).foreach(raiz => mostrarCategoria(raiz, 0))
  }
}
