# SportivTV - Architecture Documentation

## 1. System Overview

```
┌─────────────────────────────────────────────────┐
│                  SportivTV App                   │
├─────────────┬─────────────┬─────────────────────┤
│  Phone UI   │   TV UI     │   Player Activity    │
│ MainActivity│ TvMainActiv.│  (shared, fullscrn)  │
├─────────────┴─────────────┼─────────────────────┤
│         UI Layer          │   Player Layer       │
│  - Channel Grid           │  - ExoPlayer (Media3)│
│  - Category Tabs          │  - PlayerFactory     │
│  - Search                 │  - Channel Switching │
│  - Favorites              │  - Reconnect Logic   │
│  - Subscriptions          │  - Watch State       │
├───────────────────────────┼─────────────────────┤
│      Data Layer           │   Audio Layer        │
│  - M3UParser              │  - ExternalAudioMgr  │
│  - EpgParser              │  - AudioTrack        │
│  - ChannelRepository      │  - Manual Sync       │
│  - ChannelSearch          │  - (Experimental)    │
│  - FavoritesManager       │                      │
├───────────────────────────┼─────────────────────┤
│     Subscription Layer    │   Backend Layer      │
│  - SubscriptionManager    │  - BackendManager    │
│  - SecureCredentialStorage│  - DeviceInfo        │
│  - OkHttp Client          │  - Settings Sync     │
└───────────────────────────┴─────────────────────┘
```

## 2. Video Engine

**Engine**: ExoPlayer/Media3 v1.2.1

**Rationale**: ExoPlayer is the industry standard for Android video playback.
It has excellent HLS support, is actively maintained by Google, and handles
IPTV streams reliably. No alternative engine is used or needed.

**Configuration**:
- Buffer: 30s (tunable), min 15s, max 60s
- HLS extractor enabled
- OkHttp data source for robust redirect/timeout handling
- Hardware decoding (default)

## 3. Audio Architecture

### Internal Audio (Default)
ExoPlayer handles audio natively. No external intervention.

### External Audio (Experimental - Phase 5)

**Problem**: Some IPTV streams have audio that is desynchronized from video.

**Solution**: Independent audio extraction pipeline:
1. Opens a separate HTTP connection to the stream
2. Parses MPEG-TS packets to find audio PES data
3. Decodes audio to PCM
4. Plays via Android `AudioTrack`
5. Video player's internal audio is MUTED

**Key Design Decisions**:
- Does NOT use a second ExoPlayer/video player (Rule compliance)
- Uses raw `AudioTrack` for PCM playback
- Failure does NOT crash the video player (graceful degradation)
- Manual sync offset: -1000ms to +1000ms (user-adjustable SeekBar)

**Known Limitations**:
- Only works with MPEG-TS streams (HLS .m3u8 and .ts)
- Does not work with encrypted streams
- Does not work with DASH (.mpd)
- Audio decode is simplified (AAC/MP3 PES extraction)
- Sync accuracy depends on stream characteristics

## 4. Channel Switching

Channel switching uses ExoPlayer's `setMediaItem()` + `prepare()`:
1. Stop external audio if active
2. Set new MediaItem with new stream URL
3. Prepare and play
4. Reset reconnect counter
5. Save watch state
6. Load EPG for new channel

**TV Navigation**: D-pad left/right = prev/next channel.
D-pad center = toggle controls. Channel up/down keys supported.

## 5. Reconnection Logic

```
On Player Error:
  → If attempts < max (5):
    → Wait: 2s * 2^(attempt-1)  [exponential backoff]
    → Retry: setMediaItem + prepare
  → Else:
    → Show error + retry button to user
```

## 6. Data Flow

```
Subscription URL
    ↓ (HTTP GET via OkHttp)
M3U Text
    ↓ (M3UParser.parse)
List<Channel>
    ↓ (ChannelRepository - cached)
UI Grid / Player
    ↓ (PlayerFactory.createMediaItem)
ExoPlayer
    ↓ (HLS/TS extraction)
Video Output
```

## 7. Security Architecture

```
Credentials (user/password)
    ↓
SecureCredentialStorage
    ↓ (EncryptedSharedPreferences)
AES-256-GCM encrypted file
    ↓
Never logged, never exposed in plain text
```

Device IDs are generated from Android Secure ID + app salt.
Diagnostics export excludes all sensitive data.

## 8. RTL / Arabic Support

- `android:supportsRtl="true"` in manifest
- `android:layoutDirection="rtl"` in themes
- Arabic locale forced in `SportivTVApp.onCreate()`
- All string resources in `values-ar/strings.xml`
- Search normalizes Arabic diacritics and letter variants
