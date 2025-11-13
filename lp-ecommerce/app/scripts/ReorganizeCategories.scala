package scripts

import models.{Category, CategoryRepo}

/**
 * Script para reorganizar las categorías con una estructura jerárquica correcta
 */
object ReorganizeCategories {
  
  def run(): Unit = {
    println("🗂️  Reorganizando categorías...")
    
    // 1. Eliminar todas las categorías existentes
    println("🗑️  Eliminando categorías antiguas...")
    CategoryRepo.deleteAll()
    
    // 2. Crear estructura jerárquica
    println("✨ Creando nueva estructura de categorías...")
    
    // NIVEL 0: Raíz principal
    val multimedia = Category(1, "Multimedia", None, "Contenido digital multimedia")
    CategoryRepo.create(multimedia)
    
    // NIVEL 1: Categorías principales
    val audio = Category(10, "Audio", Some(1), "Contenido de audio y música")
    val video = Category(20, "Video", Some(1), "Contenido de video")
    val diseno = Category(30, "Diseño", Some(1), "Recursos de diseño gráfico")
    
    CategoryRepo.create(audio)
    CategoryRepo.create(video)
    CategoryRepo.create(diseno)
    
    // NIVEL 2: Subcategorías de Audio
    val musica = Category(11, "Música", Some(10), "Música en diversos géneros")
    val efectosSonido = Category(12, "Efectos de Sonido", Some(10), "FX y SFX para proyectos")
    
    CategoryRepo.create(musica)
    CategoryRepo.create(efectosSonido)
    
    // NIVEL 3: Géneros de Música
    val rock = Category(111, "Rock", Some(11), "Música rock y subgéneros")
    val electronica = Category(112, "Electrónica", Some(11), "Música electrónica y EDM")
    val lofi = Category(113, "LoFi", Some(11), "Música LoFi y chill")
    val pop = Category(114, "Pop", Some(11), "Música pop")
    val reggaeton = Category(115, "Reggaeton", Some(11), "Reggaeton y música urbana")
    
    CategoryRepo.create(rock)
    CategoryRepo.create(electronica)
    CategoryRepo.create(lofi)
    CategoryRepo.create(pop)
    CategoryRepo.create(reggaeton)
    
    // NIVEL 2: Subcategorías de Video
    val peliculas = Category(21, "Películas", Some(20), "Largometrajes y películas")
    val cortos = Category(22, "Cortos", Some(20), "Videos cortos y contenido breve")
    val documentales = Category(23, "Documentales", Some(20), "Documentales y contenido educativo")
    val videoclips = Category(24, "Videoclips", Some(20), "Videoclips musicales")
    
    CategoryRepo.create(peliculas)
    CategoryRepo.create(cortos)
    CategoryRepo.create(documentales)
    CategoryRepo.create(videoclips)
    
    // NIVEL 2: Subcategorías de Diseño
    val posters = Category(31, "Posters", Some(30), "Diseño de posters y carteles")
    val ilustraciones = Category(32, "Ilustraciones", Some(30), "Ilustraciones y arte digital")
    val plantillas = Category(33, "Plantillas", Some(30), "Plantillas y recursos gráficos")
    val iconos = Category(34, "Íconos", Some(30), "Packs de íconos")
    
    CategoryRepo.create(posters)
    CategoryRepo.create(ilustraciones)
    CategoryRepo.create(plantillas)
    CategoryRepo.create(iconos)
    
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
      println(s"$indent$prefix ${cat.name} (ID: ${cat.id})")
      
      // Mostrar hijas
      val hijas = todas.filter(_.parentId.contains(cat.id))
      hijas.foreach(hija => mostrarCategoria(hija, nivel + 1))
    }
    
    // Mostrar desde la raíz
    todas.filter(_.parentId.isEmpty).foreach(raiz => mostrarCategoria(raiz, 0))
  }
}
