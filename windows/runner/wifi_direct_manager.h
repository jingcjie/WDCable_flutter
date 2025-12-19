#pragma once

#include <flutter/encodable_value.h>
#include <winrt/Windows.Devices.Enumeration.h>
#include <winrt/Windows.Devices.WiFiDirect.h>

#include <functional>
#include <string>
#include <vector>

struct ConnectionInfo;

class WifiDirectManager {
   public:
    using PeerListUpdatedCallback = std::function<void(const std::vector<winrt::Windows::Devices::Enumeration::DeviceInformation>&)>;
    using ConnectionRequestedCallback = std::function<winrt::fire_and_forget(winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionRequest)>;
    using ConnectionEstablishedCallback = std::function<void(const ConnectionInfo&)>;
    using ConnectionLostCallback = std::function<void()>;

    WifiDirectManager(PeerListUpdatedCallback peer_callback,
                      ConnectionRequestedCallback request_callback,
                      ConnectionEstablishedCallback established_callback,
                      ConnectionLostCallback lost_callback);
    ~WifiDirectManager();

    void StartAdvertising();
    void StopAdvertising();
    void StartDiscovery();
    void StopDiscovery();

    winrt::fire_and_forget ConnectToDeviceAsync(const std::string& device_id);
    void Disconnect();

   private:
    void OnDeviceAdded(winrt::Windows::Devices::Enumeration::DeviceWatcher sender, winrt::Windows::Devices::Enumeration::DeviceInformation info);
    void OnConnectionRequested(winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionListener sender,
                               winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionRequestedEventArgs args);
    void OnConnectionStatusChanged(winrt::Windows::Devices::WiFiDirect::WiFiDirectDevice sender, winrt::Windows::Foundation::IInspectable const& args);

    // Asynchronous helper to get IP addresses after a connection is established.
    winrt::fire_and_forget GetConnectionDetails(winrt::Windows::Devices::WiFiDirect::WiFiDirectDevice device);

    // Member variables for storing callbacks.
    PeerListUpdatedCallback peer_list_updated_callback_;
    ConnectionRequestedCallback connection_requested_callback_;
    ConnectionEstablishedCallback connection_established_callback_;
    ConnectionLostCallback connection_lost_callback_;

    // WinRT objects for managing WiFi Direct.
    winrt::Windows::Devices::WiFiDirect::WiFiDirectAdvertisementPublisher publisher_{nullptr};
    winrt::Windows::Devices::Enumeration::DeviceWatcher deviceWatcher_{nullptr};
    winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionListener connectionListener_{nullptr};
    winrt::Windows::Devices::WiFiDirect::WiFiDirectDevice connectedDevice_{nullptr};

    // Tokens to manage event handler registration/unregistration.
    winrt::event_token deviceAddedToken_{};
    winrt::event_token connectionRequestedToken_{};
    winrt::event_token connectionStatusChangedToken_{};
    winrt::event_token deviceEnumerationCompletedToken_;

    // Local cache of discovered devices.
    std::vector<winrt::Windows::Devices::Enumeration::DeviceInformation> discoveredDevices_;
};

// Sstruct to bundle connection details.
struct ConnectionInfo {
    bool isGroupOwner = false;
    std::string localIp;
    std::string remoteIp;
};