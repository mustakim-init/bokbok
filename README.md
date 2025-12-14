# 🐔 BokBok v2

> **A next-generation social voice chat platform for Android**  
> Connect with friends through real-time voice rooms, private chats, and seamless social interactions.

<div align="center">

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Compose-1.5+-blue.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Latest-green.svg)](https://m3.material.io/)
[![Firebase](https://img.shields.io/badge/Firebase-Latest-orange.svg)](https://firebase.google.com/)
[![License: Dual](https://img.shields.io/badge/License-Dual%20(Free%2FCommercial)-blue.svg)](LICENSE)

> **📋 Licensing Notice:** BokBok v2 is dual-licensed. Free for personal/non-commercial use, commercial license required for business use. [See licensing details](#-license)

</div>

---

## 📱 What is BokBok?

BokBok is a modern social voice chat application that brings people together through **voice rooms** - virtual spaces where friends can hang out, chat, and connect in real-time. Think of it as your personal lounge where you can:

- 🎙️ **Join voice rooms** with friends or the public
- 💬 **Chat in real-time** with hybrid text messaging
- 👥 **See who's online** and available to talk
- 🎨 **Customize your experience** with themes and profiles
- 🔐 **Create private or public rooms** with flexible controls

### Why BokBok?

Unlike traditional social apps, BokBok focuses on **effortless voice communication** without the pressure of video calls. It's perfect for:

- **Casual hangouts** - Drop in and out of conversations naturally
- **Study groups** - Work together while staying connected
- **Gaming sessions** - Coordinate with friends while playing
- **Remote teams** - Lightweight communication for distributed groups
- **Just vibing** - Background company while doing your own thing

---

## ✨ Key Features

### 🎙️ Voice Rooms

<table>
<tr>
<td width="50%">

**Public Rooms**
- Discover active rooms by category
- Join call-only or become a permanent member
- Real-time participant indicators
- Auto-refresh room listings

</td>
<td width="50%">

**My Rooms**
- Create custom voice rooms
- Set room capacity (up to 50+ participants)
- Add room descriptions and cover images
- Manage permanent members

</td>
</tr>
</table>

**Room Categories:**
- 🎮 Gaming
- 📚 Study
- 🎵 Music
- 💼 Work
- 🎭 Entertainment
- 💭 Casual

### 💬 Hybrid Chat System

- **Text + Voice** - Seamlessly switch between chat and voice
- **Rich messaging** - Swipe-to-reply, reactions, message deletion
- **Group chats** - Create channels for different topics
- **Summon system** - Mention friends with `\summon @username`
- **Real-time sync** - See messages appear instantly
- **Offline support** - Messages queue when disconnected

### 👥 Friend System

- **Online status indicators** - See who's available
- **Smart friend cards** - Shows current activity (in room, online, idle)
- **Quick join** - Tap to join friends in voice rooms
- **Profile customization** - Display names, avatars, status

### 🎨 Beautiful UI/UX

- **Material Design 3** - Modern, adaptive interface
- **Custom shapes** - Unique scallop, clover, and squircle designs
- **Smooth animations** - Spring-based physics animations
- **Dark/Light themes** - Full theme support with dynamic colors
- **Skeleton loaders** - Elegant loading states
- **Parallax carousel** - Stunning room card presentations

### 🔧 Advanced Controls

**Voice Room Controls:**
- 🎤 Mute/unmute toggle
- 🔊 Individual participant volume control
- 📢 Speaking indicators with visual feedback
- 🎵 Audio quality modes (32kbps/64kbps)
- 🎧 Bluetooth audio mode switching (A2DP/SCO)
- 👥 Participant management (kick, volume adjust)

**Room Settings:**
- 🔐 Public/private visibility
- 🔔 Join notifications toggle
- 👑 Host controls and member management
- 🖼️ Cover image customization
- 📝 Description editing
- 👥 Participant limit settings

---

## 🏗️ Technical Architecture

### Built With Modern Android Stack

```
Jetpack Compose          → Declarative UI framework
Kotlin Coroutines        → Asynchronous programming
Firebase Firestore       → Real-time database
Firebase Realtime DB     → Presence and signaling
Firebase Auth            → User authentication
Room Database            → Local data persistence
WebRTC                   → Peer-to-peer voice communication
Coil                     → Image loading and caching
Material Design 3        → Design system
Navigation Compose       → Screen navigation
ViewModel + StateFlow    → State management
```

### Architecture Pattern

```
┌─────────────────────────────────────┐
│         UI Layer (Compose)          │
│  - Screens                          │
│  - Components                       │
│  - Theme System                     │
└───────────────┬─────────────────────┘
                │
┌───────────────▼─────────────────────┐
│      Presentation Layer             │
│  - ViewModels                       │
│  - UI State                         │
│  - Event Handling                   │
└───────────────┬─────────────────────┘
                │
┌───────────────▼─────────────────────┐
│         Data Layer                  │
│  - Repositories                     │
│  - Firebase Integration             │
│  - Room Database                    │
│  - WebRTC Manager                   │
└─────────────────────────────────────┘
```

### Key Components

#### Custom UI Components

- **ScallopShape** - Flower-like shape using Catmull-Rom splines
- **VoiceControlsSheet** - Expandable bottom sheet with smooth morphing animation
- **RoundedParallaxCarousel** - 3D parallax effect for room cards
- **FriendsStatusSection** - Dynamic friend cards with activity indicators
- **MinimizedRoomBar** - Floating room control bar when minimized
- **SkeletonLoader** - Shimmer loading effect for all major screens

#### Performance Optimizations

- **Path caching** in custom shapes to prevent redraw overhead
- **derivedStateOf** for expensive list transformations
- **Stable keys** in LazyColumn for smooth scrolling
- **Lazy initialization** of ViewModels and repositories
- **Background threading** for database and network operations

---

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

## 📁 Project Structure

```
app/src/main/java/com/mustakim/bokbok/
│
├── data/
│   ├── model/              # Data models (User, Room, Message, etc.)
│   ├── repository/         # Data repositories
│   ├── webrtc/             # WebRTC communication handler
│   ├── local/              # Room database and DAOs
│   ├── api/                # API services (ImgBB, FCM)
│   └── service/            # Background services
│
├── ui/
│   ├── screens/            # Full-screen composables
│   │   ├── auth/           # Login, signup, permissions
│   │   ├── lounge/         # Main lobby screen
│   │   ├── room/           # Voice room screen
│   │   ├── chat/           # Chat screens
│   │   └── settings/       # Settings and profile
│   │
│   ├── components/         # Reusable UI components
│   │   ├── CustomShapes.kt         # Shape library
│   │   ├── VoiceControlsSheet.kt   # Room controls
│   │   ├── FriendsStatusSection.kt # Friend cards
│   │   ├── ParticipantCard.kt      # Room participant UI
│   │   └── ...
│   │
│   └── theme/              # Material 3 theme system
│
├── viewmodel/              # ViewModels for state management
│   ├── AuthViewModel.kt
│   ├── LoungeViewModel.kt
│   ├── RoomViewModel.kt
│   └── ...
│
├── utils/
│   └── ...
├── MainActivity.kt
└── BokBokApp.kt            # Application class
```

---

## 🎯 Usage Examples

### Joining a Room

```kotlin
// Tap = Join call only (temporary)
onJoinCallOnly = { room ->
    viewModel.joinRoom(room.id, permanent = false)
}

// Long press = Join permanently (become member)
onJoinPermanently = { room ->
    viewModel.joinRoom(room.id, permanent = true)
}
```

### Sending a Summon Message

```kotlin
// Type in chat: \summon @username
// The SummonAutocomplete component will show suggestions
// Selecting a user completes the command

// In your message handler:
if (message.contains("\\summon @")) {
    val mentionedUser = extractMentionedUser(message)
    sendNotificationToUser(mentionedUser, "You've been summoned!")
}
```

---

## 🔧 Configuration

### Build Variants

```gradle
buildTypes {
    debug {
        applicationIdSuffix ".debug"
        debuggable true
        // Uses Firebase debug config
    }
    
    release {
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        // Uses Firebase production config
    }
}
```

### Firebase Configuration

```kotlin
// In BokBokApp.kt
class BokBokApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase (after first frame for performance)
        CoroutineScope(Dispatchers.Default).launch {
            initializeFirebase()
        }
    }
    
    private fun initializeFirebase() {
        // Firestore settings
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(CACHE_SIZE_UNLIMITED)
                    .build()
            )
            .build()
        
        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}
```

---

## 🎨 Theming

BokBok supports full Material 3 dynamic theming:

```kotlin
@Composable
fun BokBokTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

---

## 🐛 Known Issues & Limitations

### Current Limitations

1. **Room Capacity** - Maximum 50 participants per room (WebRTC mesh limitation)
2. **Audio Quality** - Toggleable between 32kbps and 64kbps
3. **Offline Mode** - Limited functionality when disconnected
4. **Message History** - Loads last 50 messages per chat
5. **Image Upload** - Cover images only (no in-chat images yet)

### Performance Notes

⚠️ **Launch Performance** - Currently being optimized. Expected improvements in next release:
- Cold start: 2-3s → 300-500ms
- First frame: 1-2s → 150-200ms
- See [Performance Roadmap](#roadmap) for details

### Known Bugs

- [ ] Skeleton loader may flicker on slow connections
- [ ] Voice controls sheet animation stutters on low-end devices
- [ ] Friend status may delay update by 1-2 seconds
- [ ] Theme switch can cause momentary jank

---

## 🗺️ Roadmap

### v2.1 (Performance Release) 🔥
- [x] Optimize app launch time (Critical)
- [x] Fix UI recomposition issues
- [ ] Add comprehensive error handling
- [ ] Implement offline mode improvements
- [ ] Add unit test coverage (>50%)

### v2.2 (Features)
- [ ] In-chat image sharing
- [ ] Voice message recording
- [ ] Room templates for quick setup
- [ ] Enhanced search and discovery
- [ ] User blocking and reporting

### v2.3 (Scale)
- [ ] SFU (Selective Forwarding Unit) for large rooms (50+ participants)
- [ ] Message pagination and infinite scroll
- [ ] Advanced room analytics
- [ ] Push notification improvements
- [ ] Background voice mode (stay in room while using other apps)

### v3.0 (Major)
- [ ] Video support (optional)
- [ ] Screen sharing
- [ ] Room recording
- [ ] Advanced moderation tools
- [ ] API for third-party integrations

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

### Ways to Contribute

- 🐛 **Report bugs** - Open an issue with detailed reproduction steps
- 💡 **Suggest features** - Share your ideas for improvements
- 📝 **Improve docs** - Help make documentation clearer
- 🔧 **Submit PRs** - Fix bugs or implement features

### Development Guidelines

1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Follow Kotlin conventions**
   - Use meaningful variable names
   - Add KDoc comments for public APIs
   - Follow Material Design guidelines
4. **Test your changes**
   - Manual testing on device
   - Add unit tests where applicable
5. **Commit with clear messages**
   ```bash
   git commit -m "Add: Amazing new feature"
   ```
6. **Push and create PR**
   ```bash
   git push origin feature/amazing-feature
   ```

### Code Style

- **Kotlin** - Follow [Official Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)
- **Compose** - Follow [Compose API Guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md)
- **Formatting** - Use Android Studio default formatter
- **Naming** - Clear, descriptive names over brevity

---

## 📄 License

BokBok v2 is available under a **Dual License**:

### 🆓 Non-Commercial License (Free)

✅ **Free to use for:**
- Personal projects and learning
- Open source projects
- Academic research and education
- Non-profit organizations
- Evaluation and testing

### 💼 Commercial License (Paid)

💰 **Requires a commercial license for:**
- Apps that generate revenue (subscriptions, ads, in-app purchases)
- Internal business tools and enterprise applications
- White-label solutions or reselling
- Integration into commercial products or services

**Commercial License Pricing:**
- 🚀 **Startup License** (< $100K annual revenue): $499/year
- 🏢 **Business License** (< $1M annual revenue): $1,999/year
- 🏭 **Enterprise License** (> $1M annual revenue): Contact for pricing

### 📧 Get a Commercial License

Interested in using BokBok commercially? Contact us:
- 📧 **Email:** mioact2smart@gmail.com
- 💬 **GitHub:** Open an issue with "Commercial License" in the title

### 📋 Full License Terms

See the [LICENSE](LICENSE) file for complete terms and conditions.

---

**Why Dual License?**

We believe in open source and want developers to learn from and improve BokBok. The free license ensures students, hobbyists, and open source projects can use it freely. The commercial license helps fund continued development and support while being affordable for businesses that benefit from the software.

---

## 👤 Author

**Mustakim**

- GitHub: [@mustakim-init](https://github.com/mustakim-init)
- Project: [bokbok-v2](https://github.com/mustakim-init/bokbok-v2)

---

## 🙏 Acknowledgments

### Built With Amazing Open Source Projects

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI
- [Firebase](https://firebase.google.com/) - Backend services
- [WebRTC](https://webrtc.org/) - Real-time communication
- [Coil](https://coil-kt.github.io/coil/) - Image loading
- [Material Design](https://m3.material.io/) - Design system

### Inspiration

- [Clubhouse](https://www.clubhouse.com/) - Social audio concept
- [Discord](https://discord.com/) - Voice channel paradigm
- [Telegram](https://telegram.org/) - Clean messaging UX

---

## ❓ Frequently Asked Questions

### Licensing

**Q: Can I use BokBok for my personal app?**  
A: Yes! Personal and non-commercial use is completely free. You can use it for learning, hobby projects, and open source work.

**Q: I'm a student working on a project. Do I need to pay?**  
A: No, students and academic projects are covered under the free non-commercial license.

**Q: My startup is making less than $100K. Do I need a license?**  
A: Yes, any revenue-generating use requires a commercial license. However, our Startup License is only $499/year and designed to be affordable for early-stage companies.

**Q: What happens if I use it commercially without a license?**  
A: Unauthorized commercial use violates the license terms and may result in legal action. We prefer to work with developers, so please reach out if you have concerns about licensing.

**Q: Can I try it before purchasing a commercial license?**  
A: Absolutely! You can evaluate BokBok for free. Once you decide to use it commercially, you'll need to purchase a license.

**Q: Do I own the apps I build with BokBok?**  
A: Yes! You own your apps. The license covers your right to use BokBok's code, but your applications remain yours.

**Q: Can I modify the code?**  
A: Yes for non-commercial use. For commercial use, you can modify the code but must purchase a commercial license.

**Q: Is the license perpetual or subscription-based?**  
A: Commercial licenses are annual subscriptions. This ensures you receive ongoing updates and support.

### Technical

**Q: What's the minimum Android version?**  
A: Android 8.0 (API 26) and above.

**Q: Does it work offline?**  
A: Limited functionality. You can view cached data, but voice rooms and real-time chat require internet.

**Q: How many people can join a room?**  
A: Currently up to 50 participants per room (WebRTC mesh limitation). We're working on SFU support for larger rooms.

**Q: Can I use my own Firebase project?**  
A: Yes! You can (and should) use your own Firebase project for backend services.

**Q: Do I need a TURN server?**  
A: Not required for most connections. STUN servers (included for free) work for ~80% of cases. TURN servers improve connectivity in restrictive networks (corporate firewalls, symmetric NATs).

**Q: What's ImgBB and why do I need it?**  
A: ImgBB is an image hosting service. It's used for uploading profile pictures and room cover images. The free tier is sufficient for development and small-scale production use.

---

## 📞 Support

Need help? Have questions?

- 📖 **Documentation** - Check this README (comprehensive guide)
- 🐛 **Bug Reports** - [Open an Issue](https://github.com/mustakim-init/bokbok-v2/issues)
- 💬 **Discussions** - [GitHub Discussions](https://github.com/mustakim-init/bokbok-v2/discussions)
- 📧 **Email** - mioact2smart@gmail.com

---

## ⭐ Star History

If you find BokBok useful, please consider giving it a star! ⭐

It helps the project gain visibility and encourages continued development.

---

<div align="center">

**Made with ❤️ and ☕ by Mustakim**

*Building the future of social voice communication*

[⬆ Back to Top](#-bokbok-v2)

</div>
