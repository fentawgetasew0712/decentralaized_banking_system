# 🔧 Troubleshooting Guide for Friend's Computer

## Error: `.\run_atm.bat` is not recognized

This error means either:
1. The file doesn't exist in the current directory
2. You're in the wrong directory
3. The file wasn't copied properly

---

## ✅ Solution Steps

### Step 1: Verify You're in the Right Directory
```powershell
# Check current directory
pwd

# List files in current directory
dir
```

You should see files like:
- `run_atm.bat`
- `run_server.bat`
- `compile.bat`
- `src` (folder)
- `bin` (folder)

### Step 2: If Files Are Missing
The entire project folder wasn't copied properly. You need these files:

**Required Files & Folders:**
```
DecentralizedBankingSystem/
├── run_atm.bat          ⚠️ REQUIRED
├── run_server.bat       ⚠️ REQUIRED
├── compile.bat          ⚠️ REQUIRED
├── src/                 ⚠️ REQUIRED (entire folder)
├── bin/                 ⚠️ REQUIRED (entire folder)
├── lib/                 (if exists)
├── accounts.csv         (will be created)
└── nodes.properties     (in src/config/)
```

**Action**: Ask your friend to send you the COMPLETE folder again.

---

## 🚀 Alternative: Run Directly with Java

If the batch file is missing, your friend can run ATM3 manually:

### Step 1: Navigate to Project Directory
```powershell
cd "C:\Users\SW\Downloads\AyuGram Desktop\DecentralizedBankingSystem"
```

### Step 2: Compile the Code (if needed)
```powershell
javac -d bin -sourcepath src src/banking/*.java src/banking/server/*.java src/banking/atm/*.java src/banking/web/*.java src/config/*.java
```

### Step 3: Run ATM3
```powershell
java -cp bin;src banking.ATMApp 3
```

**Note**: The `3` at the end is CRITICAL - it tells the system this is ATM3.

---

## 🔍 Detailed Diagnosis

### Check 1: Verify Directory
```powershell
# Show current location
Get-Location

# Should show something like:
# C:\Users\SW\Downloads\AyuGram Desktop\DecentralizedBankingSystem
```

### Check 2: List All Files
```powershell
# List all files
Get-ChildItem

# Or use:
dir
```

### Check 3: Check if run_atm.bat Exists
```powershell
Test-Path .\run_atm.bat
```
- If it returns `True` → File exists, try running with `cmd /c run_atm.bat 3`
- If it returns `False` → File is missing, use manual Java command

---

## 💡 Quick Fix Commands

### Option A: If File Exists but Won't Run (PowerShell Issue)
```powershell
# Run using cmd
cmd /c run_atm.bat 3
```

### Option B: Run Directly with Java
```powershell
java -cp bin;src banking.ATMApp 3
```

### Option C: Switch to Command Prompt (Not PowerShell)
```powershell
# Open Command Prompt instead
cmd

# Then run:
.\run_atm.bat 3
```

---

## 🎯 Recommended Solution

**Tell your friend to do this:**

1. **Open Command Prompt (NOT PowerShell)**
   - Press `Win + R`
   - Type `cmd`
   - Press Enter

2. **Navigate to the folder**
   ```cmd
   cd "C:\Users\SW\Downloads\AyuGram Desktop\DecentralizedBankingSystem"
   ```

3. **Run ATM3**
   ```cmd
   run_atm.bat 3
   ```
   OR
   ```cmd
   java -cp bin;src banking.ATMApp 3
   ```

---

## 📋 Pre-Flight Checklist

Before running, verify:
- [ ] Java is installed: `java -version`
- [ ] You're in the correct directory: `dir` shows `run_atm.bat`
- [ ] The `bin` folder exists and has `.class` files
- [ ] The `src` folder exists and has `.java` files
- [ ] Your server (127.0.0.1:9000) is running first

---

## 🌐 Network Configuration Check

Your friend's IP: `192.168.137.1`

**Test network connectivity:**
```powershell
# Check IP address
ipconfig

# Should show 192.168.137.1 on Local Area Connection* 2
```

---

## ❗ Common Issues

### Issue 1: PowerShell vs Command Prompt
**Problem**: PowerShell has different syntax than Command Prompt
**Solution**: Use `cmd` instead of PowerShell, or use `cmd /c run_atm.bat 3`

### Issue 2: Path with Spaces
**Problem**: `AyuGram Desktop` has a space
**Solution**: Use quotes: `cd "C:\Users\SW\Downloads\AyuGram Desktop\DecentralizedBankingSystem"`

### Issue 3: Missing Compiled Files
**Problem**: `bin` folder is empty
**Solution**: Run `compile.bat` first, or use the javac command above

---

## 📞 What to Send Back

If still having issues, ask your friend to send:
1. Output of: `dir` (to see what files exist)
2. Output of: `pwd` or `cd` (to see current directory)
3. Output of: `java -version` (to verify Java is installed)

---

## ✅ Success Indicators

When ATM3 runs successfully, your friend should see:
```
╔════════════════════════════════════════════════════════════╗
║         DECENTRALIZED BANKING SYSTEM - ATM 3               ║
╚════════════════════════════════════════════════════════════╝
[INFO] ATM Node 3 started on port 5001
[INFO] Attempting to connect to Bank Server...
```

If you see errors about "Connection refused", that's normal - it means the server on your computer needs to be running first.
