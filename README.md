# ClearShot - Garmin Watch Camera Control

ClearShot is a dual-device application that enables you to control your Android phone's camera using a Garmin smartwatch.

## Project Components

### 1. ClearShot_Watch (Garmin App)
The Garmin watch application serves as a remote control for your phone's camera. It provides a simple interface to trigger photos and videos from your wrist.

[Sideload the watch app here](/ClearShot_Watch/bin/ClearShot_Watch.prg)

### 2. ClearShot_App (Android App)
The Android companion app handles the camera operations on your phone. Due to Android's security restrictions on third-party camera access, this companion app is necessary to control the native camera functionality.

[Sideload the Android app here](/ClearShot_App/release/app-release.apk)


## Installation

### Garmin Watch App (ClearShot_Watch)
1. Connect your watch to your computer
2. Drag the ClearShot_Watch app into the App folder 
3. The app will appear in your watch's app list

### Android App (Comm Android)
1. Sideload the apk file 
2. Grant necessary permissions (camera, storage) when prompted

## Usage

1. Launch the ClearShot Android app on your phone
2. Open the ClearShot_Watch app on your Garmin watch
3. The watch will connect to the phone automatically
4. Use the watch interface to:
   - Take photos
   - Start/stop video recording
   - Adjust delay
   - Switch modes 

## Features

- Remote camera control from your wrist
- Photo capture
- Video recording
- Simple and intuitive interface

## Technical Details

The communication between the watch and phone is handled through Garmin's messaging system. The watch sends commands to the phone, which then executes the corresponding camera actions.

## Troubleshooting

If you experience connection issues:
1. Ensure both devices are within range
2. Restart both applications
3. Check if the necessary permissions are granted on the Android app
4. IDK

