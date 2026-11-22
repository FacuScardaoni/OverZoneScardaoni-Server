@echo off
rem Abre el servidor de OverZone con doble click.
rem Si el .jar no existe todavia, lo genera primero.
title Servidor OverZone
cd /d "%~dp0"

set JAR=OverZone-Server-1.0.0.jar
if exist "%JAR%" goto abrir
set JAR=build\libs\OverZone-Server-1.0.0.jar
if exist "%JAR%" goto abrir

echo Generando el servidor por primera vez (puede tardar unos minutos)...
call gradlew.bat jar
if errorlevel 1 goto error

:abrir
java -jar "%JAR%"
echo.
echo El servidor se cerro.
pause
exit /b 0

:error
echo.
echo No se pudo generar el servidor. Revisa el mensaje de arriba.
pause
