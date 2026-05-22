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

   Create or update the `local.properties` file in your project root directory (this file is gitignored and will never be committed to Git). 
   Add the following properties, replacing the placeholder values with your actual API keys and credentials:

   ```properties
   # =========================================================================
   # Android SDK Location (Auto-generated by Android Studio)
   # =========================================================================
   sdk.dir=/path/to/your/Android/sdk

   # =========================================================================
   # Image Upload Configuration (ImgBB API)
   # =========================================================================
   # Sign up for a free account at https://api.imgbb.com/ to get your API key.
   # This is required for uploading user profile avatars and custom chat room images.
   # Note: Must be in lowercase as shown below to match the Gradle build configuration!
   imgbb.api.key=your_imgbb_api_key_here

   # =========================================================================
   # AI Companion Configuration (Groq API)
   # =========================================================================
   # Get a free API key from the Groq console at https://console.groq.com/
   # This key powers the built-in real-time AI companion (Groq LLaMA models) in your social chats.
   GROQ_API_KEY=your_groq_api_key_here

   # =========================================================================
   # Custom TURN/STUN Server Configuration (Optional WebRTC Signaling)
   # =========================================================================
   # The application contains built-in high-quality STUN/TURN fallbacks, but you can
   # configure your own custom high-performance TURN servers for better connectivity here.
   # Format: turn:domain.com:port or turn:ip:port
   
   # Primary TURN Server
   TURN_URL=turn:your-primary-turn-server.com:3478
   TURN_USERNAME=your_turn_username
   TURN_PASSWORD=your_turn_password

   # Secondary Fallback TURN Server
   TURN_FALLBACK_URL=turn:your-fallback-turn-server.com:3478
   TURN_FALLBACK_USERNAME=your_fallback_username
   TURN_FALLBACK_PASSWORD=your_fallback_password
   ```

   **Getting Your API Keys:**

   * **ImgBB API Key**:
     1. Sign up/log in at [api.imgbb.com](https://api.imgbb.com/).
     2. Create an API key in your account dashboard.
     3. Copy it and insert it as `imgbb.api.key` (lowercase keys match the build configuration).
   * **Groq API Key**:
     1. Sign up/log in at [console.groq.com](https://console.groq.com/).
     2. Navigate to "API Keys" and create a new API key.
     3. Copy it and set it as `GROQ_API_KEY` (uppercase).
   * **TURN Server (Optional)**:
     * Built-in configurations cover basic development usage. However, for large deployments or highly restrictive enterprise networks, you can host your own server with **Coturn** on a VPS or purchase professional TURN/STUN services from provider platforms like **Metered.ca**, **Twilio TURN**, or **Xirsys**.

5. **Open in Android Studio**
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
