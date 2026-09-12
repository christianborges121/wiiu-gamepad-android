---
name: wireless-android-screenshot-debugging
description: "Use when capturing, validating, or troubleshooting Android screenshots over wireless ADB, especially when image uploads fail with invalid PNG/image-data errors."
---

# Wireless Android Screenshot Debugging

Use this skill when an Android screenshot is needed to diagnose the CemuPad UI, video stream, colors, scaling, or decoder output.

## Scope

Workspace:

```text
C:\Projects\wiiu-gamepad-android
```

Preferred wireless ADB device serial for this project may look like:

```text
adb-R5CWC0G7CKW-WpLfEG._adb-tls-connect._tcp
```

Never assume that the historical IP:port endpoint is still active. First run:

```powershell
adb devices -l
```

Use the exact current serial listed with state `device`.

## Binary-Safe Capture

Do not capture PNG bytes with PowerShell native-output redirection. In this environment, a command such as:

```powershell
adb -s <serial> exec-out screencap -p > screenshot.png
```

can create a file that has a `.png` name but is not a valid image for the image-upload pipeline.

Use `cmd.exe` redirection instead:

```powershell
$device = '<serial from adb devices -l>'
$output = 'C:\Projects\wiiu-gamepad-android\android-screen.png'
cmd /c "adb -s $device exec-out screencap -p > $output"
```

`cmd.exe` preserves the binary stdout stream correctly for this ADB capture.

Alternative binary-safe approach, when needed:

```powershell
adb -s <serial> exec-out screencap -p | Set-Content -AsByteStream -Path screenshot.png
```

`-AsByteStream` requires a newer PowerShell. Prefer `cmd /c` for compatibility with Windows PowerShell 5.1.

## Validate Before Viewing or Uploading

Always validate the output file before passing it to an image viewer or model.

Check existence and size:

```powershell
Get-Item $output | Select-Object FullName,Length,LastWriteTime
```

Check the PNG signature using portable PowerShell:

```powershell
$bytes = [System.IO.File]::ReadAllBytes($output)
$pngSignature = [byte[]](137,80,78,71,13,10,26,10)
$signatureValid = $bytes.Length -ge 8 -and (0..7 | ForEach-Object { $bytes[$_] -eq $pngSignature[$_] } | Where-Object { -not $_ }).Count -eq 0
Write-Output "PNG signature valid: $signatureValid"
```

Validate that Windows can decode the image:

```powershell
Add-Type -AssemblyName System.Drawing
$image = [System.Drawing.Image]::FromFile($output)
Write-Output "$($image.Width)x$($image.Height)"
$image.Dispose()
```

Only use an image-viewing/upload tool after both the signature and decoder checks succeed. The image must be an actual PNG, JPEG, GIF, or WebP byte stream, not terminal text, an ADB error, or a corrupted redirected stream.

## Known Failure

The project previously produced an invalid file named `phone_resolution_fixed.png`. `System.Drawing.Image.FromFile()` failed with `Out of memory`, while the binary-safe `cmd.exe` capture decoded as a valid `2340x1080` PNG. The image-upload error was therefore caused by invalid local screenshot bytes, not by the Android display.

Do not trust the file extension or file size alone.

## Video Troubleshooting Capture

Capture the screenshot first, then collect logs from the same moment:

```powershell
$device = '<serial>'
$output = 'C:\Projects\wiiu-gamepad-android\android-screen-debug.png'
cmd /c "adb -s $device exec-out screencap -p > $output"
adb -s $device logcat -d -t 800 | Select-String -Pattern 'CemuPad|VideoStreamClient|VideoDecoder|MediaCodec|Surface|FPS|frame|Exception|FATAL|error' -CaseSensitive:$false
```

On the PC, inspect the installed Cemu log:

```powershell
Select-String `
  -Path 'C:\Users\chris\AppData\Roaming\EmuDeck\Emulators\cemu\log.txt' `
  -Pattern 'VideoStream|StreamingCapture|VideoEncoder|26761|IDR|format|error|failed' `
  -CaseSensitive:$false
```

Interpret the two logs in this order:

1. Android process exists.
2. Android connects to Cemu TCP `26761`.
3. Cemu accepts the Android client.
4. Cemu receives `IDR_REQUEST`.
5. Cemu captures DRC frames.
6. Cemu accepts the DRC pixel format.
7. Cemu encodes and sends frames.
8. Android queues and renders decoder output.

## Color and Geometry Checks

For color problems:

- Compare the Cemu desktop image with the validated Android screenshot.
- Check Cemu logs for the native Vulkan DRC format.
- Treat RGBA, BGRA, packed 10-bit formats, sRGB, and YUV range as separate concerns.
- A consistent red/blue or yellow/blue shift usually indicates channel-order or packed-format extraction, not Android UI scaling.

For geometry problems:

- Record the screenshot dimensions.
- Confirm the fixed stream is `854x480` and has a 16:9 aspect ratio.
- Confirm the Android surface is inside a 16:9 aspect-fit container.
- Distinguish cropping from stretching: cropped side text means the display surface or decoder scaling policy is wider than the source without fit behavior.

## Safety Rules

- Do not kill Cemu just to capture a screenshot.
- Do not overwrite the EmuDeck installation while Cemu is running.
- Do not report an image as valid until it passes a decoder check.
- Keep the original invalid capture for forensic comparison only; use a new validated filename for diagnosis.
- Keep logs and screenshots timestamped when comparing multiple runs.
