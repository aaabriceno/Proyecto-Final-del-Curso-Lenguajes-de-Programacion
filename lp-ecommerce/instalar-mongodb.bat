@echo off
echo ========================================
echo  Instalando MongoDB Server
echo ========================================
echo.

REM Descargar MongoDB Community Server
echo Descargando MongoDB 7.0...
powershell -Command "Invoke-WebRequest -Uri 'https://fastdl.mongodb.org/windows/mongodb-windows-x86_64-7.0.14-signed.msi' -OutFile '%TEMP%\mongodb-installer.msi'"

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: No se pudo descargar MongoDB
    echo Por favor descarga manualmente desde:
    echo https://www.mongodb.com/try/download/community
    pause
    exit /b 1
)

echo.
echo Iniciando instalador...
echo IMPORTANTE: Marca la opcion "Install MongoDB as a Service"
echo.
start /wait msiexec /i "%TEMP%\mongodb-installer.msi"

echo.
echo Verificando instalacion...
sc query MongoDB >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo  MongoDB instalado correctamente!
    echo ========================================
    echo.
    echo Iniciando servicio...
    net start MongoDB
    echo.
    echo Listo! MongoDB esta corriendo.
) else (
    echo.
    echo ADVERTENCIA: No se detecto el servicio MongoDB
    echo Puede que necesites instalarlo manualmente.
)

echo.
pause
