## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK** 17 or higher
- **Android SDK** API 26+ (Android 8.0 Oreo)
- **Firebase project** for backend services
- **Physical device recommended** for voice testing
- **ImgBB API key** for image uploads (free)
- **TURN server** (optional but recommended for better connectivity)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/mustakim-init/bokbok.git
   cd bokbok
   ```

2. **Set up Firebase**

   a. **Create Firebase Project**
   - Go to [console.firebase.google.com](https://console.firebase.google.com)
   - Create a new project
   - Add an Android app with your package name: `com.mustakim.bokbok`

   b. **Enable Authentication**
   - Go to Authentication → Sign-in method
   - Enable **Email/Password**
   - Enable **Google Sign-In**
   - For Google Sign-In, add your SHA-1 fingerprint:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

   c. **Configure Cloud Firestore**
   - Go to Firestore Database → Create database
   - Start in **production mode**
   - Choose your region
   - Apply the security rules (see step 3 below)
   - **Create Composite Indexes** (required for queries):
     - Collection: `friendships`
       - Fields: `userId1` (Ascending), `status` (Ascending)
       - Fields: `userId2` (Ascending), `status` (Ascending)
     - Collection: `chats/{chatId}/messages`
       - Fields: `timestamp` (Descending)
     - Collection: `groups/{groupId}/messages`
       - Fields: `timestamp` (Descending)
     - Collection: `rooms`
       - Fields: `isPublic` (Ascending), `createdAt` (Descending)

   d. **Configure Realtime Database**
   - Go to Realtime Database → Create database
   - Start in **locked mode**
   - Apply these rules:
   ```json
   {
     "rules": {
       ".read": "auth != null",
       ".write": "auth != null"
     }
   }
   ```

   e. **Enable Cloud Storage**
   - Go to Storage → Get started
   - Start in production mode

   f. **Download Configuration**
   - Download `google-services.json`
   - Place it in `app/` directory
   - **Note**: This file is gitignored and should NOT be committed

3. **Configure Firestore Security Rules**

   In Firebase Console, go to Firestore Database → Rules and paste:

   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {

       // Users collection
       match /users/{userId} {
         allow read: if true;
         allow write: if request.auth != null && request.auth.uid == userId;
       }

       match /users/{userId}/notifications/{notificationId} {
         allow read: if request.auth != null && request.auth.uid == userId;
         allow create: if request.auth != null;
         allow update, delete: if request.auth != null && request.auth.uid == userId;
       }

       // Friendships collection
       match /friendships/{friendshipId} {
         allow read: if request.auth != null;
         allow create: if request.auth != null;
         allow update: if request.auth != null &&
           (request.auth.uid == resource.data.userId1 ||
            request.auth.uid == resource.data.userId2);
         allow delete: if request.auth != null &&
           (request.auth.uid == resource.data.userId1 ||
            request.auth.uid == resource.data.userId2);
       }

       // Rooms collection
       match /rooms/{roomId} {
         allow read: if true;
         allow create: if request.auth != null;
         allow update: if request.auth != null;
         allow delete: if request.auth != null &&
                          resource.data.hostId == request.auth.uid;

         // WebRTC signaling subcollection
         match /signals/{signalId} {
           allow read, write: if request.auth != null;
         }
       }

       // Groups collection (for Group Chats)
       match /groups/{groupId} {
         allow read: if request.auth != null && (request.auth.uid in resource.data.participants);
         allow create: if request.auth != null;
         allow update: if request.auth != null && (request.auth.uid in resource.data.participants);
         allow delete: if request.auth != null && resource.data.createdBy == request.auth.uid;

         match /messages/{messageId} {
           allow read, write: if request.auth != null &&
                              (request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.participants);
         }
       }

       // Chats collection (Messaging System)
       match /chats/{chatId} {
         allow read: if request.auth != null && (
           (resource != null && request.auth.uid in resource.data.participants) ||
           (chatId.matches('^' + request.auth.uid + '_.*') || chatId.matches('.*_' + request.auth.uid + '$'))
         );

         allow write: if request.auth != null && (
           request.auth.uid in request.resource.data.participants
         );

         // Messages subcollection
         match /messages/{messageId} {
           allow read: if request.auth != null && (
             (exists(/databases/$(database)/documents/chats/$(chatId)) &&
              request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants) ||
             (chatId.matches('^' + request.auth.uid + '_.*') || chatId.matches('.*_' + request.auth.uid + '$'))
           );

           allow create: if request.auth != null && (
              (exists(/databases/$(database)/documents/chats/$(chatId)) &&
              request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants) ||
              (chatId.matches('^' + request.auth.uid + '_.*') || chatId.matches('.*_' + request.auth.uid + '$'))
           );

           allow update, delete: if request.auth != null && (
              (exists(/databases/$(database)/documents/chats/$(chatId)) &&
              request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants)
           );
         }
       }
     }
   }
   ```

4. **Configure API Keys and Credentials**

   Create a `local.properties` file in the project root (this file is gitignored):

   ```properties
   # Android SDK location (auto-generated)
   sdk.dir=/path/to/your/Android/sdk

   # ImgBB API Key (for image uploads)
   # Get your free API key from https://api.imgbb.com/
   IMGBB_API_KEY=your_imgbb_api_key_here
   ```

   **Getting ImgBB API Key:**
   - Sign up at [ImgBB API](https://api.imgbb.com/)
   - Go to your dashboard
   - Copy your API key
   - Paste it in `local.properties`

   **Note**: Free tier allows 100 requests/hour which is sufficient for development.

5. **Configure TURN Server (WebRTC)**

   Create `app/src/main/java/com/mustakim/bokbok/data/webrtc/TurnServerManager.kt`:

   ```kotlin
   package com.mustakim.bokbok.data.webrtc

   import org.webrtc.PeerConnection
   import java.util.concurrent.atomic.AtomicInteger

   class TurnServerManager {
       private val currentTier = AtomicInteger(1)
       private val failureCount = AtomicInteger(0)
       private val successCount = AtomicInteger(0)

       fun getCurrentTier(): Int = currentTier.get()

       fun getIceServersForCurrentTier(): List<PeerConnection.IceServer> {
           val servers = mutableListOf<PeerConnection.IceServer>()

           // Tier 1: STUN only (works for most direct connections)
           servers += PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
               .createIceServer()
           servers += PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302")
               .createIceServer()

           // Tier 2: Add your primary TURN server
           if (currentTier.get() >= 2) {
               servers += PeerConnection.IceServer.builder("turn:your-turn-server.com:3478")
                   .setUsername("your-username")
                   .setPassword("your-password")
                   .createIceServer()
           }

           // Tier 3: Add fallback TURN server (optional)
           if (currentTier.get() >= 3) {
               servers += PeerConnection.IceServer.builder("turn:fallback-turn.com:3478")
                   .setUsername("fallback-username")
                   .setPassword("fallback-password")
                   .createIceServer()
           }

           return servers
       }

       fun reportConnectionSuccess() {
           successCount.incrementAndGet()
           // Downgrade to lower tier after 3 consecutive successes
           if (successCount.get() >= 3 && currentTier.get() > 1) {
               currentTier.decrementAndGet()
               failureCount.set(0)
               successCount.set(0)
           }
       }

       fun reportConnectionFailure() {
           failureCount.incrementAndGet()
           // Escalate to next tier after 2 failures
           if (failureCount.get() >= 2 && currentTier.get() < 3) {
               currentTier.incrementAndGet()
               failureCount.set(0)
               successCount.set(0)
           }
       }

       fun forceEscalateToNextTier() {
           if (currentTier.get() < 3) {
               currentTier.incrementAndGet()
               failureCount.set(0)
               successCount.set(0)
           }
       }
   }
   ```

   **Getting TURN Servers:**

   Option 1: **Free (Limited)**
   - [Metered STUN/TURN](https://www.metered.ca/tools/openrelay/)
   - Free tier: Limited usage, suitable for testing

   Option 2: **Self-Hosted (Recommended)**
   - Install [Coturn](https://github.com/coturn/coturn) on a VPS
   - Use DigitalOcean, AWS, or any VPS provider
   - Cost: ~$5-10/month

   Option 3: **Commercial**
   - [Twilio TURN](https://www.twilio.com/stun-turn) - Pay as you go
   - [Xirsys](https://xirsys.com/) - Specialized WebRTC infrastructure

   **Note**: This file is gitignored to protect your credentials. The app will work without TURN servers for most connections, but TURN servers improve connectivity in restrictive networks.

6. **Open in Android Studio**
   ```bash
   # Open Android Studio
   # File → Open → Select the project directory
   # Wait for Gradle sync to complete
   ```

7. **Build and Run**
   - Connect an Android device or start an emulator
   - Ensure USB debugging is enabled on your device
   - Click **Run** (▶️) or press `Shift + F10`
   - Select your device and wait for installation

### First Launch Setup

1. **Sign up** with email or Google account
2. **Set up your profile**:
   - Choose a unique username (lowercase, no spaces)
   - Add a display name
   - Upload a profile picture (optional)
3. **Grant permissions**:
   - Microphone access (required for voice)
   - Notifications (recommended for summons)
   - Camera (optional, for profile pictures)
4. **Explore the app**:
   - Browse public rooms in the Lounge
   - Add friends by searching usernames
   - Create your first room or join an existing one

### Troubleshooting Common Setup Issues

**Issue: Build fails with "google-services.json not found"**
- Solution: Download `google-services.json` from Firebase Console and place it in `app/` directory

**Issue: Firestore permission denied errors**
- Solution: Ensure security rules are properly configured and indexes are created

**Issue: Google Sign-In fails**
- Solution: Add your SHA-1 fingerprint in Firebase Console → Authentication → Sign-in method → Google → Configure

**Issue: Images fail to upload**
- Solution: Check that `IMGBB_API_KEY` is correctly set in `local.properties`

**Issue: Voice calls fail to connect**
- Solution: Ensure TURN server credentials are correct in `TurnServerManager.kt`, or test without TURN first (will work for most connections)

---
