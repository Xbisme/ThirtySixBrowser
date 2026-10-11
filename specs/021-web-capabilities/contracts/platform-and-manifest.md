# Contract C3 — Platform hooks and manifest

## C3.1 WebView client hooks (all answered on the main thread, exactly once — INV-3)

| Hook | Behaviour |
|---|---|
| `WebChromeClient.onPermissionRequest` | → `PageRequest.Permission` via C1.1 (silent grant/deny when decided) |
| `onPermissionRequestCanceled` | drop the matching request |
| `onGeolocationPermissionsShowPrompt` / `…HidePrompt` | → location request; `invoke(origin, allow, false)` |
| `onShowFileChooser` | → `PageRequest.FileChooser`; returns `true` |
| `onShowCustomView` / `onHideCustomView` | → `FullscreenController` |
| `onCreateWindow` | → C1.3; returns `true` only when a WebView was handed through the transport |
| `onCloseWindow` | adopted pop-up → close tab, activate opener |
| `onJsAlert` / `onJsConfirm` / `onJsPrompt` / `onJsBeforeUnload` | → `PageRequest.JsDialog` / `LeavePage`; return `true` |
| `WebViewClient.onReceivedHttpAuthRequest` | → `PageRequest.HttpAuth` |
| `WebView.setOnLongClickListener` | `hitTestResult` → C1.4; `false` when no menu |
| settings | `setSupportMultipleWindows(true)`, `javaScriptCanOpenWindowsAutomatically = true`, `setGeolocationEnabled(true)` (Spec 007's lockdown otherwise unchanged) |

## C3.2 Repository changes

```kotlin
interface SitePermissionRepository {
    fun observeAll(): Flow<List<SitePermissionDecision>>
    suspend fun decisionsFor(origin: String): Map<SiteCapability, SiteDecision>
    suspend fun save(origin: String, capability: SiteCapability, decision: SiteDecision): Result<Unit>
    suspend fun removeSite(origin: String): Result<Unit>
    suspend fun clearAll(): Result<Unit>
}

// TabRepository and IncognitoTabRepository — new member with a default body (research R9):
suspend fun createBackgroundTab(url: String): Result<Tab> = createTab(url)
```

## C3.3 Manifest (research R10)

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.microphone" android:required="false" />
<uses-feature android:name="android.hardware.location" android:required="false" />

<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
</provider>
```

`MainActivity android:configChanges` becomes
`locale|layoutDirection|screenLayout|orientation|screenSize|smallestScreenSize|keyboardHidden`
(research R4). A manifest test asserts the four permissions, the three `required="false"`
features, the provider's non-exported state, and that no other dangerous permission exists.

## C3.4 Constitution table rows (FR-037)

| Permission | Purpose | Spec |
|---|---|---|
| `CAMERA` | A site the person allowed uses the camera (calls, scanning); photo/video capture for file upload | 021 |
| `RECORD_AUDIO` | A site the person allowed uses the microphone | 021 |
| `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | A site the person allowed uses location; the person may grant approximate only | 021 |

Each requested only after the person chose an allow option for a site (FR-002), never at launch.
