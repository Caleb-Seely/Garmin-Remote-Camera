# ClearShot - Garmin ConnectIQ Camera Control App

## Overview

ClearShot is an Android application that provides remote camera control via a connected Garmin device. The app allows users to take photos, record videos, toggle camera settings, and receive status updates on their Garmin watch.

## Architecture

The application follows the MVVM (Model-View-ViewModel) architecture pattern to ensure separation of concerns, testability, and maintainability:

### Components

- **View Layer** (Activities and UI components)
  - `DeviceActivity`: Handles user interface interactions and observes ViewModel state
  - `MainActivity`: Manages device discovery and connection
  - UI components: Camera preview, controls, status indicators

- **ViewModel Layer** (Business Logic)
  - `DeviceViewModel`: Manages camera operations, ConnectIQ communications, and UI state
  - Exposes state via LiveData for the UI to observe

- **Model Layer** (Data and Services)
  - `MyCameraManager`: Handles camera operations (photo/video capture, camera switching)
  - `ConnectIQManager`: Manages communication with Garmin devices
  - `CameraState`: Encapsulates and manages camera configuration state

### Key Classes

- **Activities**
  - `MainActivity`: Entry point, displays available Garmin devices
  - `DeviceActivity`: Main camera interface that connects to the selected device
  - `HelpActivity`: Displays usage information

- **Managers**
  - `ConnectIQManager`: Handles Garmin device communication
  - `MyCameraManager`: Manages camera operations

- **UI Components**
  - `StatusMessages`: Constants for consistent UI messaging
  - `UIConstants`: Centralized UI-related constants

## Code Structure

The project follows a package structure organized by feature and layer:

```
com.garmin.android.apps.connectiq.sample.comm
├── activities        # UI layer, contains all activities
├── adapter           # RecyclerView adapters
├── camera            # Camera handling classes
├── connectiq         # ConnectIQ communication classes
├── ui                # UI constants and helper classes 
├── viewmodel         # ViewModels containing business logic
└── MessageFactory.kt # Message formatting utilities
```

## Key Features

- Remote photo capture with configurable countdown timer
- Video recording with timer display
- Camera flip (front/back)
- Flash control
- Real-time status updates
- Automatic connection to available Garmin devices

## Maintainability Guidelines

### Adding New Features

1. For new UI features:
   - Add necessary UI elements to appropriate layouts
   - Update relevant ViewModel classes with new state and logic
   - Connect UI to ViewModel through LiveData observers

2. For new camera features:
   - Extend `MyCameraManager` with new functionality
   - Update `CameraState` to include new state properties
   - Expose functionality through the ViewModel

3. For new Garmin device interactions:
   - Add message parsing logic to `ConnectIQManager`
   - Update command handling in the ViewModel

### Best Practices

1. **LiveData Usage**:
   - Use LiveData for all UI state that needs to survive configuration changes
   - Always update LiveData from the main thread or use `postValue()` from background threads

2. **Error Handling**:
   - Log all exceptions with meaningful context information
   - Provide user-friendly error messages via the UI
   - Implement graceful degradation when features are unavailable

3. **Resource Management**:
   - Ensure camera resources are properly released in lifecycle events
   - Unregister from ConnectIQ events when views are paused or destroyed
   - Avoid memory leaks by canceling background operations on lifecycle events

4. **Code Structure**:
   - Keep methods focused on a single responsibility
   - Extract complex logic into appropriately named helper methods
   - Follow the established naming conventions throughout the codebase

## Dependencies

- AndroidX Lifecycle components for MVVM architecture
- CameraX for modern camera functionality
- Garmin ConnectIQ SDK for device communication
- Kotlin Coroutines for asynchronous operations

## Building and Testing

1. Open the project in Android Studio
2. Ensure you have the latest Garmin ConnectIQ Companion SDK
3. Build and run the application on a device with camera capabilities
4. Connect to a compatible Garmin device for full functionality testing

## Future Improvements

- Migrate to a dependency injection framework (Hilt/Dagger)
- Add unit tests for business logic
- Implement instrumentation tests for UI flows
- Add support for more camera features (HDR, night mode, etc.)
- Enhance error handling and recovery strategies 