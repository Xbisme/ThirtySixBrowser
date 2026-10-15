# Contract: the browser page store

Who may write what in `CurrentPageRepository`:

| Writer | May write |
|---|---|
| `PageViewModel` | `url` (from the page), `title`, `activeTab`, `isIncognito`, `startNewPage()` |
| `AddressBarViewModel` | `url = Home URL` and `title = ""` on Back-without-history only |
| others | nothing; they read `page` |

Rules:

- A write is synchronous; the next read from any holder sees it.
- No holder keeps its own copy of these four values; it derives from `page`.
- The store holds no persisted data; tab persistence stays `UpdateActiveTabUrlAndTitleUseCase`.
