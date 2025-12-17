@echo off
echo Compiling ATM Project...
if not exist "bin" mkdir bin

cd src
echo Compiling from src directory...
javac -cp "..\lib\*;." -d ..\bin algorithm\*.java bank\*.java banking\*.java

if %errorlevel% neq 0 (
    echo Compilation Failed!
    cd ..
    exit /b %errorlevel%
)

echo Compilation Successful.
cd ..
pause
