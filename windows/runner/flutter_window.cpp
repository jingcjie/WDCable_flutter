#include "flutter_window.h"

// WinRT First
#include "flutter/generated_plugin_registrant.h"
#include "network_service.h"
#include "utils.h"

// Standard Libs
#include <flutter/method_channel.h>
#include <flutter/standard_method_codec.h>
#include <shobjidl.h>
#include <windows.h>

#include <chrono>
#include <filesystem>
#include <optional>
#include <variant>
#include <vector>

FlutterWindow::FlutterWindow(const flutter::DartProject& project)
    : project_(project) {}

FlutterWindow::~FlutterWindow() {}

bool FlutterWindow::OnCreate() {
    if (!Win32Window::OnCreate()) {
        return false;
    }

    const wchar_t* kProxyWndClassName = L"FlutterProxyWindow";
    WNDCLASS proxy_wnd_class = {};
    proxy_wnd_class.lpfnWndProc = FlutterWindow::ProxyWndProc;
    proxy_wnd_class.lpszClassName = kProxyWndClassName;
    RegisterClass(&proxy_wnd_class);
    proxy_window_handle_ = CreateWindow(
        kProxyWndClassName, L"Flutter Proxy", 0, 0, 0, 0, 0,
        HWND_MESSAGE, nullptr, nullptr, GetModuleHandle(nullptr));

    if (!proxy_window_handle_) {
        return false;
    }
    SetWindowLongPtr(proxy_window_handle_, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(this));

    wifi_direct_manager_ = std::make_unique<WifiDirectManager>(
        [this](const auto& devices) { this->OnPeerListUpdated(devices); },
        [this](auto request) -> winrt::fire_and_forget {
            this->OnConnectionRequested(request);
            co_return;
        },
        [this](const auto& info) { this->OnConnectionEstablished(info); },
        [this]() { this->OnConnectionLost(); });

    RECT frame = GetClientArea();
    flutter_controller_ = std::make_unique<flutter::FlutterViewController>(
        frame.right - frame.left, frame.bottom - frame.top, project_);

    if (!flutter_controller_->engine() || !flutter_controller_->view()) {
        return false;
    }

    RegisterPlugins(flutter_controller_->engine());

    auto channel = std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
        flutter_controller_->engine()->messenger(),
        "wifi_direct_cable",
        &flutter::StandardMethodCodec::GetInstance());

    channel->SetMethodCallHandler(
        [this](const flutter::MethodCall<flutter::EncodableValue>& call,
               std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result) {
            HandleMethodCall(call, std::move(result));
        });

    SetChildContent(flutter_controller_->view()->GetNativeWindow());

    flutter_controller_->engine()->SetNextFrameCallback([&]() {
        this->Show();
    });

    flutter_controller_->ForceRedraw();
    return true;
}

void FlutterWindow::HandleMethodCall(
    const flutter::MethodCall<flutter::EncodableValue>& call,
    std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result) {
    const std::string& method = call.method_name();

    if (method == "startAdvertising") {
        wifi_direct_manager_->StartAdvertising();
        result->Success(flutter::EncodableValue("Advertising Started"));
    } else if (method == "stopAdvertising") {
        wifi_direct_manager_->StopAdvertising();
        result->Success(flutter::EncodableValue("Advertising Stopped"));
    } else if (method == "discoverPeers") {
        wifi_direct_manager_->StartDiscovery();
        result->Success(flutter::EncodableValue("Discovery Started"));
    } else if (method == "stopDiscovery") {
        wifi_direct_manager_->StopDiscovery();
        result->Success(flutter::EncodableValue("Discovery Stopped"));
    } else if (method == "connectToPeer") {
        const auto* args = std::get_if<flutter::EncodableMap>(call.arguments());
        if (args) {
            auto it = args->find(flutter::EncodableValue("deviceAddress"));
            if (it != args->end() && std::holds_alternative<std::string>(it->second)) {
                std::string device_id = std::get<std::string>(it->second);
                wifi_direct_manager_->ConnectToDeviceAsync(device_id);
                result->Success(flutter::EncodableValue("Connection initiated"));
                return;
            }
        }
        result->Error("INVALID_ARGUMENT", "deviceAddress is missing or invalid");
    } else if (method == "disconnect") {
        wifi_direct_manager_->Disconnect();
        result->Success(flutter::EncodableValue("Disconnect initiated"));
    } else if (method == "isWifiP2pEnabled") {
        result->Success(flutter::EncodableValue(true));
    } else if (method == "getDeviceSettings") {
        flutter::EncodableMap settings;
        settings[flutter::EncodableValue("deviceName")] = flutter::EncodableValue("Windows PC");
        settings[flutter::EncodableValue("wifiDirectSupported")] = flutter::EncodableValue(true);
        settings[flutter::EncodableValue("wifiP2pEnabled")] = flutter::EncodableValue(true);
        settings[flutter::EncodableValue("isGroupOwner")] = flutter::EncodableValue(false);
        auto now = std::chrono::system_clock::now();
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
        settings[flutter::EncodableValue("timestamp")] = flutter::EncodableValue((int64_t)ms);
        result->Success(flutter::EncodableValue(settings));
    } else if (method == "sendData") {
        const auto* args = std::get_if<flutter::EncodableMap>(call.arguments());
        if (args) {
            auto it = args->find(flutter::EncodableValue("data"));
            if (it != args->end() && std::holds_alternative<std::string>(it->second)) {
                std::string data = std::get<std::string>(it->second);
                if (network_service_) {
                    network_service_->SendChatData(data);
                    result->Success(flutter::EncodableValue("Message sent"));
                    return;
                }
            }
        }
        result->Error("SEND_FAILED", "Network service not ready");
    }
    // -- SPEED TEST METHODS --
    else if (method == "sendSpeedTestData") {
        int64_t size = 1048576;  // Default
        const auto* args = std::get_if<flutter::EncodableMap>(call.arguments());
        if (args) {
            auto it = args->find(flutter::EncodableValue("sizeBytes"));
            if (it != args->end()) {
                if (std::holds_alternative<int>(it->second))
                    size = std::get<int>(it->second);
                else if (std::holds_alternative<int64_t>(it->second))
                    size = std::get<int64_t>(it->second);
            }
        }

        if (network_service_) {
            network_service_->SendSpeedTestData(size);
            result->Success(flutter::EncodableValue("Speed test upload started"));
        } else {
            result->Error("NO_CONNECTION", "Service not ready");
        }
    } else if (method == "requestSpeedTestData") {
        int64_t size = 1048576;
        const auto* args = std::get_if<flutter::EncodableMap>(call.arguments());
        if (args) {
            auto it = args->find(flutter::EncodableValue("sizeBytes"));
            if (it != args->end()) {
                if (std::holds_alternative<int>(it->second))
                    size = std::get<int>(it->second);
                else if (std::holds_alternative<int64_t>(it->second))
                    size = std::get<int64_t>(it->second);
            }
        }

        if (network_service_) {
            network_service_->RequestSpeedTestData(size);
            result->Success(flutter::EncodableValue("Speed test download requested"));
        } else {
            result->Error("NO_CONNECTION", "Service not ready");
        }
    } else if (method == "setSpeedTesting") {
        result->Success();
    }
    // -- FILE TRANSFER METHODS --
    else if (method == "sendFileStream") {
        const auto* args = std::get_if<flutter::EncodableMap>(call.arguments());
        if (args) {
            auto it = args->find(flutter::EncodableValue("filePath"));
            if (it != args->end() && std::holds_alternative<std::string>(it->second)) {
                std::string path = std::get<std::string>(it->second);
                if (network_service_) {
                    network_service_->SendFile(path);
                    result->Success(flutter::EncodableValue("File send initiated"));
                    return;
                }
            }
        }
        result->Error("ERROR", "Invalid file path or no connection");
    } else if (method == "pickFile") {
        // Native Windows File Picker Implementation
        IFileOpenDialog* pFileOpen;

        // Create the FileOpenDialog object.
        HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, NULL, CLSCTX_ALL,
                                      IID_IFileOpenDialog, reinterpret_cast<void**>(&pFileOpen));

        if (SUCCEEDED(hr)) {
            // Show the Open dialog box.
            hr = pFileOpen->Show(GetHandle());

            // Get the file name from the dialog box.
            if (SUCCEEDED(hr)) {
                IShellItem* pItem;
                hr = pFileOpen->GetResult(&pItem);
                if (SUCCEEDED(hr)) {
                    PWSTR pszFilePath;
                    hr = pItem->GetDisplayName(SIGDN_FILESYSPATH, &pszFilePath);

                    // Display the file name to the user.
                    if (SUCCEEDED(hr)) {
                        // Convert path to UTF-8
                        std::string path = Utf8FromUtf16(pszFilePath);

                        // Extract filename using filesystem
                        std::filesystem::path fsPath(pszFilePath);
                        std::string name = Utf8FromUtf16(fsPath.filename().c_str());

                        flutter::EncodableMap args;
                        args[flutter::EncodableValue("path")] = flutter::EncodableValue(path);
                        args[flutter::EncodableValue("name")] = flutter::EncodableValue(name);

                        result->Success(flutter::EncodableValue(args));

                        CoTaskMemFree(pszFilePath);
                    }
                    pItem->Release();
                }
            } else {
                // User cancelled the dialog
                result->Success();  // Return null
            }
            pFileOpen->Release();
        } else {
            result->Error("DIALOG_ERROR", "Failed to create file open dialog");
        }
    } else if (method == "openFile") {
        const auto* args = std::get_if<flutter::EncodableMap>(call.arguments());
        if (args) {
            auto it = args->find(flutter::EncodableValue("filePath"));
            if (it != args->end() && std::holds_alternative<std::string>(it->second)) {
                std::string path = std::get<std::string>(it->second);
                ShellExecuteA(nullptr, "open", path.c_str(), nullptr, nullptr, SW_SHOW);
                result->Success(flutter::EncodableValue("File opened"));
                return;
            }
        }
        result->Error("ERROR", "Could not open file");
    } else if (method.find("Preference") != std::string::npos) {
        result->Success();
    } else {
        result->NotImplemented();
    }
}

void FlutterWindow::OnPeerListUpdated(const std::vector<winrt::Windows::Devices::Enumeration::DeviceInformation>& devices) {
    auto* peerList = new flutter::EncodableList();
    for (const auto& device : devices) {
        flutter::EncodableMap peerMap;
        peerMap[flutter::EncodableValue("deviceName")] = flutter::EncodableValue(winrt::to_string(device.Name()));
        peerMap[flutter::EncodableValue("deviceAddress")] = flutter::EncodableValue(winrt::to_string(device.Id()));
        peerMap[flutter::EncodableValue("status")] = flutter::EncodableValue(3);
        peerList->push_back(peerMap);
    }

    if (proxy_window_handle_) {
        PostMessage(proxy_window_handle_, WM_USER + 1, reinterpret_cast<WPARAM>(peerList), 0);
    } else {
        delete peerList;
    }
}

winrt::fire_and_forget FlutterWindow::OnConnectionRequested(winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionRequest request) {
    auto device_name = winrt::to_string(request.DeviceInformation().Name());
    SendEventToDart("onDebug", flutter::EncodableValue("Native: Accepting connection from " + device_name));
    wifi_direct_manager_->ConnectToDeviceAsync(winrt::to_string(request.DeviceInformation().Id()));
    co_return;
}

void FlutterWindow::OnConnectionEstablished(const ConnectionInfo& info) {
    SendEventToDart("onDebug", flutter::EncodableValue("Native: Connection Established. IP: " + info.localIp));

    flutter::EncodableMap connectionData;
    connectionData[flutter::EncodableValue("isConnected")] = flutter::EncodableValue(true);
    connectionData[flutter::EncodableValue("isGroupOwner")] = flutter::EncodableValue(info.isGroupOwner);
    connectionData[flutter::EncodableValue("groupOwnerAddress")] = info.isGroupOwner ? flutter::EncodableValue(info.localIp) : flutter::EncodableValue(info.remoteIp);
    SendEventToDart("onConnectionChanged", connectionData);

    // Initialize Network Service
    network_service_ = std::make_unique<NetworkService>([this](auto cs, auto sts, auto fts) {
        this->OnSocketsReady(cs, sts, fts);
    });

    // Chat Callback
    network_service_->SetMessageReceivedCallback([this](std::string data) {
        this->SendEventToDart("onDataReceived", flutter::EncodableValue(data));
    });

    // Speed Test Callback
    network_service_->SetSpeedTestCallbacks(
        [this](std::string type, int64_t cur, int64_t tot, double speed) {
            flutter::EncodableMap args;
            args[flutter::EncodableValue("bytesReceived")] = flutter::EncodableValue(cur);
            args[flutter::EncodableValue("bytesSent")] = flutter::EncodableValue(cur);
            args[flutter::EncodableValue("totalBytes")] = flutter::EncodableValue(tot);
            args[flutter::EncodableValue("speedMbps")] = flutter::EncodableValue(speed);
            double progress = (tot > 0) ? (double)cur / tot : 0.0;
            args[flutter::EncodableValue("progress")] = flutter::EncodableValue(progress);

            std::string event = (type == "send") ? "onSpeedTestSendProgress" : "onSpeedTestReceiveProgress";
            this->SendEventToDart(event, flutter::EncodableValue(args));
        },
        [this](std::string json) {
            try {
                std::string s = json;
                int64_t bytes = std::stoll(s.substr(s.find("bytesReceived\":") + 15));
                int dur = std::stoi(s.substr(s.find("durationMs\":") + 12));
                double spd = std::stod(s.substr(s.find("speedMbps\":") + 11));

                flutter::EncodableMap args;
                args[flutter::EncodableValue("bytesReceived")] = flutter::EncodableValue(bytes);
                args[flutter::EncodableValue("durationMs")] = flutter::EncodableValue(dur);
                args[flutter::EncodableValue("speedMbps")] = flutter::EncodableValue(spd);
                this->SendEventToDart("onSpeedTestDataReceived", flutter::EncodableValue(args));
            } catch (...) {
            }
        });

    // File Transfer Callback
    network_service_->SetFileCallbacks(
        [this](std::string name, int64_t cur, int64_t tot, double speed) {
            flutter::EncodableMap args;
            args[flutter::EncodableValue("fileName")] = flutter::EncodableValue(name);
            double progress = (tot > 0) ? (double)cur / tot : 0.0;
            args[flutter::EncodableValue("progress")] = flutter::EncodableValue(progress);
            this->SendEventToDart("onFileReceiveProgress", flutter::EncodableValue(args));
            this->SendEventToDart("onFileSendProgress", flutter::EncodableValue(args));
        },
        [this](std::string name, std::string path) {
            flutter::EncodableMap args;
            args[flutter::EncodableValue("fileName")] = flutter::EncodableValue(name);
            args[flutter::EncodableValue("filePath")] = flutter::EncodableValue(path);
            this->SendEventToDart("onFileReceived", flutter::EncodableValue(args));
        });

    if (info.isGroupOwner) {
        network_service_->StartAsServer(info.localIp);
    } else {
        network_service_->StartAsClient(info.remoteIp);
    }
}

void FlutterWindow::OnConnectionLost() {
    SendEventToDart("onDebug", flutter::EncodableValue("Native: Connection Lost"));
    flutter::EncodableMap connectionData;
    connectionData[flutter::EncodableValue("isConnected")] = flutter::EncodableValue(false);
    connectionData[flutter::EncodableValue("isGroupOwner")] = flutter::EncodableValue(false);
    connectionData[flutter::EncodableValue("groupOwnerAddress")] = flutter::EncodableValue("");
    SendEventToDart("onConnectionChanged", connectionData);

    if (network_service_) {
        network_service_->Stop();
        network_service_ = nullptr;
    }
}

void FlutterWindow::OnSocketsReady(winrt::Windows::Networking::Sockets::StreamSocket chat,
                                   winrt::Windows::Networking::Sockets::StreamSocket speed,
                                   winrt::Windows::Networking::Sockets::StreamSocket file) {
    SendEventToDart("onDebug", flutter::EncodableValue("Native: All Sockets Ready"));
}

void FlutterWindow::SendEventToDart(std::string eventName, flutter::EncodableValue args) {
    if (proxy_window_handle_) {
        auto* eventData = new std::pair<std::string, flutter::EncodableValue>(eventName, args);
        PostMessage(proxy_window_handle_, WM_USER + 2, reinterpret_cast<WPARAM>(eventData), 0);
    }
}

void FlutterWindow::OnDestroy() {
    if (flutter_controller_) flutter_controller_ = nullptr;
    if (proxy_window_handle_) {
        DestroyWindow(proxy_window_handle_);
        proxy_window_handle_ = nullptr;
    }
    Win32Window::OnDestroy();
}

LRESULT FlutterWindow::MessageHandler(HWND hwnd, UINT const message, WPARAM const wparam, LPARAM const lparam) noexcept {
    if (hwnd == proxy_window_handle_) {
        switch (message) {
            case WM_USER + 1: {
                auto* list = reinterpret_cast<flutter::EncodableList*>(wparam);
                auto channel = std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
                    flutter_controller_->engine()->messenger(), "wifi_direct_cable", &flutter::StandardMethodCodec::GetInstance());
                channel->InvokeMethod("onPeersChanged", std::make_unique<flutter::EncodableValue>(*list));
                delete list;
                return 0;
            }
            case WM_USER + 2: {
                auto* data = reinterpret_cast<std::pair<std::string, flutter::EncodableValue>*>(wparam);
                auto channel = std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
                    flutter_controller_->engine()->messenger(), "wifi_direct_cable", &flutter::StandardMethodCodec::GetInstance());
                channel->InvokeMethod(data->first, std::make_unique<flutter::EncodableValue>(data->second));
                delete data;
                return 0;
            }
        }
    }
    if (flutter_controller_) {
        std::optional<LRESULT> result = flutter_controller_->HandleTopLevelWindowProc(hwnd, message, wparam, lparam);
        if (result) return *result;
    }
    return Win32Window::MessageHandler(hwnd, message, wparam, lparam);
}

LRESULT CALLBACK FlutterWindow::ProxyWndProc(HWND const window, UINT const message, WPARAM const wparam, LPARAM const lparam) noexcept {
    if (auto* that = reinterpret_cast<FlutterWindow*>(GetWindowLongPtr(window, GWLP_USERDATA))) {
        return that->MessageHandler(window, message, wparam, lparam);
    }
    return DefWindowProc(window, message, wparam, lparam);
}