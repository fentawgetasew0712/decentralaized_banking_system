# ATM3 Setup Instructions for Your Friend

## System Information
- **Friend's IP Address**: `192.168.137.1` (Local Area Connection* 2)
- **ATM Node**: ATM3 (Node 3)
- **Port**: 5001

---

## Prerequisites
Your friend needs:
1. **Java JDK 8 or higher** installed
2. The complete `DecentralizedBankingSystem` folder
3. Network connectivity to your computer

---

## Step-by-Step Instructions

### Step 1: Verify Java Installation
Open Command Prompt and run:
```cmd
java -version
```
If Java is not installed, download it from: https://www.oracle.com/java/technologies/downloads/

### Step 2: Get the Project Files
Your friend needs the entire `DecentralizedBankingSystem` folder. You can:
- Share it via USB drive
- Use file sharing (Google Drive, OneDrive, etc.)
- Use network file sharing

### Step 3: Navigate to Project Directory
Open Command Prompt and navigate to the project:
```cmd
cd C:\Path\To\DecentralizedBankingSystem
```
(Replace with the actual path where they saved the folder)

### Step 4: Run ATM3
Execute the following command:
```cmd
.\run_atm.bat 3
```

**Important**: The number `3` tells the system this is ATM3.

---

## What Should Happen

When ATM3 starts successfully, your friend should see:
```
╔════════════════════════════════════════════════════════════╗
║         DECENTRALIZED BANKING SYSTEM - ATM 3               ║
╔════════════════════════════════════════════════════════════╗
[INFO] ATM Node 3 started on port 5001
[INFO] Connected to Bank Server at 127.0.0.1:9000
[INFO] Discovered peer nodes: 2
```

---

## Network Configuration Notes

### Important IP Address Information
Your friend's computer has multiple network adapters:

1. **Ethernet (10.18.51.21)** - University/School network
2. **Local Area Connection* 2 (192.168.137.1)** - Mobile Hotspot/Shared Connection ✅ **USING THIS**

We configured the system to use `192.168.137.1` because it's their active connection.

### Firewall Configuration
Your friend may need to allow Java through Windows Firewall:
1. Open **Windows Defender Firewall**
2. Click **Allow an app through firewall**
3. Find **Java** or click **Allow another app**
4. Select Java and check both **Private** and **Public** networks

---

## Troubleshooting

### Problem: "Connection Refused" or "Cannot Connect to Server"
**Solution**: 
- Ensure your server (127.0.0.1:9000) is running first
- Check that all computers are on the same network
- Verify firewall settings

### Problem: "Port 5001 already in use"
**Solution**: 
- Close any other programs using port 5001
- Or modify the port in `nodes.properties` (change 5001 to another port like 5003)

### Problem: "Class not found" or "Java errors"
**Solution**: 
- Make sure Java JDK is installed (not just JRE)
- Verify the `src` folder and all `.java` files are present

---

## Testing the Connection

Once ATM3 is running, test it by:
1. Opening a web browser on your friend's computer
2. Going to: `http://192.168.137.1:8080`
3. Try creating an account or logging in

---

## Network Topology

```
┌─────────────────────────────────────────────────────────┐
│                    YOUR COMPUTER                        │
│  - Server: 127.0.0.1:9000                              │
│  - ATM1: 127.0.0.1:5001                                │
│  - Web Server: 127.0.0.1:8080                          │
└─────────────────────────────────────────────────────────┘
                         │
                         │ Network Connection
                         │
┌─────────────────────────────────────────────────────────┐
│                  FRIEND'S COMPUTER                      │
│  - ATM3: 192.168.137.1:5001                            │
└─────────────────────────────────────────────────────────┘
```

---

## Quick Reference Commands

| Action | Command |
|--------|---------|
| Start ATM3 | `.\run_atm.bat 3` |
| Check Java | `java -version` |
| Check IP | `ipconfig` |
| Stop ATM3 | Press `Ctrl+C` in the Command Prompt |

---

## Contact Information
If your friend encounters issues, they should:
1. Check the error messages in the Command Prompt
2. Verify network connectivity: `ping 127.0.0.1` (your computer's IP)
3. Ensure the server is running on your computer first

---

**Note**: The server and ATM1 should be running on YOUR computer before your friend starts ATM3.
