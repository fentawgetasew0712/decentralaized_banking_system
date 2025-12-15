# ⏱️ Performance Analysis: Distributed System Timing

## 🎯 Operation Time Estimates

### Single Node Operation (Local Only)
If only ONE node is running:

| Operation | Time | Details |
|-----------|------|---------|
| **Account Creation** | ~50-100ms | Local MySQL INSERT |
| **Login** | ~20-50ms | Local MySQL SELECT + password check |
| **Balance Check** | ~10-30ms | Local MySQL SELECT |
| **Deposit** | ~50-100ms | Local MySQL UPDATE |
| **Withdrawal** | ~50-100ms | Local MySQL UPDATE + validation |
| **Transfer** | ~100-150ms | Two MySQL UPDATEs |

---

### Multi-Node Operation (With Replication)
With ALL 3 nodes running:

| Operation | Component | Time | Details |
|-----------|-----------|------|---------|
| **Account Creation** | | | |
| - Distributed Lock | Ricart-Agrawala | 100-500ms | Request + wait for 2 replies |
| - Local DB Insert | MySQL | 50-100ms | Create account locally |
| - Replication Broadcast | Network | 50-200ms | Send to 2 peers |
| - Peer Processing | Remote MySQL | 50-100ms | Peers update their DBs |
| **TOTAL** | | **250-900ms** | **~0.5 seconds typical** |
| | | | |
| **Deposit/Withdrawal** | | | |
| - Distributed Lock | Ricart-Agrawala | 100-500ms | Request + wait for 2 replies |
| - Local DB Update | MySQL | 50-100ms | Update balance locally |
| - Replication Broadcast | Network | 50-200ms | Send to 2 peers |
| - Peer Processing | Remote MySQL | 50-100ms | Peers update their DBs |
| **TOTAL** | | **250-950ms** | **~0.5 seconds typical** |
| | | | |
| **Transfer** | | | |
| - Distributed Lock | Ricart-Agrawala | 100-500ms | Request + wait for 2 replies |
| - Local DB Updates | MySQL | 100-150ms | Update 2 accounts |
| - Replication Broadcast | Network | 100-400ms | Send 2 updates to 2 peers |
| - Peer Processing | Remote MySQL | 100-150ms | Peers update 2 accounts |
| **TOTAL** | | **400-1200ms** | **~0.7 seconds typical** |

---

## 📊 Detailed Breakdown

### Phase 1: Distributed Mutual Exclusion (100-500ms)

**What Happens**:
1. ATM1 sends REQUEST to ATM2 and ATM3
2. ATM2 replies: "OK, proceed"
3. ATM3 replies: "OK, proceed"
4. ATM1 waits for both replies

**Timing Factors**:
- **Network latency**: 10-50ms per message (LAN)
- **Processing time**: 5-10ms per node
- **Number of peers**: 2 peers = 2 round trips

**Calculation**:
```
Best case:  (10ms latency + 5ms processing) × 2 peers = 30ms
Typical:    (30ms latency + 10ms processing) × 2 peers = 80ms
Worst case: (100ms latency + 20ms processing) × 2 peers = 240ms
+ Timeout buffer: +100ms
= 100-500ms total
```

---

### Phase 2: Local Database Operation (50-150ms)

**What Happens**:
- MySQL INSERT/UPDATE on local database
- Single transaction

**Timing Factors**:
- **MySQL performance**: 10-50ms for simple queries
- **Disk I/O**: 20-100ms if writing to disk
- **Transaction overhead**: 10-20ms

**Calculation**:
```
Simple SELECT: 10-30ms
Simple UPDATE: 50-100ms
Complex operation (transfer): 100-150ms
```

---

### Phase 3: Replication Broadcast (50-400ms)

**What Happens**:
1. ATM1 sends replication message to ATM2
2. ATM1 sends replication message to ATM3
3. Messages sent in parallel (not sequential)

**Timing Factors**:
- **Network latency**: 10-50ms per peer (LAN)
- **Message size**: ~100 bytes (very small)
- **Parallel sending**: Both peers contacted simultaneously

**Calculation**:
```
Best case:  10ms × 2 peers (parallel) = 10ms
Typical:    30ms × 2 peers (parallel) = 30ms
Worst case: 100ms × 2 peers (parallel) = 100ms

For transfer (2 messages):
Typical: 30ms × 2 messages × 2 peers = 120ms
```

---

### Phase 4: Peer Database Updates (50-150ms)

**What Happens**:
- ATM2 receives replication, updates its local MySQL
- ATM3 receives replication, updates its local MySQL
- Happens in parallel on each peer

**Timing Factors**:
- **MySQL UPDATE**: 50-100ms per peer
- **Parallel execution**: Both peers update simultaneously

**Calculation**:
```
Best case:  50ms (parallel)
Typical:    75ms (parallel)
Worst case: 150ms (parallel)
```

---

## 🌐 Network Topology Impact

### Local Network (All on same LAN):
- **Latency**: 1-10ms
- **Bandwidth**: 100Mbps - 1Gbps
- **Total operation time**: **250-600ms**

### University Network (Your setup):
- **Latency**: 10-50ms (within campus)
- **Bandwidth**: 100Mbps
- **Total operation time**: **300-900ms**

### Internet (Different locations):
- **Latency**: 50-200ms
- **Bandwidth**: Variable
- **Total operation time**: **500-2000ms** (0.5-2 seconds)

---

## 🚀 Performance Optimization

### Current Implementation:
- ✅ Parallel replication (not sequential)
- ✅ Asynchronous peer updates
- ✅ Efficient message format

### Potential Improvements:
1. **Async Replication** (Future):
   - Don't wait for peer confirmation
   - Reduces time by 50-100ms
   - Trade-off: Eventual consistency

2. **Batch Updates** (Future):
   - Group multiple operations
   - Reduces overhead
   - Better for high-volume scenarios

3. **Local Caching** (Future):
   - Cache balance checks
   - Reduces MySQL queries
   - Faster read operations

---

## 📈 Scalability Analysis

### With 3 Nodes (Current):
- **Lock acquisition**: 100-500ms (wait for 2 peers)
- **Replication**: 50-200ms (broadcast to 2 peers)
- **Total**: 250-900ms

### With 5 Nodes (Hypothetical):
- **Lock acquisition**: 150-700ms (wait for 4 peers)
- **Replication**: 100-400ms (broadcast to 4 peers)
- **Total**: 350-1200ms

### With 10 Nodes (Hypothetical):
- **Lock acquisition**: 200-1000ms (wait for 9 peers)
- **Replication**: 200-800ms (broadcast to 9 peers)
- **Total**: 500-2000ms

**Conclusion**: System scales linearly with number of nodes.

---

## 🎯 Real-World Expectations

### User Experience:

| Operation | User Sees | Acceptable? |
|-----------|-----------|-------------|
| **Login** | 200-500ms | ✅ Instant |
| **Balance Check** | 100-300ms | ✅ Instant |
| **Deposit** | 500-900ms | ✅ Fast |
| **Withdrawal** | 500-900ms | ✅ Fast |
| **Transfer** | 700-1200ms | ✅ Acceptable |

**Comparison to Real Banks**:
- ATM withdrawal: 3-10 seconds (includes card reading, PIN, cash dispensing)
- Online banking: 1-3 seconds
- **Your system**: 0.5-1 second ✅ **Competitive!**

---

## ⚡ Timeout Settings

### Current Configuration:

**In RicartNode.java**:
```java
long timeout = 10000; // 10 seconds timeout
```

**Why 10 seconds?**
- Allows for slow networks
- Handles temporary node failures
- Prevents indefinite waiting

**Recommended Settings**:
- **LAN**: 2-5 seconds
- **Campus Network**: 5-10 seconds (current)
- **Internet**: 15-30 seconds

---

## 🔍 Monitoring Performance

### How to Measure:

Add timing code to ATMNode.java:
```java
long startTime = System.currentTimeMillis();

// ... perform operation ...

long endTime = System.currentTimeMillis();
System.out.println("Operation took: " + (endTime - startTime) + "ms");
```

### What to Monitor:
1. **Lock acquisition time**: Should be < 500ms
2. **Database operation time**: Should be < 100ms
3. **Replication time**: Should be < 200ms
4. **Total operation time**: Should be < 1000ms

---

## 📊 Performance Summary

### Typical Transaction Timeline:

```
0ms     ─────► Start transaction
        │
100ms   ├────► Distributed lock acquired
        │      (waited for 2 peer replies)
        │
150ms   ├────► Local database updated
        │      (MySQL UPDATE completed)
        │
200ms   ├────► Replication broadcast sent
        │      (messages sent to 2 peers)
        │
350ms   ├────► Peers updated their databases
        │      (parallel MySQL UPDATEs)
        │
400ms   ─────► Transaction complete ✅
        │
        └────► User sees result
```

**Total Time: ~400-500ms** (less than half a second!)

---

## 🎯 Key Takeaways

1. **Most operations complete in 0.5-1 second**
2. **Distributed locking is the slowest part** (100-500ms)
3. **Replication is fast** (50-200ms) due to parallel execution
4. **Performance is acceptable for banking application**
5. **System scales linearly with number of nodes**

---

## 💡 Recommendations

### For Your Setup (Campus Network):
- ✅ Current performance is good (500-900ms)
- ✅ No optimization needed for 3 nodes
- ✅ User experience will be smooth

### If Performance Issues:
1. Check network latency: `ping 10.18.51.25`
2. Check MySQL performance: Enable slow query log
3. Reduce timeout if all nodes are reliable
4. Consider async replication for non-critical operations

---

**Bottom Line**: Your distributed system will perform **transactions in about 0.5-1 second**, which is excellent for a distributed banking application! 🚀
