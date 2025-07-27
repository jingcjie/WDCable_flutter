# JSON Messaging Implementation Guide

This guide explains the JSON-based messaging protocol implemented for WiFi Direct Cable communication between Windows clients and Android devices.

## Overview

The messaging system now supports JSON encoding for chat messages, providing structured data with timestamps and better handling of multi-line messages.

## Message Format

### JSON Message Structure
```json
{
  "message": "Your message content here",
  "timestamp": 1703123456789
}
```

- `message`: The actual message content (string)
- `timestamp`: Unix timestamp in milliseconds (integer)

### Protocol Rules

1. **Encoding**: UTF-8
2. **Termination**: Each JSON message must end with `\n`
3. **Port**: Use port 8888 for chat messages
4. **Backward Compatibility**: Plain text messages are still supported

## Android Implementation

### Sending Messages (MainActivity.kt)

The `sendData` method now creates JSON objects:

```kotlin
private fun sendData(data: String): Boolean {
    return try {
        if (chatSocket?.isConnected == true) {
            val messageData = JSONObject().apply {
                put("message", data)
                put("timestamp", System.currentTimeMillis())
            }
            chatWriter?.println(messageData.toString())
            chatWriter?.flush()
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}
```

### Receiving Messages (MainActivity.kt)

The `handleChatConnection` method parses both JSON and plain text:

```kotlin
private fun handleChatConnection(socket: Socket) {
    try {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
        var line: String?
        while (socket.isConnected && reader.readLine().also { line = it } != null) {
            line?.let {
                try {
                    val jsonObject = JSONObject(it)
                    val messageData = mapOf(
                        "message" to jsonObject.getString("message"),
                        "timestamp" to jsonObject.getLong("timestamp")
                    )
                    methodChannel.invokeMethod("onDataReceived", messageData)
                } catch (e: JSONException) {
                    // Fallback to plain text for backward compatibility
                    methodChannel.invokeMethod("onDataReceived", it)
                }
            }
        }
    } catch (e: Exception) {
        // Handle connection errors
    }
}
```

## Flutter/Dart Implementation

### Event Handling (wifi_direct_service.dart)

The `DataReceivedEvent` class now supports both formats:

```dart
class DataReceivedEvent extends WiFiDirectEvent {
  final String message;
  final int? timestamp;
  
  DataReceivedEvent(this.message, {this.timestamp});
}
```

### Message Processing

```dart
case 'onDataReceived':
  if (call.arguments is Map) {
    // New JSON format with message and timestamp
    final Map<String, dynamic> messageData = Map<String, dynamic>.from(call.arguments as Map);
    _eventController.add(DataReceivedEvent(
      messageData['message'] as String,
      timestamp: messageData['timestamp'] as int?,
    ));
  } else {
    // Backward compatibility: plain string
    final String data = call.arguments as String;
    _eventController.add(DataReceivedEvent(data));
  }
  break;
```

### Controller Updates (wifi_direct_controller.dart)

The controller now handles timestamps properly:

```dart
void _addChatMessage(String content, bool isSent, [int? receivedTimestamp]) {
  final message = ChatMessage(
    content: content,
    timestamp: receivedTimestamp != null 
        ? DateTime.fromMillisecondsSinceEpoch(receivedTimestamp)
        : DateTime.now(),
    isSent: isSent,
    senderName: isSent ? 'You' : 'Peer',
  );
  final updatedMessages = List<ChatMessage>.from(_currentState.chatMessages)..add(message);
  _updateState(_currentState.copyWith(chatMessages: updatedMessages));
}
```

## Windows Client Implementation

### Python Example

See `windows_client_example.py` for a complete implementation:

```python
def send_message(self, message):
    """Send a JSON-encoded message to Android device"""
    message_data = {
        "message": message,
        "timestamp": int(time.time() * 1000)
    }
    
    json_message = json.dumps(message_data) + "\n"
    self.chat_socket.send(json_message.encode('utf-8'))
```

### C# Example

```csharp
public void SendMessage(string message)
{
    var messageData = new
    {
        message = message,
        timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
    };
    
    string jsonMessage = JsonSerializer.Serialize(messageData) + "\n";
    byte[] data = Encoding.UTF8.GetBytes(jsonMessage);
    chatSocket.Send(data);
}
```

## Multi-line Message Handling

With JSON encoding, multi-line messages are naturally supported:

```python
# Multi-line message example
multiline_message = "Line 1\nLine 2\nLine 3"
client.send_message(multiline_message)
```

The JSON format preserves newlines within the message content, eliminating the need for special encoding.

## Backward Compatibility

The implementation maintains backward compatibility:

- Plain text messages are still accepted
- Old clients can continue to work without modification
- New clients benefit from structured data and timestamps

## Testing

1. **Run the Android app** and establish a WiFi Direct connection
2. **Use the Python example**: `python windows_client_example.py`
3. **Send various message types**:
   - Simple text messages
   - Multi-line messages
   - Messages with special characters

## Troubleshooting

### Common Issues

1. **JSON Parse Errors**: Ensure messages are valid JSON and properly escaped
2. **Encoding Issues**: Always use UTF-8 encoding
3. **Missing Newlines**: Every message must end with `\n`
4. **Timestamp Format**: Use milliseconds since Unix epoch

### Debug Tips

- Check Android logs for JSON parsing errors
- Verify socket connection status
- Test with simple messages first
- Use the Python example as a reference implementation

## Benefits

1. **Structured Data**: Messages now include metadata like timestamps
2. **Multi-line Support**: Natural handling of complex message content
3. **Extensibility**: Easy to add new fields in the future
4. **Backward Compatibility**: Existing clients continue to work
5. **Better Debugging**: Structured data is easier to log and analyze