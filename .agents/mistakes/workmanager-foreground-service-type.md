# WorkManager Foreground Service Type Crash on Android 15 (API 36+)

## Error
```
java.lang.IllegalArgumentException: foregroundServiceType 0x00000001 is not a subset of foregroundServiceType attribute 0x00000000 in service element of manifest file
    at androidx.work.impl.foreground.SystemForegroundService$Api31Impl.startForeground(SystemForegroundService.java:193)
```

## Cause
WorkManager's `SystemForegroundService` (declared in its library manifest) has no `android:foregroundServiceType` attribute. On API 36+, Android enforces that the foreground service type used at runtime must be a subset of the declared type in the manifest.

## Fix
Add this `<service>` entry in `app/src/main/AndroidManifest.xml` to merge with the library's declaration:

```xml
<service
    android:name="androidx.work.impl.foreground.SystemForegroundService"
    android:foregroundServiceType="dataSync"
    tools:node="merge" />
```

The `tools:node="merge"` ensures the app's declaration merges with (rather than replaces) the library's existing attributes.

## Key Points
- `0x00000001` = `dataSync` foreground service type
- The app targets `targetSdkVersion = 36`
- This crash happens on first launch when WorkManager tries to move a work spec to foreground
- `andorid.permission.FOREGROUND_SERVICE_DATA_SYNC` is already declared in the manifest
