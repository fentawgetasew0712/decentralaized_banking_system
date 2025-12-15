@echo off
title BANK SERVER
echo Starting Bank Server...
java -cp "bin;lib\mysql-connector-j-9.2.0.jar" bank.BankServer
pause
