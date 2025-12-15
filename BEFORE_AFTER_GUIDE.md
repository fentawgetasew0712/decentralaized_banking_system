# 🔄 Before vs. After: System Comparison & Quick Start Guide

## 📊 What Changed? (Simple Comparison)

### BEFORE: Hybrid System ❌

**Architecture**:
```
Your Computer:                    Friend 1:              Friend 2:
┌─────────────┐                  ┌─────────┐           ┌─────────┐
│ BankServer  │◄─────────────────┤  ATM 2  │           │  ATM 3  │
│  (MySQL)    │                  └─────────┘           └─────────┘
└──────┬──────┘                        ▲                     ▲
       │                                │                     │
       │                                └─────────┬───────────┘
       │                                          │
   ┌───▼────┐                               Mutual Exclusion
   │  ATM 1 │                                  (Distributed)
   └────────┘
```

**How It Worked**:
1. You run `run_server.bat` (BankServer)
2. You run `run_atm.bat` (ATM1)
3. Friends run `run_atm.bat` (ATM2, ATM3)
4. All ATMs connect to YOUR BankServer
5. Only YOUR computer has the database

**Problems**:
- ❌ If BankServer crashes → EVERYTHING stops
- ❌ Friends depend on YOUR computer
- ❌ Single point of failure
- ❌ Not truly distributed

---

### AFTER: True Distributed System ✅

**Architecture**:
```
Your Computer:              Friend 1:                Friend 2:
┌─────────────┐            ┌─────────────┐          ┌─────────────┐
│   ATM 1     │◄──────────►│   ATM 2     │◄────────►│   ATM 3     │
│  + MySQL 1  │            │  + MySQL 2  │          │  + MySQL 3  │
└─────────────┘            └─────────────┘          └─────────────┘
      ▲                           ▲                        ▲
      │                           │                        │
      └───────────────────────────┴────────────────────────┘
                    Data Replication (Peer-to-Peer)
```

**How It Works**:
1. You run ONLY `run_atm.bat` (ATM1 with local MySQL)
2. Friend 1 runs `run_atm.bat` (ATM2 with local MySQL)
3. Friend 2 runs `run_atm.bat` (ATM3 with local MySQL)
4. Each ATM has its own database
5. All databases stay synchronized automatically

**Benefits**:
- ✅ No central server - fully distributed
- ✅ Each computer is independent
- ✅ If one crashes, others continue working
- ✅ True distributed system

---

## 🔑 Key Differences Summary

| Feature | BEFORE (Hybrid) | AFTER (Distributed) |
|---------|-----------------|---------------------|
| **BankServer** | Required ✅ | Removed ❌ |
| **run_server.bat** | Must run first | Deleted (not needed) |
| **MySQL Location** | Only on your PC | On ALL computers |
| **Database Name** | `bank_system` | `bank_system_node1/2/3` |
| **Data Storage** | Centralized | Replicated across all nodes |
| **Single Point of Failure** | Yes ❌ | No ✅ |
| **Friend Dependency** | Depend on your server | Independent |
| **Startup Steps** | 2 (server + ATM) | 1 (just ATM) |

---

## 🚀 How to Run the NEW Distributed System

### Prerequisites (IMPORTANT!)

**Everyone needs**:
- ✅ MySQL (XAMPP) installed
- ✅ Complete project folder
- ✅ Same network (or internet connection)

---

### Step-by-Step: Your Computer (Node 1)

#### Step 1: Start MySQL
1. Open **XAMPP Control Panel**
2. Click **Start** next to MySQL
3. Wait for green "Running" status

#### Step 2: Run ATM Node 1
```cmd
cd c:\Users\hp\Desktop\DecentralizedBankingSystem
.\run_atm.bat
```

#### Step 3: Enter Node ID
When prompted:
```
Enter YOUR Node ID (e.g. 1, 2, or 3): 1
```

#### Step 4: Verify Startup
You should see:
```
╔════════════════════════════════════════════════════════════╗
║   STARTING TRUE DISTRIBUTED BANKING SYSTEM                 ║
║   Node ID: 1                                               ║
║   Port: 5001                                               ║
║   NO CENTRAL SERVER - FULLY DISTRIBUTED!                   ║
╚════════════════════════════════════════════════════════════╝

╔════════════════════════════════════════════════════════════╗
║   ATM NODE 1 - DISTRIBUTED DATABASE INITIALIZED            ║
║   Database: bank_system_node1                              ║
║   NO CENTRAL SERVER - FULLY DISTRIBUTED!                   ║
╚════════════════════════════════════════════════════════════╝
```

#### Step 5: Access Web Interface
Open browser:
```
http://localhost:8081
```

---

### Step-by-Step: Friend 1's Computer (Node 2)

#### Step 1: Install MySQL (XAMPP)
1. Download XAMPP from: https://www.apachefriends.org/
2. Install XAMPP
3. Start MySQL in XAMPP Control Panel

#### Step 2: Get Project Files
- Copy the entire `DecentralizedBankingSystem` folder from you
- Save it anywhere (e.g., `C:\Users\Friend\Desktop\`)

#### Step 3: Run ATM Node 2
```cmd
cd C:\Users\Friend\Desktop\DecentralizedBankingSystem
.\run_atm.bat
```

#### Step 4: Enter Node ID
When prompted:
```
Enter YOUR Node ID (e.g. 1, 2, or 3): 2
```

#### Step 5: Verify Startup
Should see:
```
╔════════════════════════════════════════════════════════════╗
║   ATM NODE 2 - DISTRIBUTED DATABASE INITIALIZED            ║
║   Database: bank_system_node2                              ║
╚════════════════════════════════════════════════════════════╝
```

#### Step 6: Access Web Interface
Open browser:
```
http://localhost:8082
```

---

### Step-by-Step: Friend 2's Computer (Node 3)

**Same as Friend 1, but**:
- Enter Node ID: `3`
- Database created: `bank_system_node3`
- Web interface: `http://localhost:8083`

---

## 🧪 Testing the Distributed System

### Test 1: Create Account (on Node 1)
1. Go to `http://localhost:8081`
2. Click "Sign Up"
3. Fill in:
   - ID: `123456789012`
   - Name: `Test User`
   - Password: `test123`
   - Initial Deposit: `1000`
4. Click "Create Account"

**What Happens**:
- ✅ Account created in `bank_system_node1`
- 📡 Replication message sent to Node 2 and Node 3
- ✅ Account automatically created in `bank_system_node2`
- ✅ Account automatically created in `bank_system_node3`

### Test 2: Verify Replication
**On your computer** (MySQL):
```sql
USE bank_system_node1;
SELECT * FROM users;
```

**On Friend 1's computer** (MySQL):
```sql
USE bank_system_node2;
SELECT * FROM users;
```

**On Friend 2's computer** (MySQL):
```sql
USE bank_system_node3;
SELECT * FROM users;
```

**Result**: All 3 should show the SAME account! ✅

### Test 3: Deposit Money (on Node 2)
1. Friend 1 goes to `http://localhost:8082`
2. Login with the account created earlier
3. Deposit `$500`

**What Happens**:
- ✅ Balance updated in `bank_system_node2`
- 📡 Replication to Node 1 and Node 3
- ✅ All databases now show balance: `$1500`

### Test 4: Check Balance (on Node 3)
1. Friend 2 goes to `http://localhost:8083`
2. Login with same account
3. Check balance

**Result**: Should show `$1500` ✅ (replicated from Node 2!)

---

## 🔍 What's Different in the Code?

### Files That Changed:

#### 1. `nodes.properties` - BEFORE:
```properties
# Old version had this:
server=127.0.0.1:9000    ← REMOVED!

1=127.0.0.1:5001
2=10.18.51.25:5001
3=192.168.137.1:5001
```

#### 1. `nodes.properties` - AFTER:
```properties
# New version - NO SERVER!
# Just ATM nodes:
1=127.0.0.1:5001
2=10.18.51.25:5001
3=192.168.137.1:5001
```

#### 2. `ATMNode.java` - BEFORE:
```java
// Old: Connected to BankServer
private final String bankServerIp;
private final int bankServerPort;

public String checkBalance(String user) {
    // Connected to remote BankServer
    Socket socket = new Socket(bankServerIp, bankServerPort);
    // ...
}
```

#### 2. `ATMNode.java` - AFTER:
```java
// New: Has local database
private final Database localDB;

public String checkBalance(String user) {
    // Uses local database
    int balance = localDB.getBalance(user);
    return String.valueOf(balance);
}
```

#### 3. Startup - BEFORE:
```cmd
# Old way (2 steps):
.\run_server.bat    ← Start BankServer first
.\run_atm.bat       ← Then start ATM
```

#### 3. Startup - AFTER:
```cmd
# New way (1 step):
.\run_atm.bat       ← Just start ATM (has built-in database)
```

---

## 📝 Quick Reference Card

### OLD System (Don't use anymore):
```
Step 1: run_server.bat  (on your PC only)
Step 2: run_atm.bat     (on all PCs)
Result: Centralized database on your PC
```

### NEW System (Use this!):
```
Step 1: Start MySQL     (on ALL PCs)
Step 2: run_atm.bat     (on ALL PCs)
Result: Distributed databases on ALL PCs
```

---

## ⚠️ Important Notes

### For Your Friends:
1. **Must install MySQL** - Each computer needs its own MySQL
2. **Must have project folder** - Complete copy of all files
3. **Must enter correct Node ID** - Friend 1 enters `2`, Friend 2 enters `3`

### Database Names:
- Your computer: `bank_system_node1`
- Friend 1: `bank_system_node2`
- Friend 2: `bank_system_node3`

### Web Ports:
- Node 1: Port `8081`
- Node 2: Port `8082`
- Node 3: Port `8083`

---

## 🎯 Summary

### What You DON'T Do Anymore:
- ❌ Run `run_server.bat`
- ❌ Wait for server to start first
- ❌ Worry about server crashing

### What You DO Now:
- ✅ Just run `run_atm.bat`
- ✅ Enter your node ID (1, 2, or 3)
- ✅ System automatically creates local database
- ✅ Replication happens automatically

**That's it! Much simpler!** 🎉
