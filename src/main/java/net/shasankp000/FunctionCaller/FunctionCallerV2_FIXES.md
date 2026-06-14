# Critical FunctionCallerV2 Bug Fixes

## Issues Identified and Fixed

### 1. **Placeholder Fallback to Zero (Line 5859)**
**Severity:** 🔴 CRITICAL
**Impact:** Unresolved placeholders silently become `"0"`, causing bot to use wrong coordinates

**Problem:**
```java
if (resolvedObj == null) {
    logger.warn("⚠️ Placeholder '{}' not found in sharedState. Using fallback value '0'", key);
    return "0";  // ❌ DANGEROUS!
}
```

**Fix:** Throw exception instead of silent fallback
```java
if (resolvedObj == null) {
    logger.error("❌ CRITICAL: Required placeholder '{}' could not be resolved!", key);
    throw new IllegalStateException("Required placeholder not resolved: $" + key);
}
```

---

### 2. **Unresolved Parameter Check Bug (Line 5374)**
**Severity:** 🔴 CRITICAL
**Impact:** The check for unresolved parameters never triggers because placeholders that can't be resolved become "0", not "__UNRESOLVED__"

**Problem:**
```java
boolean hasUnresolved = paramMap.values().stream()
    .anyMatch(v -> v.equals("__UNRESOLVED__"));
```

**Fix:** After throwing on unresolved placeholders above, this check becomes redundant. Or add:
```java
boolean hasUnresolved = paramMap.values().stream()
    .anyMatch(v -> v.equals("0") && 
              (v.startsWith("$") || /* check if this was a placeholder */));
```

---

### 3. **Non-Atomic SharedState Updates (Line 6156-6165)**
**Severity:** 🟠 HIGH
**Impact:** Race condition if another thread reads state between updates; bot gets inconsistent coordinates

**Problem:**
```java
SharedStateUtils.setValue(state, "found_block_x", blockPos.getX());
SharedStateUtils.setValue(state, "found_block_y", blockPos.getY());
SharedStateUtils.setValue(state, "found_block_z", blockPos.getZ());
```

**Fix:** Atomically update all coordinates:
```java
Map<String, Object> blockData = new HashMap<>();
blockData.put("found_block_x", blockPos.getX());
blockData.put("found_block_y", blockPos.getY());
blockData.put("found_block_z", blockPos.getZ());
blockData.put("found_block_type", blockType);
state.putAll(blockData);  // Atomic update in one operation
```

---

### 4. **Incorrect Mining Stand Position Logic (Line 6463)**
**Severity:** 🟠 HIGH
**Impact:** Bot tries to stand inside the block it's mining (blockX + 1 is not valid)

**Problem:**
```java
params.put("x", String.valueOf(standX != null ? standX : blockX + 1));
```

The standing position should be calculated properly, not blindly offset.

**Fix:** Use the existing `findMiningStandPos()` method:
```java
BlockPos standingPos = findMiningStandPos(botSource.getPlayer(), new BlockPos(blockX, blockY, blockZ));
if (standingPos == null) {
    standingPos = new BlockPos(blockX + 1, blockY, blockZ);  // Only use as fallback
}
params.put("x", String.valueOf(standingPos.getX()));
params.put("y", String.valueOf(standingPos.getY()));
params.put("z", String.valueOf(standingPos.getZ()));
```

---

### 5. **Excessive goTo Timeout (Line 414)**
**Severity:** 🟡 MEDIUM
**Impact:** 120 seconds is too long; if bot gets stuck, debugger waits forever

**Problem:**
```java
String result = startPreciseCoordinateMove(x, y, z, sprint).get(120, TimeUnit.SECONDS);
```

**Fix:** Reduce to reasonable timeout:
```java
String result = startPreciseCoordinateMove(x, y, z, sprint).get(30, TimeUnit.SECONDS);
```

---

## Testing Recommendations

1. **Test placeholder resolution** with missing keys to ensure exception is thrown
2. **Test coordinate storage** to verify no race conditions
3. **Test mining** to confirm bot stands at correct position
4. **Test pipeline execution** under load to catch threading issues

## Files to Update
- `src/main/java/net/shasankp000/FunctionCaller/FunctionCallerV2.java`

## Priority
- **High:** Fixes #1, #2, #3 (prevent crashes and incorrect behavior)
- **Medium:** Fix #4 (prevents mining failures)
- **Low:** Fix #5 (improves debugging experience)
