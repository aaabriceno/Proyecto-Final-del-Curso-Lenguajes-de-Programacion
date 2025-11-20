@echo off
echo ================================================
echo  SYNC de Atlas -> MongoDB Localhost
echo ================================================

echo.
echo Haciendo mongodump desde Atlas...
"C:\Program Files\MongoDB\Tools\100\bin\mongodump.exe" ^
  --uri "mongodb+srv://anthonybriceno_db_user:VvOjX7zqYxNULOZH@lp-ecommerce-cluster.cmr7cbl.mongodb.net/lp_ecommerce?retryWrites=true&w=majority&authSource=admin" ^
  --out "C:\backup_from_cloud"

echo.
echo Restaurando en localhost...
"C:\Program Files\MongoDB\Tools\100\bin\mongorestore.exe" ^
  --drop ^
  --db lp_ecommerce ^
  "C:\backup_from_cloud\lp_ecommerce"

echo.
echo ================================================
echo  SYNC COMPLETADO (Atlas -> Localhost)
echo ================================================
pause
