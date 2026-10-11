# Contract C1 — What ThirtySix declares to the system

The app's public interface to other apps is its manifest. After Spec 020, `MainActivity`
declares exactly the following (research R1, R2). Nothing else may be added without a spec.

```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTask"
    android:configChanges="locale|layoutDirection|screenLayout"
    android:exported="true"
    android:theme="@style/Theme.ThirtySix.Splash">

    <!-- Launcher (unchanged) -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- FR-001: marks the app as a browser -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.APP_BROWSER" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>

    <!-- FR-001 / FR-002: web links only — http and https, any host -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="http" />
        <data android:scheme="https" />
    </intent-filter>

    <!-- FR-009: Share sheet -->
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>

    <!-- FR-011: text-selection menu -->
    <intent-filter android:label="@string/entry_process_text_label">
        <action android:name="android.intent.action.PROCESS_TEXT" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>

    <!-- FR-012: system web search -->
    <intent-filter>
        <action android:name="android.intent.action.WEB_SEARCH" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

## Rules

- **C1-1**: The `VIEW` filter's `<data>` elements name **only** `http` and `https` schemes,
  with no host, path or MIME restriction (FR-002). A test parses the merged manifest and fails
  on any other scheme.
- **C1-2**: `launchMode="singleTask"` — one instance (FR-004, research R2).
- **C1-3**: No new `<uses-permission>` (FR-028). No `<queries>` element is needed: the only
  package query (`resolveActivity` for the default browser) runs on API 24–28, before package
  visibility rules exist.
- **C1-4**: The `PROCESS_TEXT` label is a string resource present in all 8 locales (FR-029).

## Intent handling contract (`MainActivity`)

| Callback | Handles the intent when | Then |
|---|---|---|
| `onCreate` | `savedInstanceState == null` **and** no `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` | `IncomingIntentReader.read(intent)?.let(relay::submit)` |
| `onNewIntent` | always | `setIntent(intent)`, then as above |

`IncomingIntentReader.read` is total: any missing extra, wrong type or unknown action returns
`null`; it never throws.
