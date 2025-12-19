#pragma once

#include <winrt/Windows.Networking.Sockets.h>
#include <winrt/Windows.Storage.Streams.h>
#include <winrt/Windows.Storage.h>

#include <functional>
#include <mutex>
#include <string>
#include <vector>

class NetworkService {
   public:
    // Callback to notify when all three sockets are ready.
    using SocketsReadyCallback = std::function<void(winrt::Windows::Networking::Sockets::StreamSocket,
                                                    winrt::Windows::Networking::Sockets::StreamSocket,
                                                    winrt::Windows::Networking::Sockets::StreamSocket)>;

    using MessageReceivedCallback = std::function<void(std::string)>;
    using ProgressCallback = std::function<void(std::string type, int64_t current, int64_t total, double speed_mbps)>;
    using FileReceivedCallback = std::function<void(std::string filename, std::string path)>;

    NetworkService(SocketsReadyCallback callback);
    ~NetworkService();

    // Lifecycle
    winrt::fire_and_forget StartAsServer(const std::string& local_ip);
    winrt::fire_and_forget StartAsClient(const std::string& remote_ip);
    void Stop();

    // Chat
    winrt::fire_and_forget SendChatData(const std::string& message);
    void SetMessageReceivedCallback(MessageReceivedCallback callback) { message_received_callback_ = callback; }

    // Speed Test
    // Sends data to the other peer (Upload Test from our perspective)
    winrt::fire_and_forget SendSpeedTestData(int64_t size_bytes);
    // Requests the other peer to send data to us (Download Test from our perspective)
    winrt::fire_and_forget RequestSpeedTestData(int64_t size_bytes);
    void SetSpeedTestCallbacks(ProgressCallback progress_cb, MessageReceivedCallback data_cb) {
        speed_progress_callback_ = progress_cb;
        speed_data_callback_ = data_cb;
    }

    // File Transfer
    winrt::fire_and_forget SendFile(const std::string& file_path);
    void SetFileCallbacks(ProgressCallback progress_cb, FileReceivedCallback received_cb) {
        file_progress_callback_ = progress_cb;
        file_received_callback_ = received_cb;
    }

   private:
    void CheckAllSocketsConnected();

    SocketsReadyCallback sockets_ready_callback_;

    // Callbacks
    MessageReceivedCallback message_received_callback_;
    ProgressCallback speed_progress_callback_;
    MessageReceivedCallback speed_data_callback_;
    ProgressCallback file_progress_callback_;
    FileReceivedCallback file_received_callback_;

    // Server-side listeners
    winrt::Windows::Networking::Sockets::StreamSocketListener chat_listener_{nullptr};
    winrt::Windows::Networking::Sockets::StreamSocketListener speed_test_listener_{nullptr};
    winrt::Windows::Networking::Sockets::StreamSocketListener file_transfer_listener_{nullptr};

    // Client-side
    winrt::Windows::Networking::Sockets::StreamSocket chat_socket_{nullptr};
    winrt::Windows::Networking::Sockets::StreamSocket speed_test_socket_{nullptr};
    winrt::Windows::Networking::Sockets::StreamSocket file_transfer_socket_{nullptr};

    // Connection tracking
    int connected_sockets_count_ = 0;
    std::mutex connection_mutex_;

    // Background listeners
    winrt::fire_and_forget StartChatListener(winrt::Windows::Networking::Sockets::StreamSocket socket);
    winrt::fire_and_forget StartSpeedTestListener(winrt::Windows::Networking::Sockets::StreamSocket socket);
    winrt::fire_and_forget StartFileListener(winrt::Windows::Networking::Sockets::StreamSocket socket);
};