@echo off

setlocal

cd %~dp0..
call ant compile.module.simple-hl7v2.production

endlocal
