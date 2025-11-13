# ===============================================
# Script para abrir el puerto 9000 en Windows Firewall
# Ejecutar como ADMINISTRADOR
# ===============================================

Write-Host "🔥 Configurando Windows Firewall para LP Studios..." -ForegroundColor Cyan
Write-Host ""

# Verificar si se ejecuta como administrador
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "❌ ERROR: Este script debe ejecutarse como ADMINISTRADOR" -ForegroundColor Red
    Write-Host ""
    Write-Host "Pasos:" -ForegroundColor Yellow
    Write-Host "1. Cierra esta ventana" -ForegroundColor White
    Write-Host "2. Click derecho en PowerShell" -ForegroundColor White
    Write-Host "3. Selecciona 'Ejecutar como administrador'" -ForegroundColor White
    Write-Host "4. Ejecuta nuevamente: .\abrir-firewall.ps1" -ForegroundColor White
    Write-Host ""
    Read-Host "Presiona ENTER para salir"
    exit
}

# Eliminar regla anterior si existe
Write-Host "🔍 Verificando reglas existentes..." -ForegroundColor Yellow
$existingRule = Get-NetFirewallRule -DisplayName "LP Studios Server" -ErrorAction SilentlyContinue

if ($existingRule) {
    Write-Host "📝 Eliminando regla anterior..." -ForegroundColor Yellow
    Remove-NetFirewallRule -DisplayName "LP Studios Server"
}

# Crear nueva regla
Write-Host "✅ Creando regla de firewall..." -ForegroundColor Green
New-NetFirewallRule `
    -DisplayName "LP Studios Server" `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort 9000 `
    -Profile Any `
    -Description "Permite conexiones al servidor LP Studios en puerto 9000"

Write-Host ""
Write-Host "✅ ¡Firewall configurado correctamente!" -ForegroundColor Green
Write-Host ""
Write-Host "🌐 Ahora puedes conectarte desde otros dispositivos en tu red local" -ForegroundColor Cyan
Write-Host ""
Write-Host "📱 Desde tu laptop/celular, abre el navegador y ve a:" -ForegroundColor Yellow
Write-Host "   http://TU_IP:9000" -ForegroundColor White
Write-Host ""
Write-Host "💡 Para ver tu IP, ejecuta en PowerShell:" -ForegroundColor Cyan
Write-Host "   ipconfig" -ForegroundColor White
Write-Host "   (busca 'Dirección IPv4')" -ForegroundColor Gray
Write-Host ""
Read-Host "Presiona ENTER para continuar"
