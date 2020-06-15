@ECHO OFF
SETLOCAL ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

cd %~dp0..

SET output_dir=build\javadoc

IF "%1" NEQ "" (
    SET visibility=%1
    SET output_dir="!output_dir!-!visibility!"
    IF "!visibility!" EQU "private" SET linksrc=-linksource
) ELSE (
    SET visibility=protected
)

rmdir /S /Q %output_dir%
mkdir %output_dir%

javadoc -classpath lib\*;build\production\simple-hl7v2 -%visibility% %linksrc% ^
   -sourcepath src -subpackages com.illcode.hl7 -d "%output_dir%" ^
   -windowtitle "Simple-HL7v2 Javadocs" -doctitle "Simple-HL7v2 Javadocs"

::    -overview src\overview.html ^

:END
ENDLOCAL
ECHO ON
@EXIT /B 0
