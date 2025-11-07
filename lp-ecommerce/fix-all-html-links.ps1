# Script para corregir TODOS los enlaces HTML en el proyecto
$viewsPath = "app\views"
$htmlFiles = Get-ChildItem -Path $viewsPath -Filter "*.html"

Write-Host "Corrigiendo enlaces en archivos HTML..." -ForegroundColor Cyan

foreach ($file in $htmlFiles) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    
    # Reemplazar enlaces .html por rutas sin extension
    $content = $content -replace 'href="index\.html"', 'href="/"'
    $content = $content -replace 'href="login\.html"', 'href="/login"'
    $content = $content -replace 'href="register\.html"', 'href="/register"'
    $content = $content -replace 'href="shop\.html"', 'href="/shop"'
    $content = $content -replace 'href="media_list\.html"', 'href="/shop"'
    $content = $content -replace 'href="cart\.html"', 'href="/cart"'
    $content = $content -replace 'href="purchase_page\.html"', 'href="/purchase"'
    
    # Reemplazar rutas relativas con ../
    $content = $content -replace 'href="\.\./index\.html"', 'href="/"'
    $content = $content -replace 'href="\.\./login\.html"', 'href="/login"'
    $content = $content -replace 'href="\.\./register\.html"', 'href="/register"'
    $content = $content -replace 'href="\.\./shop\.html"', 'href="/shop"'
    $content = $content -replace 'href="\.\./cart\.html"', 'href="/cart"'
    
    # Rutas de administrador
    $content = $content -replace 'href="admin_dashboard\.html"', 'href="/admin"'
    $content = $content -replace 'href="admin_users\.html"', 'href="/admin/users"'
    $content = $content -replace 'href="admin_media\.html"', 'href="/admin/media"'
    $content = $content -replace 'href="admin_categories\.html"', 'href="/admin/categories"'
    $content = $content -replace 'href="admin_promotions\.html"', 'href="/admin/promotions"'
    $content = $content -replace 'href="admin_balance_requests\.html"', 'href="/admin/balance/requests"'
    
    # Rutas de usuario
    $content = $content -replace 'href="user_account\.html"', 'href="/user/account"'
    $content = $content -replace 'href="user_info\.html"', 'href="/user/info"'
    $content = $content -replace 'href="user_downloads\.html"', 'href="/user/downloads"'
    
    # Corregir rutas de CSS/JS
    $content = $content -replace 'href="\.\./assets/', 'href="/assets/'
    $content = $content -replace 'href="assets/', 'href="/assets/'
    $content = $content -replace 'src="\.\./assets/', 'src="/assets/'
    $content = $content -replace 'src="assets/', 'src="/assets/'
    
    # Si hubo cambios, guardar el archivo
    if ($content -ne $originalContent) {
        Set-Content -Path $file.FullName -Value $content -Encoding UTF8 -NoNewline
        Write-Host "Corregido: $($file.Name)" -ForegroundColor Green
    } else {
        Write-Host "Sin cambios: $($file.Name)" -ForegroundColor Gray
    }
}

Write-Host "Proceso completado!" -ForegroundColor Green
