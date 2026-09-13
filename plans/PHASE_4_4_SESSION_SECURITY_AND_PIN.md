# Phase 4.4 Implementation Plan — Session Security & Optional PIN Pairing

## 1. Overview & Objective
On open, school, dormitory, or shared home Wi-Fi networks, any device running CemuPad on the local subnet could inadvertently connect to an active Cemu session, viewing GamePad video or sending spurious inputs.

**Objective**:
Implement an optional 4-digit PIN pairing handshake on the TCP control connection (port `26761`):
1. When PIN pairing is enabled in Cemu settings, Cemu displays a random 4-digit code (e.g. `4829`) on its status bar or GamePad window overlay.
2. The Android client prompts the user for the 4-digit PIN on initial connection.
3. Upon successful verification, Cemu issues an authentication token that the Android client caches in `SharedPreferences`, allowing seamless automatic reconnection thereafter without re-prompting.

---

## 2. Protocol Specification (TCP Port 26761)

| Opcode | Name | Direction | Payload | Description |
|:---|:---|:---|:---|:---|
| `0x30` | `OPCODE_AUTH_REQUEST` | Phone → Cemu | 4 bytes (ASCII PIN or 32-bit token) | Client attempts authentication |
| `0x31` | `OPCODE_AUTH_RESPONSE` | Cemu → Phone | 1 byte status + 8 bytes token | `0x00` = Success (with token), `0x01` = Denied |

- If authentication fails, Cemu closes the connection immediately.
- If PIN protection is disabled in Cemu (`require_pin = false`), Cemu automatically responds with status `0x00` (Success).

---

## 3. Files to Modify

### Cemu Backend (`Cemu/src/streaming/`)

#### [MODIFY] [`Cemu/src/streaming/CemuPadBridge.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/CemuPadBridge.h)
Expose PIN query and session security methods:
```cpp
    // PIN Pairing Security
    bool IsPinRequired() const;
    void SetRequirePin(bool required);
    uint32_t GetCurrentPin() const;
    uint32_t RegeneratePin();
```

#### [MODIFY] [`Cemu/src/streaming/VideoStreamServer.h`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/VideoStreamServer.h)
Add security opcodes:
```cpp
    static constexpr uint8 OPCODE_AUTH_REQUEST = 0x30;
    static constexpr uint8 OPCODE_AUTH_RESPONSE = 0x31;
```

#### [MODIFY] [`Cemu/src/streaming/VideoStreamServer.cpp`](file:///c:/Projects/wiiu-gamepad-android/Cemu/src/streaming/VideoStreamServer.cpp)
Handle authentication in the client connection loop:
```cpp
    case OPCODE_AUTH_REQUEST:
    {
        if (payloadSize >= 4)
        {
            bool authorized = false;
            if (!m_requirePin)
            {
                authorized = true;
            }
            else
            {
                uint32 pin = *(uint32*)payload;
                authorized = (pin == m_currentPin);
            }

            uint8 response[9];
            response[0] = authorized ? 0x00 : 0x01;
            uint64 token = authorized ? m_sessionToken : 0;
            memcpy(response + 1, &token, sizeof(token));

            send(client.socket, (const char*)response, sizeof(response), 0);
            if (!authorized)
            {
                cemuLog_log(LogType::Force, "VideoStreamServer: Client auth failed, disconnecting");
                client.active = false;
            }
        }
        break;
    }
```

---

### Android Frontend (`android-gamepad-app`)

#### [NEW] [`android-gamepad-app/app/src/main/java/com/cemupad/ui/dialogs/PinPairingDialog.kt`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/src/main/java/com/cemupad/ui/dialogs/PinPairingDialog.kt)
Jetpack Compose dialog prompting for a 4-digit PIN when Cemu requests authentication:
```kotlin
package com.cemupad.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinPairingDialog(
    onDismiss: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    var pinText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair with Cemu", color = Color(0xFFEAF2FF), fontSize = 18.sp) },
        text = {
            Column {
                Text("Enter the 4-digit PIN displayed on your PC screen:", color = Color(0xFF9FB0C6), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 4) pinText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitPin(pinText) },
                enabled = pinText.length == 4,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("Pair", color = Color(0xFF0B111B))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9FB0C6))
            }
        },
        containerColor = Color(0xFF161D2B),
        shape = RoundedCornerShape(12.dp)
    )
}
```

---

## 4. Automated Testing & Verification
1. Run unit tests:
   ```powershell
   cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
   .\gradlew.bat testDebugUnitTest
   ```
2. Build Cemu Release:
   ```powershell
   cmake --build c:\Projects\wiiu-gamepad-android\Cemu\build --config Release --target Cemu
   ```
3. Test invalid PIN: enter `0000`, verify client is disconnected with error toast.
4. Test valid PIN: enter matching PIN, verify session token is cached and stream starts immediately.
