#ifndef RUNNER_FLUTTER_WINDOW_H_
#define RUNNER_FLUTTER_WINDOW_H_

#include <flutter/dart_project.h>
#include <flutter/encodable_value.h>
#include <flutter/flutter_view_controller.h>
#include <flutter/method_channel.h>

#include <memory>
#include <string>

#include "network_service.h"
#include "wifi_direct_manager.h"
#include "win32_window.h"

class FlutterWindow : public Win32Window {
   public:
    static LRESULT CALLBACK ProxyWndProc(HWND const window, UINT const message,
                                         WPARAM const wparam,
                                         LPARAM const lparam) noexcept;

    explicit FlutterWindow(const flutter::DartProject& project);
    virtual ~FlutterWindow();

   protected:
    bool OnCreate() override;
    void OnDestroy() override;
    LRESULT MessageHandler(HWND window, UINT const message, WPARAM const wparam,
                           LPARAM const lparam) noexcept override;

   private:
    // Core Flutter members
    flutter::DartProject project_;
    std::unique_ptr<flutter::FlutterViewController> flutter_controller_;

    // Method Channel Logic
    void HandleMethodCall(
        const flutter::MethodCall<flutter::EncodableValue>& call,
        std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);

    // Callbacks for services
    void OnPeerListUpdated(const std::vector<winrt::Windows::Devices::Enumeration::DeviceInformation>& devices);
    winrt::fire_and_forget OnConnectionRequested(winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionRequest request);
    void OnConnectionEstablished(const ConnectionInfo& info);
    void OnConnectionLost();
    void OnSocketsReady(winrt::Windows::Networking::Sockets::StreamSocket chat_socket,
                        winrt::Windows::Networking::Sockets::StreamSocket speed_test_socket,
                        winrt::Windows::Networking::Sockets::StreamSocket file_transfer_socket);

    // Helper to send data back to Dart on the UI thread
    void SendEventToDart(std::string eventName, flutter::EncodableValue args);

    // Service Members
    std::unique_ptr<WifiDirectManager> wifi_direct_manager_;
    std::unique_ptr<NetworkService> network_service_;

    // Handle for the invisible proxy window used for thread marshalling.
    HWND proxy_window_handle_ = nullptr;
};

#endif  // RUNNER_FLUTTER_WINDOW_H_