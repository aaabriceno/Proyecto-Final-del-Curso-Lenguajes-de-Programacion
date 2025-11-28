# Script to open port 9000 in Windows Firewall
# Run as administrator

Write-Host "Configurando Windows Firewall para LP Studios..."

# Check admin
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "ERROR: Ejecuta este script como ADMINISTRADOR."
    Write-Host "Pasos: cierra esta ventana, click derecho en PowerShell -> Ejecutar como administrador, luego .\\abrir-firewall.ps1"
    Read-Host "Presiona ENTER para salir"
    exit
}

$ruleName = "LP Studios Server"
$port = 9000

Write-Host "Verificando regla existente..."
$existingRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if ($existingRule) {
    Write-Host "Eliminando regla anterior..."
    Remove-NetFirewallRule -DisplayName $ruleName
}

Write-Host "Creando regla de firewall..."
New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow -Protocol TCP -LocalPort $port -Profile Any -Description "Permite conexiones al servidor LP Studios en puerto 9000"

Write-Host "Listo. Regla creada para puerto $port."
Write-Host "Abre desde otros dispositivos: http://TU_IP:$port"
Write-Host "Para ver tu IP, ejecuta: ipconfig (Direccion IPv4)"
Read-Host "Presiona ENTER para continuar"
