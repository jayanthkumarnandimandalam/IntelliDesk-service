# Email Configuration Guide

## Overview

This guide provides detailed instructions for configuring your corporate email account across various email clients and devices. The company uses Microsoft 365 (Exchange Online) for email services. All employees are provisioned with a corporate email address in the format firstname.lastname@company.com during the onboarding process.

## Email Account Details

Use the following settings when manually configuring your email client:

| Setting | Value |
|---------|-------|
| Email Address | firstname.lastname@company.com |
| Server Type | Microsoft Exchange / Microsoft 365 |
| Incoming Server (IMAP) | outlook.office365.com |
| Incoming Port (IMAP) | 993 (SSL/TLS) |
| Outgoing Server (SMTP) | smtp.office365.com |
| Outgoing Port (SMTP) | 587 (STARTTLS) |
| Authentication | OAuth 2.0 (Modern Authentication) |
| Username | Your full email address |

Note: Basic authentication (username/password only) has been deprecated and disabled. All clients must support Modern Authentication (OAuth 2.0) to connect.

## Outlook Desktop (Windows)

### Automatic Configuration

Microsoft Outlook on Windows typically configures itself automatically when connected to the corporate domain:

1. Open Microsoft Outlook from the Start menu
2. If this is your first time opening Outlook, the setup wizard will appear automatically
3. Enter your corporate email address and click "Connect"
4. You will be redirected to the corporate SSO login page
5. Enter your credentials and complete the MFA challenge
6. Outlook will automatically detect and configure all server settings
7. Click "Done" to complete the setup

### Manual Configuration

If automatic setup fails:

1. Open Outlook and navigate to File > Account Settings > Account Settings
2. Click "New" to add a new account
3. Select "Exchange" or "Microsoft 365" as the account type
4. Enter your email address: firstname.lastname@company.com
5. Click "Advanced Settings" and select "Let me set up my account manually"
6. Choose "Microsoft 365" as the account type
7. Enter the server: outlook.office365.com
8. Select OAuth 2.0 as the authentication method
9. Click "Connect" and complete the authentication flow
10. Outlook will synchronise your mailbox. Initial sync may take 15-30 minutes depending on mailbox size

## Outlook for Mac

1. Open Microsoft Outlook from the Applications folder
2. If prompted with the setup wizard, enter your corporate email address
3. Click "Continue" and authenticate through the SSO portal
4. Outlook will automatically configure using Autodiscover
5. If automatic configuration fails, go to Outlook > Preferences > Accounts
6. Click the "+" button and select "Exchange or Microsoft 365"
7. Enter your email address and proceed with Modern Authentication

## Apple Mail (macOS and iOS)

### macOS Configuration

1. Open System Settings > Internet Accounts
2. Click "Add Account" and select "Microsoft Exchange"
3. Enter your name and corporate email address
4. Click "Sign In" — you will be redirected to the corporate SSO page
5. Enter your credentials and complete MFA verification
6. Select which services to synchronise: Mail, Contacts, Calendars, Reminders
7. Click "Done" to complete the setup

### iOS Configuration

1. Open Settings > Mail > Accounts > Add Account
2. Select "Microsoft Exchange"
3. Enter your corporate email address and a description for the account
4. Tap "Sign In" and complete authentication through the SSO page
5. Select which data to synchronise (Mail, Contacts, Calendars, Notes)
6. Tap "Save" to complete the configuration
7. Your email will begin downloading. You can configure the sync period under Settings > Mail > Accounts > your account > Mail Days to Sync

## Android Email Configuration

### Gmail App

1. Open the Gmail app and tap your profile icon
2. Select "Add another account"
3. Choose "Office 365" or "Exchange"
4. Enter your corporate email address
5. Tap "Next" and authenticate through the company SSO portal
6. Grant the required permissions for mail synchronisation
7. Configure notification preferences as desired
8. Your corporate email will appear alongside any personal accounts

### Microsoft Outlook App (Recommended)

1. Download Microsoft Outlook from the Google Play Store
2. Open the app and tap "Add Account"
3. Enter your corporate email address
4. You will be redirected to the corporate SSO login
5. Complete authentication and MFA challenge
6. The app will configure automatically using Autodiscover
7. Enable notifications and configure focused inbox preferences as desired

## Shared Mailboxes and Distribution Lists

### Accessing Shared Mailboxes

If you have been granted access to a shared mailbox:

1. In Outlook Desktop: File > Account Settings > Account Settings > Change > More Settings > Advanced > Add shared mailbox
2. In Outlook Web: Click your profile icon > Open another mailbox > Enter the shared mailbox address
3. In Outlook Mobile: Settings > Add Account > Add Shared Mailbox

Shared mailboxes do not require a separate password. Access is controlled through Active Directory group membership.

### Distribution Lists

To send email to a distribution list, simply address your message to the list email address. Common distribution lists include:

- all-staff@company.com — All employees (requires manager approval)
- it-support@company.com — IT support team
- engineering@company.com — Engineering department
- hr@company.com — Human Resources

To request membership in a distribution list, submit a request through the Service Portal or contact your department administrator.

## Email Signature Configuration

### Corporate Signature Standard

All employees must use the corporate email signature template. The standard signature includes:

- Full name
- Job title
- Department
- Direct phone number
- Corporate email address
- Company logo (hosted image, not attached)
- Confidentiality disclaimer

### Setting Up Your Signature

1. Download the signature template from the intranet: https://intranet.company.internal/email-signatures
2. Customise the template with your personal details
3. In Outlook: File > Options > Mail > Signatures > New
4. Paste the template and configure it as default for new messages and replies
5. In Outlook Web: Settings > View all Outlook settings > Compose and reply > Email signature

## Troubleshooting Common Issues

### Cannot Send or Receive Email

1. Verify your internet connection is active
2. Check if you can access Outlook Web App at https://outlook.office365.com
3. If Outlook Web works but desktop client does not, try restarting Outlook
4. Check the Outlook status bar at the bottom for "Disconnected" or "Trying to connect"
5. Verify your password has not expired (check SSO portal)
6. Run the Microsoft Support and Recovery Assistant tool for automated diagnostics

### Authentication Prompts Keep Appearing

1. Close Outlook completely
2. Open Windows Credential Manager (Control Panel > Credential Manager)
3. Remove all entries related to "MicrosoftOffice" and "outlook.office365.com"
4. Reopen Outlook and authenticate fresh using Modern Authentication
5. If the issue persists, check that your Outlook version supports OAuth 2.0 (requires Outlook 2019 or later, or Microsoft 365 subscription)

### Large Mailbox Performance Issues

If Outlook is running slowly due to mailbox size:

1. Check your mailbox size: File > Account Settings > Data Files
2. Archive old emails: File > Tools > Clean Up Old Items
3. Empty your Deleted Items and Junk folders
4. Reduce the offline sync period in Exchange settings to 3 or 6 months
5. Consider creating local PST archives for old project emails

### Email Not Arriving

1. Check your Junk Email folder for misclassified messages
2. Verify with the sender that they have the correct email address
3. Check if any mail flow rules in Outlook are redirecting or deleting messages
4. If expecting external emails, ask the sender to check their delivery report
5. Contact IT if you suspect a mail flow rule at the organisation level is blocking messages

## Email Storage Quotas

Each corporate mailbox has the following storage limits:

- Standard quota: 50 GB
- Warning notification: at 45 GB usage
- Send restriction: at 49 GB (you cannot send new emails until you free space)
- Full restriction: at 50 GB (cannot send or receive)

To check your current usage, go to File > Account Settings > Data Files or check Outlook Web Settings > General > Storage.

## Getting Help

For email-related issues not covered in this guide:
- Phone: IT Help Desk, Extension 4357
- Email: helpdesk@company.internal
- Web: https://serviceportal.company.internal/email
- Hours: Monday to Friday, 7:00 AM to 7:00 PM
