# Contract: `TabSessionRepository` (core/domain/repository)

```kotlin
interface TabSessionRepository {
    /** Increments on every clearAll(), so a live WebView can drop its back and forward. */
    val clears: StateFlow<Long>
    suspend fun get(tabId: Long): TabSession?
    suspend fun save(session: TabSession)
    suspend fun delete(tabId: Long)
    suspend fun retainOnly(tabIds: Set<Long>)
    suspend fun clearAll()
}
```

| Rule | Requirement |
|---|---|
| `save` with `tabId < 0` keeps the session in memory only; it never reaches the DAO | FR-006 |
| `save` with a state larger than the bound is rejected (no write) | FR-003 |
| `get` of a missing or unreadable session returns `null`, never throws | FR-005 |
| `retainOnly` removes every session, normal and incognito, whose id is not given | FR-007 |
| `clearAll` removes all sessions, then increments `clears` | FR-008, FR-009 |
| Every call runs on the injected IO dispatcher | constitution III |

**Writers**:
- `TabSessionViewModel` calls `save`, `get` and `retainOnly`.
- `ClearBrowsingHistoryUseCase` calls `clearAll`.
- The cascade on `tabs` removes normal rows.

Nothing else writes.

**Implementation**: `feature/tabs/data/repository/TabSessionRepositoryImpl`, bound in `feature/tabs/di`.
