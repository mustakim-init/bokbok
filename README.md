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
│         UI Layer (Compose)                                  │
│  - Screens                                                  │
│  - Components                                               │
│  - Theme System                                             │
└───────────────┬─────────────────────┘
                          │
┌───────────────▼─────────────────────┐
│      Presentation Layer                                     │
│  - ViewModels                                               │
│  - UI State                                                 │
│  - Event Handling                                           │
└───────────────┬─────────────────────┘
                          │
┌───────────────▼─────────────────────┐
│         Data Layer                                          │
│  - Repositories                                             │
│  - Firebase Integration                                     │
│  - Room Database                                            │
│  - WebRTC Manager                                           │
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

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/mustakim-init/bokbok-v2.git
   cd bokbok-v2
   ```

2. **Set up Firebase**
   - Create a new Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
   - Enable **Authentication** (Email/Password, Google Sign-In)
   - Enable **Cloud Firestore**
   - Enable **Cloud Storage**
   - Download `google-services.json`
   - Place it in `app/` directory

3. **Configure Firestore Security Rules**
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       // Users collection
       match /users/{userId} {
         allow read: if request.auth != null;
         allow write: if request.auth.uid == userId;
       }
       
       // Rooms collection
       match /rooms/{roomId} {
         allow read: if request.auth != null;
         allow write: if request.auth != null;
       }
       
       // Messages collection
       match /messages/{messageId} {
         allow read, write: if request.auth != null;
       }
     }
   }
   ```

4. **Open in Android Studio**
   ```bash
   # Open the project in Android Studio
   # Wait for Gradle sync to complete
   ```

5. **Run the app**
   - Connect an Android device or start an emulator
   - Click **Run** (▶️) or press `Shift + F10`

### First Launch

1. **Sign up** with email or Google account
2. **Set up your profile** (username, display name, avatar)
3. **Grant permissions** (microphone, notifications)
4. **Explore the lounge** to discover public rooms
5. **Create your first room** or join an existing one

---

## 📁 Project Structure

```
app/src/main/java/com/mustakim/bokbok/
│
├── data/
│   ├── model/              # Data models (User, Room, Message, etc.)
│   ├── repository/         # Room Repository
│   ├── webrtc/             # Communication handler
│   └── local/              # Room database
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
4. **Message History** - Loads last 100 messages per chat
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
- 🌐 **Website:** N/A

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
A: Yes! You can use your own Firebase project for backend services.

---

## 📞 Support

Need help? Have questions?

- 📖 **Documentation** - Check the [Wiki](https://github.com/mustakim-init/bokbok-v2/wiki) (coming soon)
- 🐛 **Bug Reports** - [Open an Issue](https://github.com/mustakim-init/bokbok-v2/issues)
- 💬 **Discussions** - [GitHub Discussions](https://github.com/mustakim-init/bokbok-v2/discussions)
- 📧 **Email** - Contact via GitHub profile

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
