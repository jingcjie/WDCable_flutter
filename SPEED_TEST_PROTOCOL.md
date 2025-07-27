# Speed Test Protocol Documentation

This document describes the speed test protocol implementation used in the WiFi Direct Cable Android application and provides guidance for implementing a compatible C# client.

## Overview

The speed test functionality uses a dedicated TCP connection on **port 8889** (SPEED_TEST_PORT) to perform network throughput measurements between connected devices.

## Protocol Architecture

### Connection Setup
- **Port**: 8889 (SPEED_TEST_PORT)
- **Protocol**: TCP
- **Connection Type**: Persistent connection for multiple speed tests
- **Buffer Size**: Configurable (default: 8192 bytes)
- **Timeout**: Configurable (default: 30000ms)
- **Keep Alive**: Enabled by default

### Message Format

All protocol messages are text-based headers followed by binary data when applicable:

```
<COMMAND>:<PARAMETER>\n
<binary_data_if_applicable>
```

## Speed Test Protocol Commands

### 1. Speed Test Request
**Format**: `SPEED_TEST_REQUEST:<size_in_bytes>\n`

**Purpose**: Request the peer to send speed test data of specified size

**Example**: `SPEED_TEST_REQUEST:1048576\n` (requests 1MB of test data)

**Flow**:
1. One side sends request
2. Other side will respond with `SPEED_TEST_DATA` message

### 2. Speed Test Data Transmission
**Format**: `SPEED_TEST_DATA:<size_in_bytes>\n<binary_data>`

**Purpose**: Send speed test data to measure throughput

**Data Pattern**: 
- Chunk size: 65536 bytes (64KB)
- Pattern: Sequential bytes (0-255 repeating): `(byte)(i % 256)`
- Memory management: Data sent in chunks to prevent memory overflow

**Flow**:
1. Send header with total size
2. Send binary data in 64KB chunks
3. Add 1ms delay every 1MB to prevent overwhelming

### 3. Binary Stream
**Format**: `BINARY_STREAM:<size_in_bytes>\n<binary_data>`

**Purpose**: Send arbitrary binary data for testing


## Testing Scenarios

### 1. Download Speed Test
1. C# client sends: `SPEED_TEST_REQUEST:1048576\n`
2. Android responds with: `SPEED_TEST_DATA:1048576\n<1MB_data>`
3. C# client measures receive speed

### 2. Upload Speed Test
1. C# client sends: `SPEED_TEST_DATA:1048576\n<1MB_data>`
2. Android receives and measures speed
3. Android reports results via callback

### 3. Bidirectional Speed Test

## Error Handling

- **Connection Errors**: Handle TCP connection failures gracefully
- **Timeout Handling**: Implement proper timeouts for all operations
- **Data Validation**: Verify received data size matches expected size
- **Memory Management**: Use streaming approach to handle large data transfers

## Performance Considerations

- **Chunk Size**: 64KB chunks provide good balance between memory usage and performance
- **Buffer Size**: 8KB buffer for reading operations
- **Flow Control**: 1ms delay every 1MB prevents overwhelming slower devices
- **Memory Usage**: Stream data instead of loading entire payload into memory

## Validation Checklist

- [ ] TCP connection established on port 8889
- [ ] Protocol headers correctly formatted with `\n` terminator
- [ ] Binary data follows immediately after header
- [ ] Data pattern matches: `(byte)(i % 256)`
- [ ] Speed calculation: `(bytes * 8) / (milliseconds / 1000) = Mbps`
- [ ] Proper error handling for connection issues
- [ ] Memory-efficient streaming implementation
- [ ] Timeout handling for all operations