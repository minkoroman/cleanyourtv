@echo off
rem Подвійний клік на Windows: запускає інсталятор Sun Home.
chcp 65001 >nul
cd /d "%~dp0"
where py >nul 2>nul && (py -3 install.py %*) || (python install.py %*)
if errorlevel 9009 echo Потрібен Python 3: https://www.python.org/downloads/ (постав галочку "Add to PATH").
pause
