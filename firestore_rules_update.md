# Firestore Security Rules Update

The app is crashing because of a `PERMISSION_DENIED` error when trying to read notifications. You need to update your Firestore Security Rules in the Firebase Console.

## 1. Go to Firebase Console
1. Open your project in the [Firebase Console](https://console.firebase.google.com/).
2. Navigate to **Firestore Database**.
3. Click on the **Rules** tab.

## 2. Update Rules
Add the following rule to your existing rules. This allows a user to read and write only their *own* notifications.

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // ... keep your existing rules ...

    // ✅ ADD THIS BLOCK:
    match /users/{userId}/notifications/{notificationId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Example of what your user rule might look like (keep existing):
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

## 3. Publish
Click **Publish** to apply the changes. The crash should be resolved immediately after this.
