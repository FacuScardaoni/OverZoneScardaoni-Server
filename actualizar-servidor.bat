@echo off
rem Vuelve a generar el .jar del servidor. Usalo despues de cambiar el codigo.
cd /d "%~dp0"
call gradlew.bat jar
echo.
if errorlevel 1 (echo Hubo un error.) else (echo Listo: build\libs\OverZone-Server-1.0.0.jar)
pause
