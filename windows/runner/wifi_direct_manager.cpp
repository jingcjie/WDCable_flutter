#include "wifi_direct_manager.h"

#include <windows.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Networking.h>

#include "utils.h"

WifiDirectManager::WifiDirectManager(PeerListUpdatedCallback peer_callback,
                                     ConnectionRequestedCallback request_callback,
                                     ConnectionEstablishedCallback established_callback,
                                     ConnectionLostCallback lost_callback)
    : peer_list_updated_callback_(peer_callback),
      connection_requested_callback_(request_callback),
      connection_established_callback_(established_callback),
      connection_lost_callback_(lost_callback) {}

WifiDirectManager::~WifiDirectManager() {
    Disconnect();
    StopAdvertising();
    StopDiscovery();
}

void WifiDirectManager::StartAdvertising() {
    try {
        OutputDebugString(L"WDM: StartAdvertising called.\n");
        if (!publisher_) {
            publisher_ = winrt::Windows::Devices::WiFiDirect::WiFiDirectAdvertisementPublisher();
        }
        publisher_.Advertisement().IsAutonomousGroupOwnerEnabled(true);
        publisher_.Advertisement().ListenStateDiscoverability(winrt::Windows::Devices::WiFiDirect::WiFiDirectAdvertisementListenStateDiscoverability::Normal);
        publisher_.Start();

        if (!connectionListener_) {
            connectionListener_ = winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionListener();
            connectionRequestedToken_ = connectionListener_.ConnectionRequested({this, &WifiDirectManager::OnConnectionRequested});
            OutputDebugString(L"WDM: Connection listener started.\n");
        }

    } catch (winrt::hresult_error const& ex) {
        winrt::hstring message = L"WDM: StartAdvertising FAILED: " + ex.message() + L"\n";
        OutputDebugString(message.c_str());
    }
}

void WifiDirectManager::StopAdvertising() {
    if (publisher_ && publisher_.Status() == winrt::Windows::Devices::WiFiDirect::WiFiDirectAdvertisementPublisherStatus::Started) {
        publisher_.Stop();
    }
    if (connectionListener_) {
        connectionListener_.ConnectionRequested(connectionRequestedToken_);
        connectionListener_ = nullptr;
    }
}

void WifiDirectManager::StartDiscovery() {
    try {
        if (deviceWatcher_ && (deviceWatcher_.Status() == winrt::Windows::Devices::Enumeration::DeviceWatcherStatus::Started ||
                               deviceWatcher_.Status() == winrt::Windows::Devices::Enumeration::DeviceWatcherStatus::EnumerationCompleted)) {
            return;
        }

        winrt::hstring selector = winrt::Windows::Devices::WiFiDirect::WiFiDirectDevice::GetDeviceSelector(
            winrt::Windows::Devices::WiFiDirect::WiFiDirectDeviceSelectorType::AssociationEndpoint);

        auto requestedProperties = winrt::single_threaded_vector<winrt::hstring>();

        requestedProperties.Append(L"System.Devices.WiFiDirect.InformationElements");
        requestedProperties.Append(L"System.Devices.WiFiDirect.DeviceAddress");
        requestedProperties.Append(L"System.Devices.Aep.IsConnected");

        deviceWatcher_ = winrt::Windows::Devices::Enumeration::DeviceInformation::CreateWatcher(
            selector,
            requestedProperties.GetView());

        discoveredDevices_.clear();

        deviceAddedToken_ = deviceWatcher_.Added({this, &WifiDirectManager::OnDeviceAdded});

        deviceEnumerationCompletedToken_ = deviceWatcher_.EnumerationCompleted([this](auto&&, auto&&) {
            OutputDebugString(L"Initial enumeration completed.\n");
        });

        deviceWatcher_.Start();

    } catch (winrt::hresult_error const& ex) {
        OutputDebugString(ex.message().c_str());
    }
}

void WifiDirectManager::StopDiscovery() {
    if (deviceWatcher_) {
        if (deviceWatcher_.Status() == winrt::Windows::Devices::Enumeration::DeviceWatcherStatus::Started ||
            deviceWatcher_.Status() == winrt::Windows::Devices::Enumeration::DeviceWatcherStatus::EnumerationCompleted) {
            deviceWatcher_.Stop();
        }
        if (deviceAddedToken_) {
            deviceWatcher_.Added(deviceAddedToken_);
            deviceAddedToken_ = {};
        }
        deviceWatcher_ = nullptr;
    }
}
void WifiDirectManager::OnDeviceAdded(winrt::Windows::Devices::Enumeration::DeviceWatcher sender,
                                      winrt::Windows::Devices::Enumeration::DeviceInformation info) {
    try {
        discoveredDevices_.push_back(info);
        if (peer_list_updated_callback_) {
            peer_list_updated_callback_(discoveredDevices_);
        }
    } catch (...) {
    }
}

winrt::fire_and_forget WifiDirectManager::ConnectToDeviceAsync(const std::string& device_id) {
    try {
        winrt::hstring id_hstr = winrt::to_hstring(device_id);
        Disconnect();

        auto connectionParameters = winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionParameters();

        // 0 = Client, 15 = Group Owner.
        // If Android is the one "Advertising", the PC should usually be the Client (0).
        connectionParameters.GroupOwnerIntent(0);

        // Specify that we want to use the standard negotiation
        connectionParameters.PreferredPairingProcedure(
            winrt::Windows::Devices::WiFiDirect::WiFiDirectPairingProcedure::GroupOwnerNegotiation);

        OutputDebugString(L"WDM: FromIdAsync starting with parameters...\n");

        // Use the overload that takes parameters
        connectedDevice_ = co_await winrt::Windows::Devices::WiFiDirect::WiFiDirectDevice::FromIdAsync(id_hstr, connectionParameters);

        if (!connectedDevice_) {
            OutputDebugString(L"WDM: FromIdAsync returned null.\n");
            connection_lost_callback_();
            co_return;
        }

        connectionStatusChangedToken_ = connectedDevice_.ConnectionStatusChanged({this, &WifiDirectManager::OnConnectionStatusChanged});

        // Trigger the handshake
        auto currentStatus = connectedDevice_.ConnectionStatus();
        if (currentStatus == winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionStatus::Connected) {
            GetConnectionDetails(connectedDevice_);
        } else {
            OutputDebugString(L"WDM: Triggering handshake via GetConnectionEndpointPairs...\n");
            connectedDevice_.GetConnectionEndpointPairs();
        }

    } catch (winrt::hresult_error const& ex) {
        std::wstring err = L"WDM: Exception: " + std::wstring(ex.message()) + L"\n";
        OutputDebugString(err.c_str());
        Disconnect();
    }
    co_return;
}

void WifiDirectManager::Disconnect() {
    if (connectedDevice_) {
        OutputDebugString(L"WDM: Disconnect called.\n");
        connectedDevice_.ConnectionStatusChanged(connectionStatusChangedToken_);
        connectionStatusChangedToken_ = {};
        connectedDevice_.Close();
        connectedDevice_ = nullptr;
    }
    if (connection_lost_callback_) {
        connection_lost_callback_();
    }
}

void WifiDirectManager::OnConnectionRequested(winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionListener sender,
                                              winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionRequestedEventArgs args) {
    OutputDebugString(L"WDM: OnConnectionRequested event received.\n");
    if (connection_requested_callback_) {
        connection_requested_callback_(args.GetConnectionRequest());
    }
}

void WifiDirectManager::OnConnectionStatusChanged(winrt::Windows::Devices::WiFiDirect::WiFiDirectDevice sender, winrt::Windows::Foundation::IInspectable const& args) {
    auto status = sender.ConnectionStatus();
    if (status == winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionStatus::Connected) {
        OutputDebugString(L"WDM: OnConnectionStatusChanged event: CONNECTED. Getting connection details...\n");
        GetConnectionDetails(sender);
    } else if (status == winrt::Windows::Devices::WiFiDirect::WiFiDirectConnectionStatus::Disconnected) {
        OutputDebugString(L"WDM: OnConnectionStatusChanged event: DISCONNECTED.\n");
        Disconnect();
    } else {
        OutputDebugString(L"WDM: OnConnectionStatusChanged event: OTHER STATUS.\n");
    }
}

winrt::fire_and_forget WifiDirectManager::GetConnectionDetails(winrt::Windows::Devices::WiFiDirect::WiFiDirectDevice device) {
    OutputDebugString(L"WDM: GetConnectionDetails called.\n");
    try {
        auto endpointPairs = device.GetConnectionEndpointPairs();
        if (endpointPairs.Size() > 0) {
            OutputDebugString(L"WDM: Found endpoint pairs. Extracting IPs.\n");
            auto endpoint = endpointPairs.GetAt(0);

            ConnectionInfo info{};
            info.localIp = winrt::to_string(endpoint.LocalHostName().CanonicalName());
            info.remoteIp = winrt::to_string(endpoint.RemoteHostName().CanonicalName());

            std::wstring logMsg = L"WDM: Local IP: " + Utf8ToUtf16(info.localIp) + L", Remote IP: " + Utf8ToUtf16(info.remoteIp) + L"\n";
            OutputDebugString(logMsg.c_str());

            if (info.localIp.length() > 2 && info.localIp.substr(info.localIp.length() - 2) == ".1") {
                info.isGroupOwner = true;
                OutputDebugString(L"WDM: Determined role as Group Owner.\n");
            } else {
                info.isGroupOwner = false;
                OutputDebugString(L"WDM: Determined role as Client.\n");
            }

            if (connection_established_callback_) {
                connection_established_callback_(info);
            }
        } else {
            OutputDebugString(L"WDM: GetConnectionDetails FAILED: No endpoint pairs found.\n");
            Disconnect();
        }
    } catch (winrt::hresult_error const& ex) {
        winrt::hstring message = L"WDM: GetConnectionDetails FAILED with exception: " + ex.message() + L"\n";
        OutputDebugString(message.c_str());
        Disconnect();
    }
    co_return;
}