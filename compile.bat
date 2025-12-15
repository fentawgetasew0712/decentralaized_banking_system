@echo off
if not exist "bin" mkdir bin
echo Compiling...
javac -cp ".;lib\mysql-connector-j-9.2.0.jar" -d bin src/bank/*.java src/banking/*.java src/algorithm/*.java src/banking/*.java
if %errorlevel% neq 0 (
    echo Compilation Failed!
    pause
    exit /b %errorlevel%
)
echo Compilation Successful!
pause
