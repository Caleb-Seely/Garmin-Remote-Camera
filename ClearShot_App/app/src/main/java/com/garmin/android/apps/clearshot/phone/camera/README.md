# Camera Module Architecture

This document outlines the architecture of the camera module after refactoring. The original monolithic `MyCameraManager` class has been refactored into smaller, more manageable components following the Single Responsibility Principle.

## Overview

The camera module now follows a component-based architecture with clear separation of concerns:

```
CameraManager (Coordinator)
├── CameraState (Shared State)
├── CameraInitializer (Camera Setup)
├── PhotoCaptureManager (Photo Capture)
├── VideoCaptureManager (Video Recording)
├── CountdownManager (Countdown Logic)
└── CameraConfigManager (Configuration)
```

## Components

### CameraState

A central state repository that maintains the current state of the camera:
- Camera mode (photo/video)
- Recording status
- Camera facing direction (front/back)
- Flash settings

### CameraManager

The main coordinator class that clients interact with. It delegates operations to specialized components and manages their lifecycle.

**Responsibilities:**
- Initialize and coordinate component managers
- Expose a simplified public API
- Handle component lifecycle

### CameraInitializer

Handles camera setup and initialization.

**Responsibilities:**
- Camera provider initialization
- Use case binding (Preview, ImageCapture, VideoCapture)
- Error recovery for camera initialization
- Camera lifecycle management

### PhotoCaptureManager

Handles photo capture operations.

**Responsibilities:**
- Taking photos with the camera
- Saving photos to the device storage
- Handling capture errors and callbacks

### VideoCaptureManager

Handles video recording operations.

**Responsibilities:**
- Starting video recordings
- Stopping recordings
- Tracking recording duration
- Saving recordings to device storage

### CountdownManager

Manages countdown functionality for both photo and video captures.

**Responsibilities:**
- Countdown timing and display
- Cancellation handling
- Flash notifications during countdown
- Triggering capture on completion

### CameraConfigManager

Manages camera configuration options.

**Responsibilities:**
- Switching between front and back cameras
- Flash/torch control
- Mode switching (photo/video)

### CameraLogger

Utility class for consistent logging across camera components.

**Responsibilities:**
- Centralized logging with component-specific tags
- Debug instance tracking

## Migration Guide

To migrate from the old `MyCameraManager` to the new architecture:

1. Replace `MyCameraManager` instances with `CameraManager`
2. The public API remains mostly the same, with some minor improvements
3. The old `MyCameraManager` has been renamed to `MyCameraManagerLegacy` and deprecated

## Error Handling

Each component handles errors specific to its responsibility:
- Error logging is consistent using the `CameraLogger`
- Components attempt recovery from errors when possible
- Errors are reported to the UI using Toast messages
- Each component cleans up its resources during shutdown

## Lifecycle Management

Camera components respect the Android lifecycle:
- Camera is properly initialized and released with the activity lifecycle
- Resources are cleaned up when components are no longer needed
- Each component has a `shutdown()` method for cleanup

## Threading

- The camera uses the main thread for UI updates
- Background operations use coroutines with appropriate dispatchers
- Callbacks from CameraX are handled on the main thread 