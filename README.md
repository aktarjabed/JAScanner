# JAScanner - Advanced Document Scanner

A production-ready Android document scanner application with advanced features including PDF/A generation, LTV digital signatures, OCR processing, terahertz scanning capabilities, and comprehensive security features.

## Features

### Core Scanning Features
- **CameraX Integration**: Advanced camera controls with burst mode, focus evaluation, and AR guide overlays
- **OCR Processing**: ML Kit-powered text recognition with AI enhancement
- **PDF/A Generation**: Strict PDF/A-2u compliance with embedded ICC profiles and fonts
- **LTV Digital Signatures**: Long-term validation signatures with Android Keystore ECDSA P-256
- **Terahertz Scanning**: Support for real hardware or demo mode with material analysis

### Security & Compliance
- **Android Keystore**: Hardware-backed key storage with biometric authentication
- **Encrypted Storage**: EncryptedSharedPreferences for sensitive data
- **PDF/A Validation**: Automatic compliance checking with iText 7
- **Biometric Authentication**: Fingerprint and face unlock support

### Advanced Features
- **Offline-First Architecture**: Room database with cloud sync capabilities
- **WorkManager Integration**: Background processing for exports and compression
- **Advanced Compression**: Multi-level image and PDF compression
- **Material Design 3**: Modern UI with dynamic theming support
- **Hilt Dependency Injection**: Clean architecture with proper separation of concerns

## Project Structure

```
JAScanner/
├── app/
│   ├── src/main/
│   │   ├── java/com/jascanner/
│   │   │   ├── di/                    # Dependency injection modules
│   │   │   ├── data/                  # Database entities and DAOs
│   │   │   ├── repository/            # Data repositories
│   │   │   ├── scanner/               # Core scanning functionality
│   │   │   │   ├── camera/           # CameraX integration
│   │   │   │   ├── ocr/              # OCR processing
│   │   │   │   ├── pdf/              # PDF generation
│   │   │   │   └── thz/              # Terahertz scanning
│   │   │   ├── security/              # Security and encryption
│   │   │   ├── presentation/          # UI components and screens
│   │   │   ├── workers/               # Background workers
│   │   │   └── utils/                 # Utility classes
│   │   └── res/                       # Resources
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## Dependencies

### Core Android
- AndroidX Core, Lifecycle, Activity Compose
- Compose BOM 2024.08.00 with Material3
- Navigation Compose with Hilt integration

### Database & Storage
- Room 2.6.1 with Kotlin extensions
- DataStore Preferences for settings
- EncryptedSharedPreferences for secure storage

### Camera & Image Processing
- CameraX 1.3.4 (Camera2, Lifecycle, View)
- ML Kit Text Recognition 16.0.1
- Coil for image loading

### PDF & Security
- iText 7.2.5 (Core, PDF/A, Sign)
- BouncyCastle 1.78.1 (Provider, PKIX)
- Bouncy Castle Adapter for iText

### Background Processing
- WorkManager 2.9.1 with Hilt integration
- Hilt WorkerFactory for dependency injection

### Networking & Sync
- Retrofit 2.11.0 with Gson converter
- OkHttp logging interceptor

### Security
- Biometric 1.2.0-alpha05
- Android Keystore integration

## Setup Instructions

### 1. Clone and Import
```bash
git clone <repository-url>
cd JAScanner
```
Open the project in Android Studio.

### 2. Required Assets
Place the following files in `app/src/main/assets/`:
- `sRGB.icc` - sRGB ICC profile for PDF/A compliance
- `fonts/arial.ttf` - Embedded font for PDF/A (required)
- `tessdata/eng.traineddata` - Tesseract data (if using Tesseract OCR)
- `models/font_detection.tflite` - TensorFlow Lite model (optional)

### 3. Build Configuration
The project is configured for:
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- Compile SDK: 34
- Java/Kotlin: JVM Target 17

### 4. Permissions
The app requests the following permissions:
- `CAMERA` - Document scanning
- `RECORD_AUDIO` - Video capture (optional)
- `INTERNET` - Cloud sync
- `ACCESS_NETWORK_STATE` - Network status
- `READ_EXTERNAL_STORAGE` - File access (API ≤ 32)
- `READ_MEDIA_IMAGES` - Media access (API ≥ 33)
- `USE_BIOMETRIC` - Biometric authentication

## Key Components

### PDF/A Generation
The `PDFAGenerator` class creates strict PDF/A-2u compliant documents:
- Embedded sRGB ICC profile as output intent
- Embedded TrueType fonts (required for compliance)
- XMP metadata with proper document information
- Validation via re-opening with PdfADocument

### LTV Digital Signatures
The `LTVSignatureManager` implements:
- Android Keystore ECDSA P-256 key generation
- RFC 3161 timestamp authority integration
- OCSP responder for certificate validation
- DSS (Document Security Store) enrichment for LTV

### Camera Controller
Advanced camera features:
- Tap-to-focus with metering points
- Burst capture with quality evaluation
- Focus evaluation using edge detection
- Flash and zoom controls

### OCR Processing
ML Kit integration with:
- AI-powered image enhancement
- Pattern extraction (emails, phones, URLs)
- Document structure analysis
- Confidence scoring

### Security Architecture
- Hardware-backed key storage
- Biometric authentication with fallback
- Encrypted preferences for sensitive data
- Hash verification for data integrity

## Usage

### Basic Document Scanning
1. Launch the app and grant camera permission
2. Point camera at document within the guide overlay
3. Tap capture button or use burst mode
4. Review OCR results and save as PDF/A

### THz Scanning (if supported)
1. Navigate to THz scanner
2. Calibrate scanner if needed
3. Start scan and wait for completion
4. Review material analysis and defect detection

### Document Management
1. View all scanned documents in the list
2. Open document details for editing
3. Export to various formats (PDF/A, text, image)
4. Add LTV signatures for long-term validation

## Build and Run

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing configuration)
./gradlew assembleRelease

# Install debug APK
./gradlew installDebug

# Run tests
./gradlew test
```

## ProGuard Configuration

The app includes ProGuard rules for:
- iText 7 and BouncyCastle libraries
- ML Kit text recognition
- CameraX components
- Room database
- Retrofit and Gson

## Architecture Notes

### Offline-First Design
- All scanning works without internet connection
- Cloud sync is optional and runs in background
- Local database is the single source of truth

### Performance Optimizations
- Image compression with multiple quality levels
- Background processing for heavy operations
- Efficient bitmap handling and memory management
- WorkManager for reliable background tasks

### Error Handling
- Comprehensive error reporting with Timber logging
- Graceful degradation for missing features
- User-friendly error messages
- Recovery mechanisms for failed operations

## Testing

The project includes:
- Unit tests for core business logic
- Instrumented tests for database operations
- UI tests for critical user flows
- Mock implementations for testing

## Contributing

1. Follow the existing code style and architecture
2. Add unit tests for new functionality
3. Update documentation for API changes
4. Test on multiple devices and Android versions

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- iText 7 for PDF/A generation and digital signatures
- Google ML Kit for OCR processing
- CameraX for modern camera integration
- Material Design 3 for UI components
- BouncyCastle for cryptographic operations