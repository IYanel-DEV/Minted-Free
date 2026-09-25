@echo off
title Minted Global Shops Editor - Local Server
cd /d "%~dp0"
echo Starting local server at http://localhost:8080
echo Press Ctrl+C to stop
echo.
python -m http.server 8080
pause