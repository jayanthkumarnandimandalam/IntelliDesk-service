# Software Installation and Request Guide

## Overview

This guide explains how to request, install, and manage software on corporate devices. All software installations on company-managed devices must follow the approved process to maintain security compliance, licensing integrity, and system stability. Employees cannot install unapproved software directly on corporate devices.

## Software Catalogue

The company maintains a curated software catalogue accessible through the Self-Service Software Portal. This catalogue contains pre-approved applications that can be installed immediately without additional approval.

### Accessing the Software Portal

1. Open your web browser and navigate to https://software.company.internal
2. Log in with your corporate SSO credentials
3. Browse or search the catalogue for your required software
4. The portal displays application name, version, description, category, and installation instructions

### Categories Available

- **Productivity**: Microsoft Office 365, Adobe Acrobat Reader, Notepad++, 7-Zip
- **Browsers**: Google Chrome, Mozilla Firefox, Microsoft Edge
- **Communication**: Microsoft Teams, Slack, Zoom
- **Development**: Visual Studio Code, IntelliJ IDEA, Git, Node.js, Python, Docker Desktop
- **Design**: Figma (web), Adobe Creative Cloud (requires licence approval)
- **Utilities**: VPN Client, Remote Desktop tools, PDF editors, screen capture tools
- **Security**: Corporate antivirus, password manager, certificate manager

## Self-Service Installation

### Installing Pre-Approved Software

For software listed in the Self-Service catalogue:

1. Navigate to the Software Portal at https://software.company.internal
2. Search for the application you need
3. Click "Install" next to the application name
4. The Company Portal agent on your device will begin downloading and installing the software
5. You may see a notification from the Company Portal indicating installation progress
6. Most installations complete within 5 to 15 minutes depending on software size
7. Some applications may require a system restart to complete installation
8. After installation, the application will appear in your Start menu (Windows) or Applications folder (macOS)

### Verifying Installation

After installation, verify the software is working correctly:

1. Launch the application from your Start menu or Applications folder
2. Check the version number matches the one listed in the Software Portal
3. For development tools, verify integration with your existing toolchain
4. If the application requires a licence key, check the Software Portal for activation instructions
5. Report any installation failures through the Service Portal

## Requesting Non-Catalogue Software

If the software you need is not available in the Self-Service catalogue, you must submit a formal request for approval.

### Submission Process

1. Navigate to the Service Portal at https://serviceportal.company.internal/software-request
2. Complete the Software Request Form with the following information:
   - Software name and version
   - Publisher and official download URL
   - Business justification (explain why this software is needed for your role)
   - Alternative options considered (list any catalogue alternatives and why they are insufficient)
   - Department and cost centre (for paid software)
   - Number of licences required
   - Expected duration of use (permanent or time-limited)
3. Submit the form and note your request reference number
4. You will receive an email confirmation with the estimated review timeline

### Approval Workflow

Software requests follow this approval chain:

1. **Line Manager**: Reviews business justification (1-2 business days)
2. **IT Security Review**: Assesses security risks, data handling, and compliance implications (2-5 business days)
3. **Licensing Review**: Verifies licence terms, costs, and procurement options (1-3 business days)
4. **Final Approval**: IT Director sign-off for enterprise-wide or high-cost software (1-2 business days)

Total estimated timeline: 5-12 business days depending on complexity and cost.

### Request Status Tracking

Track your software request status at any time:

1. Log into the Service Portal
2. Navigate to "My Requests"
3. Find your request by reference number or date submitted
4. View the current approval stage and any comments from reviewers
5. You will receive email notifications at each stage of the approval process

## Installation Methods

### Company Portal (Recommended)

The Company Portal is the primary method for deploying approved software:

1. Open the Company Portal application on your device (pre-installed on all corporate machines)
2. Search for the approved application
3. Click "Install" and wait for deployment to complete
4. The Company Portal manages updates and version control automatically

### Manual Installation (With IT Assistance)

For complex software requiring custom configuration:

1. Schedule an appointment with IT Support through the Service Portal
2. An IT technician will remotely connect to your device or arrange an in-person visit
3. The technician will install and configure the software according to corporate standards
4. Post-installation testing will be performed to ensure proper functionality
5. The software will be registered in the asset management system

### Developer Tools (Self-Managed)

Engineering team members have elevated permissions for development-specific tools:

1. Development tools listed in the "Developer Approved" section can be installed directly
2. Use your preferred package manager (Chocolatey on Windows, Homebrew on macOS, apt/dnf on Linux)
3. Only install versions that are on the approved versions list maintained by the Engineering Platform team
4. Do not install pre-release, beta, or nightly builds without explicit approval from your engineering lead
5. All developer tool installations are logged and audited quarterly

## Software Updates and Patches

### Automatic Updates

Most catalogue software is configured for automatic updates managed by the Company Portal:

1. Security patches are deployed within 48 hours of availability
2. Feature updates are deployed during scheduled maintenance windows (typically weekends)
3. Critical zero-day patches may be deployed immediately with a notification to affected users
4. You will receive a notification when updates require a restart

### Manual Updates

If you need to update software manually:

1. Check the Software Portal for the latest approved version
2. If a newer version is available in the portal, click "Update"
3. Do not download updates directly from the internet — always use the Software Portal
4. If the portal version is outdated and you need a newer version, submit a request through the Service Portal

## Uninstalling Software

### Self-Service Removal

1. Open the Company Portal
2. Navigate to "Installed" to view your installed applications
3. Click "Uninstall" next to the application you want to remove
4. Confirm the removal when prompted
5. The application will be removed within 5 minutes

### Requesting Removal of Managed Software

Some enterprise software cannot be self-removed:

1. Submit a request through the Service Portal under "Software Removal"
2. Provide the application name and reason for removal
3. IT will schedule the removal during your next available maintenance window

## Troubleshooting Installation Issues

### Installation Fails or Hangs

1. Ensure your device has sufficient disk space (at least 2x the application size for installation)
2. Close all other applications to free system resources
3. Check that your device is connected to the corporate network (VPN for remote workers)
4. Restart the Company Portal service: open Services (services.msc), find "IntuneManagementExtension", restart it
5. Reboot your device and attempt the installation again
6. If the issue persists, submit a ticket with the error message and screenshots

### Application Not Working After Installation

1. Verify the application version is correct and matches the approved version
2. Check if the application requires additional dependencies (Java runtime, .NET framework, etc.)
3. Run the application as administrator (right-click > Run as administrator) to check for permission issues
4. Check the Windows Event Viewer or macOS Console for error logs related to the application
5. Uninstall and reinstall the application through the Company Portal
6. If issues continue, contact IT Help Desk with details of the error

### Licence Activation Issues

1. Check the Software Portal for licence key or activation instructions specific to your application
2. Ensure you are logged into the application with your corporate credentials (many apps use SSO)
3. For seat-based licences, verify with your manager that a licence has been allocated to you
4. Contact the Licensing team at licensing@company.internal for licence-related queries
5. Do not use personal licence keys on corporate devices or share corporate keys

## Compliance and Policies

- All software on corporate devices must be approved through the official process
- Pirated, cracked, or unlicensed software is strictly prohibited and will result in disciplinary action
- Open-source software must still be reviewed by IT Security for compliance with corporate policies
- Software audits are conducted quarterly; unapproved software will be flagged for removal
- Employees are responsible for reporting any software that was installed outside the approved process
- Shadow IT (using unapproved cloud services or locally installed tools) violates corporate security policy

## Getting Help

For software installation and management issues:
- Self-Service Portal: https://software.company.internal
- Service Portal: https://serviceportal.company.internal/software
- Phone: IT Help Desk, Extension 4357
- Email: helpdesk@company.internal
- Licensing queries: licensing@company.internal
- Hours: Monday to Friday, 7:00 AM to 7:00 PM
