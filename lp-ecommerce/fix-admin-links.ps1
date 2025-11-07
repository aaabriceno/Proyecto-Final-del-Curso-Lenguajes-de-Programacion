# Script para corregir enlaces de administracion en archivos HTML
$viewsPath = "app\views"
$htmlFiles = Get-ChildItem -Path $viewsPath -Filter "*.html"

Write-Host "Corrigiendo enlaces de administracion..." -ForegroundColor Cyan

foreach ($file in $htmlFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    
    # Corregir enlaces de admin dashboard
    $content = $content -replace 'href="admin\.html"', 'href="/admin"'
    $content = $content -replace 'href="usuarios\.html"', 'href="/admin/users"'
    $content = $content -replace 'href="media\.html"', 'href="/admin/media"'
    $content = $content -replace 'href="categorias\.html"', 'href="/admin/categories"'
    $content = $content -replace 'href="promociones\.html"', 'href="/admin/promotions"'
    $content = $content -replace 'href="solicitudesSaldo\.html"', 'href="/admin/balance/requests"'
    $content = $content -replace 'href="addContent\.html"', 'href="/admin/media/new"'
    $content = $content -replace 'href="estadisticas\.html"', 'href="/admin"'
    
    # Corregir rutas relativas con ../
    $content = $content -replace 'href="\.\./admin\.html"', 'href="/admin"'
    
    # Si hubo cambios, guardar el archivo
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
        Write-Host "Corregido: $($file.Name)" -ForegroundColor Green
    } else {
        Write-Host "Sin cambios: $($file.Name)" -ForegroundColor Gray
    }
}

Write-Host "Proceso completado!" -ForegroundColor Green
