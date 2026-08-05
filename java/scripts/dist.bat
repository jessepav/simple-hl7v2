@echo off

SETLOCAL ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

if "%1" == "" (
    echo Usage: dist ^<version^>
    goto END
)

set VERSION=%1

cd %~dp0..
call ant artifact.dist
move dist\simple-hl7v2.jar dist\simple-hl7v2-%VERSION%.jar

call scripts\make-javadoc.bat
jar cvMf dist\simple-hl7v2-%VERSION%-javadoc.jar -C build\javadoc\ .

jar cvMf dist\simple-hl7v2-%VERSION%-sources.jar -C src .

:END
ENDLOCAL
ECHO ON
@EXIT /B 0
