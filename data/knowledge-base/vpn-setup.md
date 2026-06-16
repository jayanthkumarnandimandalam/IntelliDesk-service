# VPN Setup and Troubleshooting Guide

## Overview

The corporate Virtual Private Network (VPN) provides secure remote access to internal company resources. All employees working remotely or connecting from external networks must use the VPN to access internal applications, file shares, and development environments. This guide covers installation, configuration, and troubleshooting of the corporate VPN client.

## Supported VPN Client

The company uses GlobalProtect VPN client version 6.x for all platforms. This client supports Windows 10/11, macOS 12 and later, and common Linux distributions including Ubuntu 22.04+ and Fedora 38+. Mobile clients are available for iOS 16+ and Android 13+.

## Installation Instructions

### Windows Installation

1. Download the GlobalProtect installer from the internal software portal at https://software.company.internal/vpn
2. Right-click the downloaded MSI file and select "Run as administrator"
3. Accept the licence agreement and click "Next" through the installer wizard
4. When prompted for the portal address, enter: vpn.company.internal
5. Leave all other settings at their defaults unless instructed otherwise by IT
6. Click "Install" and wait for the installation to complete
7. Restart your computer when prompted
8. After restart, the GlobalProtect icon will appear in your system tray

### macOS Installation

1. Download the GlobalProtect PKG file from the software portal
2. Double-click the PKG file to launch the installer
3. Follow the installation prompts and enter your macOS administrator password when requested
4. When prompted about system extensions, navigate to System Settings > Privacy & Security and approve the GlobalProtect system extension
5. Enter the portal address: vpn.company.internal
6. The GlobalProtect icon will appear in your menu bar upon completion

### Linux Installation

1. Download the appropriate package for your distribution (DEB for Ubuntu/Debian, RPM for Fedora/RHEL)
2. Install using your package manager: `sudo dpkg -i globalprotect.deb` or `sudo rpm -i globalprotect.rpm`
3. Start the service: `sudo systemctl start gpd`
4. Launch the client: `globalprotect connect --portal vpn.company.internal`
5. Configure the service to start on boot: `sudo systemctl enable gpd`

## Initial Configuration

### First-Time Connection

1. Click the GlobalProtect icon in your system tray or menu bar
2. Enter the portal address: vpn.company.internal (this should be pre-populated from installation)
3. Click "Connect"
4. Enter your corporate credentials (same as SSO login)
5. Complete the MFA challenge on your registered device
6. Wait for the connection to establish. The icon will change to indicate a successful connection
7. Verify connectivity by accessing an internal resource such as the intranet homepage

### Connection Profiles

The VPN supports multiple connection profiles for different access needs:

- **General Access**: Default profile for standard corporate network access. Provides access to email, intranet, and common business applications
- **Development**: Extended profile for engineering teams. Includes access to development servers, staging environments, and source code repositories
- **Restricted**: High-security profile for accessing sensitive systems. Requires additional authentication and has stricter timeout policies

Your profile is automatically assigned based on your Active Directory group membership. Contact your manager or IT if you need a different access profile.

## Split Tunnelling Configuration

The corporate VPN uses split tunnelling by default. This means only traffic destined for internal company networks is routed through the VPN tunnel. Internet traffic such as web browsing, streaming, and personal applications uses your local internet connection directly.

The following networks are routed through the VPN:
- 10.0.0.0/8 (internal corporate network)
- 172.16.0.0/12 (development and staging environments)
- Internal DNS domains: *.company.internal, *.dev.company.internal

If you need full tunnel mode for compliance testing or security assessments, contact the Network Security team to request a temporary full-tunnel profile.

## Troubleshooting Common Issues

### Unable to Connect

If the VPN client fails to establish a connection:

1. Verify your internet connection is working by accessing an external website
2. Check that the portal address is correct: vpn.company.internal
3. Ensure your corporate password has not expired (reset via SSO portal if needed)
4. Disable any personal VPN or proxy software that may conflict
5. Check your system clock is accurate (MFA tokens require correct time)
6. Try disconnecting from your current WiFi and reconnecting
7. If on a public network, try switching to a mobile hotspot as some networks block VPN protocols
8. Restart the GlobalProtect service and try again

### Frequent Disconnections

If the VPN connects but drops frequently:

1. Check your internet connection stability using a speed test
2. Ensure your WiFi signal strength is adequate (at least 3 bars)
3. Move closer to your router or use a wired Ethernet connection
4. Disable WiFi power saving mode in your network adapter settings
5. Update your network adapter drivers to the latest version
6. Check if your router firmware needs updating
7. If using a corporate laptop, ensure the latest GlobalProtect client update is installed

### Slow Performance Over VPN

If internal resources are slow to load while connected to the VPN:

1. Run a speed test both with and without VPN connected to identify the difference
2. Check if the issue affects all internal resources or only specific applications
3. Try connecting to a different VPN gateway region if available
4. Close bandwidth-intensive applications that are not needed
5. If using video conferencing, check if the application is correctly excluded from the VPN tunnel via split tunnelling
6. Report persistent performance issues to the Network Operations team with speed test results

### Authentication Errors

If you receive authentication errors when connecting:

1. Verify you are using your current corporate credentials
2. If you recently changed your password, update the saved credentials in the VPN client
3. Clear the saved credentials: Settings > Clear Saved Credentials > Reconnect
4. Check if your account is locked by attempting to log into the SSO portal
5. Ensure your MFA device is registered and functioning properly
6. Contact the Help Desk if your account requires unlocking

### DNS Resolution Issues

If internal hostnames are not resolving while connected to the VPN:

1. Open a command prompt or terminal
2. Run `nslookup intranet.company.internal` to test DNS resolution
3. If resolution fails, try flushing the DNS cache: `ipconfig /flushdns` (Windows) or `sudo dscacheutil -flushcache` (macOS)
4. Verify the VPN is assigning DNS servers correctly: check your network connection details
5. Restart the GlobalProtect client
6. If issues persist, try disabling and re-enabling your network adapter

## Security Policies

- VPN sessions automatically disconnect after 12 hours of continuous use. You must reconnect to continue accessing internal resources
- Inactive VPN sessions timeout after 30 minutes of no network activity
- The VPN client performs a security posture check before connecting. Your device must have up-to-date antivirus software, enabled firewall, and current OS patches
- Do not share your VPN credentials with anyone, including IT support staff
- Report any suspicious VPN connection attempts or unexpected disconnections to the Security Operations Centre

## Getting Help

For VPN-related issues not covered in this guide, contact the IT Help Desk:
- Phone: Extension 4357
- Email: helpdesk@company.internal
- Service Portal: https://serviceportal.company.internal/vpn
- Available Monday to Friday, 7:00 AM to 7:00 PM
- Emergency after-hours support available for critical connectivity issues
