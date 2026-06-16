# Password Reset Guide

## Overview

This document provides comprehensive instructions for resetting passwords across all corporate systems. Whether you have forgotten your password, your account has been locked, or you need to update your credentials for security reasons, this guide covers all scenarios you may encounter.

## Self-Service Password Reset

### Prerequisites

Before attempting a self-service password reset, ensure that you have previously registered your recovery options. These include a secondary email address, a mobile phone number for SMS verification, or security questions configured during your initial account setup.

### Step-by-Step Instructions

1. Navigate to the corporate Single Sign-On (SSO) portal at https://sso.company.internal
2. Click the "Forgot Password" link located below the login form
3. Enter your corporate email address (format: firstname.lastname@company.com)
4. Select your preferred recovery method from the available options
5. If you selected email recovery, check your registered secondary email for a reset link. The link expires after 15 minutes
6. If you selected SMS verification, enter the six-digit code sent to your registered mobile number
7. Create a new password following the password policy requirements listed below
8. Confirm the new password by entering it a second time
9. Click "Reset Password" to complete the process
10. You will receive a confirmation email at both your corporate and recovery email addresses

### Password Policy Requirements

All passwords must meet the following complexity requirements to ensure account security:

- Minimum length of 12 characters
- At least one uppercase letter (A-Z)
- At least one lowercase letter (a-z)
- At least one numeric digit (0-9)
- At least one special character from the set: !@#$%^&*()-_+=[]{}|;:,.<>?
- Cannot reuse any of your last 12 passwords
- Cannot contain your username, first name, or last name
- Cannot contain more than 3 consecutive identical characters
- Must not be found in the common password dictionary

Passwords expire every 90 days. You will receive email notifications at 14 days, 7 days, and 1 day before expiration.

## Account Lockout Recovery

### What Causes Account Lockout

Your account will be automatically locked after 5 consecutive failed login attempts within a 15-minute window. This security measure protects against brute-force attacks. The lockout duration is 30 minutes, after which you can attempt to log in again.

### Steps to Unlock Your Account

1. Wait for the 30-minute lockout period to expire, then try logging in with the correct password
2. If you cannot remember your password, use the self-service password reset process described above
3. If self-service reset is unavailable or not working, contact the IT Help Desk at extension 4357 or email helpdesk@company.internal
4. Provide your employee ID and answer your security verification questions
5. The help desk agent will verify your identity and unlock your account manually

### Preventing Future Lockouts

- Use your corporate password manager to store credentials securely
- Enable browser autofill only on company-managed devices
- Ensure Caps Lock is not accidentally enabled when typing passwords
- Check that your keyboard language and layout settings are correct
- If you use multiple devices, ensure your new password is updated on all devices after a reset

## Multi-Factor Authentication (MFA) Issues

### MFA Token Not Working

If your MFA token is not being accepted, try the following steps:

1. Verify that the time on your authenticator device is synchronised correctly. MFA tokens are time-based and require accurate clock settings
2. Wait for the current token to expire and try the next generated token
3. If using a hardware token, ensure the battery is not depleted
4. Clear the authenticator app cache and restart the application
5. If none of the above work, contact the IT Help Desk to have your MFA reset

### Lost or Stolen MFA Device

If you have lost your MFA device or it has been stolen:

1. Contact the IT Help Desk immediately at extension 4357
2. Request an emergency temporary access code
3. The help desk will disable MFA on your account temporarily
4. You will be required to re-register a new MFA device within 24 hours
5. If your device was stolen, also report this to the Security Operations team

## Service-Specific Password Resets

### Email (Microsoft 365)

Your email password is synchronised with your corporate SSO credentials. Resetting your SSO password will automatically update your email access. Allow up to 5 minutes for synchronisation to complete across all Microsoft 365 services.

### VPN

VPN credentials are tied to your Active Directory account. After a password reset, you must update the saved credentials in your VPN client. Open the VPN client settings, navigate to the connection profile, and enter your new password.

### Development Tools (GitHub Enterprise, Jira, Confluence)

These tools use SSO authentication. After resetting your corporate password, simply log out and log back in. Personal access tokens and API keys are not affected by password resets and remain valid.

## Troubleshooting Common Issues

### Reset Email Not Received

- Check your spam and junk folders in both corporate and recovery email
- Verify the recovery email address is correct in your profile settings
- Ensure your mailbox is not full (check storage quota)
- Wait up to 5 minutes for delivery; some email systems have delivery delays
- Contact the Help Desk if the email has not arrived after 10 minutes

### New Password Not Accepted

- Ensure you meet all complexity requirements listed above
- Try a completely different password rather than modifying your old one
- Avoid common patterns such as seasonal words followed by numbers
- Use a passphrase approach: combine four or more random words with special characters

### Still Unable to Access Account

If you have exhausted all self-service options and are still unable to access your account, please contact the IT Help Desk with the following information ready:

- Your full name and employee ID
- The specific system or application you are trying to access
- Any error messages displayed on screen (screenshots are helpful)
- The date and time of your last successful login
- Whether you recently changed devices or locations

The Help Desk is available Monday to Friday, 7:00 AM to 7:00 PM, and provides emergency support outside these hours for critical access issues.
