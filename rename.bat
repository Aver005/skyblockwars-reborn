@echo off
REM Обёртка для rename.ps1. Использование:
REM   rename.bat com.acme.spleef Spleef spleef
REM Меняет пакет/имя плагина/команду шаблона под новый проект.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0rename.ps1" %*
