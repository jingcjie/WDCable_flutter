#include "network_service.h"

//
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Networking.h>
#include <winrt/Windows.Storage.FileProperties.h>
#include <winrt/Windows.Storage.Streams.h>
#include <winrt/Windows.Storage.h>
#include <winrt/base.h>

//
#include <shlobj.h>
#include <windows.h>

#include <algorithm>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <string>
#include <vector>

constexpr int CHAT_PORT = 8888;
constexpr int SPEED_TEST_PORT = 8889;
constexpr int FILE_PORT = 8890;
constexpr int BUFFER_SIZE = 8192;

using namespace winrt::Windows::Networking::Sockets;
using namespace winrt::Windows::Storage;
using namespace winrt::Windows::Storage::Streams;

NetworkService::NetworkService(SocketsReadyCallback callback) : sockets_ready_callback_(callback) {}

NetworkService::~NetworkService() {
    Stop();
}

void NetworkService::Stop() {
    try {
        if (chat_socket_) chat_socket_.Close();
        if (speed_test_socket_) speed_test_socket_.Close();
        if (file_transfer_socket_) file_transfer_socket_.Close();

        if (chat_listener_) chat_listener_.Close();
        if (speed_test_listener_) speed_test_listener_.Close();
        if (file_transfer_listener_) file_transfer_listener_.Close();
    } catch (...) {
    }

    connected_sockets_count_ = 0;
}

winrt::fire_and_forget NetworkService::StartAsServer(const std::string& local_ip) {
    try {
        winrt::hstring hostName = winrt::to_hstring(local_ip);
        winrt::Windows::Networking::HostName localHost(hostName);

        // Chat Listener
        chat_listener_ = StreamSocketListener();
        chat_listener_.ConnectionReceived([this](auto, auto args) {
            this->chat_socket_ = args.Socket();
            this->CheckAllSocketsConnected();
        });
        co_await chat_listener_.BindEndpointAsync(localHost, winrt::to_hstring(CHAT_PORT));

        // Speed Test Listener
        speed_test_listener_ = StreamSocketListener();
        speed_test_listener_.ConnectionReceived([this](auto, auto args) {
            this->speed_test_socket_ = args.Socket();
            this->CheckAllSocketsConnected();
        });
        co_await speed_test_listener_.BindEndpointAsync(localHost, winrt::to_hstring(SPEED_TEST_PORT));

        // File Transfer Listener
        file_transfer_listener_ = StreamSocketListener();
        file_transfer_listener_.ConnectionReceived([this](auto, auto args) {
            this->file_transfer_socket_ = args.Socket();
            this->CheckAllSocketsConnected();
        });
        co_await file_transfer_listener_.BindEndpointAsync(localHost, winrt::to_hstring(FILE_PORT));

    } catch (winrt::hresult_error const& ex) {
        OutputDebugString((L"StartAsServer failed: " + ex.message() + L"\n").c_str());
        Stop();
    }
}

winrt::fire_and_forget NetworkService::StartAsClient(const std::string& remote_ip) {
    auto connect_port = [this, remote_ip](int port, StreamSocket& sock) -> winrt::Windows::Foundation::IAsyncAction {
        try {
            winrt::Windows::Networking::HostName remoteHostName(winrt::to_hstring(remote_ip));
            sock = StreamSocket();
            co_await sock.ConnectAsync(remoteHostName, winrt::to_hstring(port));
            this->CheckAllSocketsConnected();
        } catch (...) {
            OutputDebugString((L"Failed to connect to port " + std::to_wstring(port) + L"\n").c_str());
        }
    };

    connect_port(CHAT_PORT, chat_socket_);
    connect_port(SPEED_TEST_PORT, speed_test_socket_);
    connect_port(FILE_PORT, file_transfer_socket_);

    co_return;
}

void NetworkService::CheckAllSocketsConnected() {
    std::lock_guard<std::mutex> lock(connection_mutex_);

    connected_sockets_count_++;
    if (connected_sockets_count_ == 3) {
        OutputDebugString(L"All 3 sockets connected. Starting listeners.\n");

        StartChatListener(chat_socket_);
        StartSpeedTestListener(speed_test_socket_);
        StartFileListener(file_transfer_socket_);

        if (sockets_ready_callback_) {
            sockets_ready_callback_(chat_socket_, speed_test_socket_, file_transfer_socket_);
        }
    }
}

winrt::fire_and_forget NetworkService::SendChatData(const std::string& message) {
    if (!chat_socket_) co_return;

    try {
        DataWriter writer(chat_socket_.OutputStream());
        std::string formatted = "{\"message\":\"" + message + "\",\"timestamp\":0}\n";

        writer.WriteString(winrt::to_hstring(formatted));
        co_await writer.StoreAsync();
        co_await writer.FlushAsync();
        writer.DetachStream();
    } catch (...) {
        OutputDebugString(L"NetworkService: Failed to send chat data\n");
    }
}

winrt::fire_and_forget NetworkService::StartChatListener(StreamSocket socket) {
    auto reader = DataReader(socket.InputStream());
    reader.InputStreamOptions(InputStreamOptions::Partial);

    try {
        while (true) {
            unsigned int bytesLoaded = co_await reader.LoadAsync(1024);
            if (bytesLoaded == 0) break;

            winrt::hstring data = reader.ReadString(bytesLoaded);
            std::string message = winrt::to_string(data);

            if (message_received_callback_) {
                message_received_callback_(message);
            }
        }
    } catch (...) {
        OutputDebugString(L"Chat Listener stopped.\n");
    }
}

winrt::fire_and_forget NetworkService::SendSpeedTestData(int64_t size_bytes) {
    if (!speed_test_socket_) co_return;

    try {
        DataWriter writer(speed_test_socket_.OutputStream());

        // Send Header
        std::string header = "SPEED_TEST_DATA:" + std::to_string(size_bytes) + "\n";
        writer.WriteBytes(std::vector<uint8_t>(header.begin(), header.end()));
        co_await writer.StoreAsync();

        // Send Dummy Data
        std::vector<uint8_t> buffer(BUFFER_SIZE, 0xAB);
        int64_t sent = 0;

        auto start_time = std::chrono::high_resolution_clock::now();
        auto last_report_time = start_time;

        while (sent < size_bytes) {
            uint32_t chunk_size = (uint32_t)std::min((int64_t)BUFFER_SIZE, size_bytes - sent);

            if (chunk_size == BUFFER_SIZE) {
                writer.WriteBytes(buffer);
            } else {
                writer.WriteBytes(winrt::array_view<const uint8_t>(buffer.data(), buffer.data() + chunk_size));
            }

            co_await writer.StoreAsync();
            sent += chunk_size;

            auto now = std::chrono::high_resolution_clock::now();
            if (std::chrono::duration_cast<std::chrono::milliseconds>(now - last_report_time).count() > 100) {
                double elapsed_sec = std::chrono::duration<double>(now - start_time).count();
                if (elapsed_sec > 0 && speed_progress_callback_) {
                    double mbps = (sent * 8.0 / 1000000.0) / elapsed_sec;
                    speed_progress_callback_("send", sent, size_bytes, mbps);
                }
                last_report_time = now;
            }
        }

        co_await writer.FlushAsync();
        writer.DetachStream();

        auto end_time = std::chrono::high_resolution_clock::now();
        double total_sec = std::chrono::duration<double>(end_time - start_time).count();
        if (speed_progress_callback_) {
            double final_mbps = (size_bytes * 8.0 / 1000000.0) / total_sec;
            speed_progress_callback_("send", size_bytes, size_bytes, final_mbps);
        }

    } catch (...) {
        OutputDebugString(L"SendSpeedTestData failed.\n");
    }
}

winrt::fire_and_forget NetworkService::RequestSpeedTestData(int64_t size_bytes) {
    if (!speed_test_socket_) co_return;
    try {
        DataWriter writer(speed_test_socket_.OutputStream());
        std::string request = "SPEED_TEST_REQUEST:" + std::to_string(size_bytes) + "\n";
        writer.WriteBytes(std::vector<uint8_t>(request.begin(), request.end()));
        co_await writer.StoreAsync();
        co_await writer.FlushAsync();
        writer.DetachStream();
    } catch (...) {
    }
}

winrt::fire_and_forget NetworkService::StartSpeedTestListener(StreamSocket socket) {
    auto reader = DataReader(socket.InputStream());
    reader.InputStreamOptions(InputStreamOptions::Partial);

    try {
        while (true) {
            std::string header;
            while (true) {
                if (co_await reader.LoadAsync(1) == 0) co_return;
                uint8_t b = reader.ReadByte();
                if (b == '\n') break;
                header += (char)b;
            }

            OutputDebugString((L"SpeedTest Header: " + winrt::to_hstring(header) + L"\n").c_str());

            if (header.find("SPEED_TEST_REQUEST:") == 0) {
                std::string size_str = header.substr(19);
                int64_t size = std::stoll(size_str);
                SendSpeedTestData(size);
            } else if (header.find("SPEED_TEST_DATA:") == 0) {
                std::string size_str = header.substr(16);
                int64_t total_size = std::stoll(size_str);
                int64_t received = 0;

                auto start_time = std::chrono::high_resolution_clock::now();
                auto last_report_time = start_time;

                while (received < total_size) {
                    uint32_t to_read = (uint32_t)std::min((int64_t)BUFFER_SIZE, total_size - received);
                    unsigned int loaded = co_await reader.LoadAsync(to_read);
                    if (loaded == 0) break;

                    reader.ReadBuffer(loaded);
                    received += loaded;

                    auto now = std::chrono::high_resolution_clock::now();
                    if (std::chrono::duration_cast<std::chrono::milliseconds>(now - last_report_time).count() > 100) {
                        double elapsed_sec = std::chrono::duration<double>(now - start_time).count();
                        if (elapsed_sec > 0 && speed_progress_callback_) {
                            double mbps = (received * 8.0 / 1000000.0) / elapsed_sec;
                            speed_progress_callback_("receive", received, total_size, mbps);
                        }
                        last_report_time = now;
                    }
                }

                double total_sec = std::chrono::duration<double>(std::chrono::high_resolution_clock::now() - start_time).count();
                if (speed_data_callback_) {
                    std::string json = "{ \"bytesReceived\": " + std::to_string(received) +
                                       ", \"durationMs\": " + std::to_string((int)(total_sec * 1000)) +
                                       ", \"speedMbps\": " + std::to_string((received * 8.0 / 1e6) / total_sec) + " }";
                    speed_data_callback_(json);
                }
            }
        }
    } catch (...) {
        OutputDebugString(L"SpeedTest Listener stopped.\n");
    }
}

winrt::fire_and_forget NetworkService::SendFile(const std::string& file_path) {
    if (!file_transfer_socket_) co_return;

    try {
        StorageFile file = co_await StorageFile::GetFileFromPathAsync(winrt::to_hstring(file_path));
        auto props = co_await file.GetBasicPropertiesAsync();
        uint64_t size = props.Size();
        std::string name = winrt::to_string(file.Name());

        DataWriter writer(file_transfer_socket_.OutputStream());

        std::string header = "FILE:" + name + ":" + std::to_string(size) + "\n";
        writer.WriteBytes(std::vector<uint8_t>(header.begin(), header.end()));
        co_await writer.StoreAsync();

        auto stream = co_await file.OpenReadAsync();
        auto reader = DataReader(stream);

        uint64_t sent = 0;
        if (file_progress_callback_) file_progress_callback_("start", 0, size, 0.0);

        while (sent < size) {
            uint32_t chunk = (uint32_t)std::min((uint64_t)BUFFER_SIZE, size - sent);
            unsigned int loaded = co_await reader.LoadAsync(chunk);
            if (loaded == 0) break;

            auto buffer = reader.ReadBuffer(loaded);
            writer.WriteBuffer(buffer);
            co_await writer.StoreAsync();

            sent += loaded;

            if (file_progress_callback_) {
                file_progress_callback_(name, sent, size, 0.0);
            }
        }

        co_await writer.FlushAsync();
        writer.DetachStream();
    } catch (winrt::hresult_error const& ex) {
        OutputDebugString((L"SendFile error: " + ex.message() + L"\n").c_str());
    }
}

winrt::fire_and_forget NetworkService::StartFileListener(StreamSocket socket) {
    auto reader = DataReader(socket.InputStream());
    reader.InputStreamOptions(InputStreamOptions::Partial);

    try {
        while (true) {
            std::string header;
            while (true) {
                if (co_await reader.LoadAsync(1) == 0) co_return;
                uint8_t b = reader.ReadByte();
                if (b == '\n') break;
                header += (char)b;
            }

            if (header.find("FILE:") == 0) {
                size_t first_colon = header.find(':');
                size_t second_colon = header.find(':', first_colon + 1);

                if (first_colon == std::string::npos || second_colon == std::string::npos) continue;

                std::string name = header.substr(first_colon + 1, second_colon - (first_colon + 1));
                uint64_t size = std::stoull(header.substr(second_colon + 1));

                PWSTR downloadsPathRaw = NULL;
                if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_Downloads, 0, NULL, &downloadsPathRaw))) {
                    std::wstring wDownloadPath(downloadsPathRaw);
                    CoTaskMemFree(downloadsPathRaw);

                    std::filesystem::path downloadDir(wDownloadPath);
                    std::filesystem::path filePath = downloadDir / name;

                    int counter = 1;
                    while (std::filesystem::exists(filePath)) {
                        std::filesystem::path tempPath = downloadDir / (std::filesystem::path(name).stem().string() + "_" + std::to_string(counter) + std::filesystem::path(name).extension().string());
                        filePath = tempPath;
                        counter++;
                    }

                    std::ofstream outFile(filePath, std::ios::binary);
                    if (outFile.is_open()) {
                        uint64_t received = 0;
                        while (received < size) {
                            uint32_t chunk = (uint32_t)std::min((uint64_t)BUFFER_SIZE, size - received);
                            unsigned int loaded = co_await reader.LoadAsync(chunk);
                            if (loaded == 0) break;

                            auto ibuffer = reader.ReadBuffer(loaded);
                            uint8_t* data = ibuffer.data();

                            outFile.write(reinterpret_cast<char*>(data), loaded);

                            received += loaded;

                            if (file_progress_callback_) {
                                file_progress_callback_(name, received, size, 0.0);
                            }
                        }
                        outFile.close();

                        if (file_received_callback_) {
                            file_received_callback_(name, filePath.string());
                        }
                    }
                }
            }
        }
    } catch (...) {
        OutputDebugString(L"File Transfer Listener stopped.\n");
    }
}