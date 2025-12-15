# How to Run the Decentralized Banking System

This guide explains how to start the Bank Server and ATM Nodes on different computers.

## Prerequisites
1.  **Java Installed**: Ensure Java is installed on all computers.
    - Check by running: `java -version` in Command Prompt.
2.  **Network Connection**: All computers must be on the same network (e.g., connected to same Wi-Fi).
    - You have verified your IPs are in the `10.18.51.x` range.
3.  **Firewall**: If connections fail, try temporarily disabling Windows Firewall or allowing Java through the firewall.

## Step 1: Start the Bank Server (On YOUR PC)

The computer identified as `server=10.18.51.164` in `nodes.properties` (Your PC) must start the server first.

1.  Open **Command Prompt** (cmd) on your PC.
2.  Navigate to the project folder:
    ```cmd
    cd C:\Users\hp\Desktop\DecentralizedBankingSystem
    ```
3.  Compile the code (if not already done):
    ```cmd
    .\compile.bat
    ```
4.  Run the Server:
    ```cmd
    .\run_server.bat
    ```
    *You should see a message indicating the server is running.*

## Step 2: Start ATM Nodes (On Respective PCs)

### On Your PC (ATM 1)
1.  Open distinct **Command Prompt** window.
2.  Navigate to the project folder.
3.  Run ATM 1:
    ```cmd
    .\run_atm.bat
    ```
    *When asked for Node ID, enter `1` and press Enter.*
    *The GUI for ATM 1 should appear.*

### On Friend 1's PC (ATM 2)
1.  Copy the entire `DecentralizedBankingSystem` folder to their PC.
2.  Open **Command Prompt** or **PowerShell**.
3.  Navigate to the folder.
4.  Run ATM 2:
    ```cmd
    .\run_atm.bat
    ```
    *When asked for Node ID, enter `2` and press Enter.*

### On Friend 2's PC (ATM 3)
1.  Copy the entire `DecentralizedBankingSystem` folder to their PC.
2.  Open **Command Prompt** or **PowerShell**.
3.  Navigate to the folder.
4.  Run ATM 3:
    ```cmd
    .\run_atm.bat
    ```
    *When asked for Node ID, enter `3` and press Enter.*

## Troubleshooting
- **"The term ... is not recognized"**:
    - **CRITICAL: You are in the wrong folder.**
    - Look at the command prompt. If it says `C:\Users\Dagne>`, you are NOT in the project folder.
    - You must mistakenly be trying to run the file from outside the folder.
    - **Fix**:
        1. Find where you pasted the `DecentralizedBankingSystem` folder.
        2. Type `cd Desktop\DecentralizedBankingSystem` (or the correct path).
        3. Type `dir`. **You MUST see `run_atm.bat` in the list.**
        4. IF you don't see it, you are still in the wrong place. Do not try to run it until you find it.
    - **PowerShell**: You must type `.\` before the file name (e.g., `.\run_atm.bat`).
    - **No Dots at End**: Do NOT type a dot `.` at the very end of the command.
- **Connection Refused**: Ensure the Server is running first. Check that the IP addresses in `src/config/nodes.properties` match the actual IP addresses of the machines running those nodes.
- **"Class not found"**: Make sure you ran `compile.bat` first.
