# Printer Installation and Troubleshooting Guide

## Overview

This guide covers the installation, configuration, and troubleshooting of network printers available throughout the corporate office environment. All printers are managed centrally through the corporate print server and support secure print release via employee badge authentication.

## Available Printers

The company maintains networked multifunction printers on each floor. These devices support printing, scanning, copying, and faxing. All printers are accessible from any corporate-managed device connected to the internal network or VPN.

### Printer Naming Convention

Printers follow the naming format: `PRN-[Building]-[Floor]-[Location]`

Examples:
- PRN-HQ-3-NORTH — Headquarters, 3rd Floor, North Wing
- PRN-HQ-3-SOUTH — Headquarters, 3rd Floor, South Wing
- PRN-SAT-1-MAIN — Satellite Office, 1st Floor, Main Area

### Printer Capabilities

| Feature | Standard Printer | High-Capacity Printer |
|---------|-----------------|----------------------|
| Colour Printing | Yes | Yes |
| Double-Sided | Yes | Yes |
| Stapling | No | Yes |
| Scanning to Email | Yes | Yes |
| Scanning to Network Folder | Yes | Yes |
| A3 Paper Size | No | Yes |
| Monthly Duty Cycle | 5,000 pages | 25,000 pages |

## Adding a Network Printer

### Windows (Automatic via Print Server)

1. Open Settings > Devices > Printers & Scanners
2. Click "Add a printer or scanner"
3. Wait for Windows to discover available network printers
4. If your desired printer appears in the list, click on it and select "Add device"
5. Windows will automatically download and install the correct driver from the print server
6. The printer will appear in your list of available printers within 30 seconds

### Windows (Manual Connection)

If automatic discovery does not find your printer:

1. Open Settings > Devices > Printers & Scanners
2. Click "Add a printer or scanner" then "The printer that I want isn't listed"
3. Select "Select a shared printer by name"
4. Enter the print server path: `\\printserver.company.internal\PRN-[Building]-[Floor]-[Location]`
5. Click "Next" and wait for the connection to establish
6. The driver will be installed automatically from the print server
7. Choose whether to set this printer as your default
8. Print a test page to verify the connection

### macOS Installation

1. Open System Settings > Printers & Scanners
2. Click the "+" button to add a new printer
3. Select the "IP" tab at the top of the dialog
4. Enter the printer IP address (available on the printer information sheet posted near each device)
5. For Protocol, select "HP Jetdirect - Socket"
6. The Name and Location fields will auto-populate
7. For Use/Driver, select "Generic PostScript Printer" or download the specific driver from the software portal
8. Click "Add" to complete the installation
9. Alternatively, use the print server path: smb://printserver.company.internal/PRN-[name]

### Linux Installation

1. Open your distribution's printer settings (System Settings > Printers on Ubuntu)
2. Click "Add" or "Add Printer"
3. Select "Network Printer" and then "Windows Printer via SAMBA"
4. Enter the SMB URI: smb://printserver.company.internal/PRN-[name]
5. Select the appropriate driver (Generic PCL or PostScript)
6. Enter your corporate credentials when prompted
7. Set print options (paper size, default quality) as needed

## Secure Print Release

All print jobs are held in a secure queue until released at the printer using your employee badge. This prevents sensitive documents from being left unattended at the printer.

### How Secure Print Works

1. Send your print job from any application as normal
2. The job is sent to the print server and held in your personal secure queue
3. Walk to any corporate printer in the building
4. Tap your employee badge on the card reader attached to the printer
5. Your queued print jobs will be displayed on the printer's touch screen
6. Select the jobs you want to print and tap "Print"
7. Uncollected jobs are automatically deleted after 24 hours

### Badge Not Recognised

If the printer does not recognise your badge:

1. Ensure you are tapping the badge firmly on the card reader
2. Try removing your badge from its holder or wallet (other cards can interfere)
3. Check that the card reader LED is active (flashing green)
4. Try a different printer to determine if the issue is with the specific device
5. If no printers recognise your badge, contact Facilities to verify your badge is active
6. Contact IT Help Desk if your badge works for door access but not printers

## Scanning Documents

### Scan to Email

1. Place your document on the scanner glass or in the document feeder
2. Tap your badge to log into the printer
3. Select "Scan to Email" on the touch screen
4. Your corporate email address will be pre-populated as the recipient
5. Add additional recipients if needed by entering their email addresses
6. Select scan settings: colour/black-and-white, resolution (150/300/600 DPI), file format (PDF/TIFF/JPEG)
7. Press "Scan" to begin
8. The scanned document will be delivered to the specified email addresses within 2 minutes

### Scan to Network Folder

1. Log into the printer with your badge
2. Select "Scan to Folder"
3. Browse to your personal network folder or enter the path manually
4. Your personal scan folder is: `\\fileserver.company.internal\scans\[employee-id]`
5. Configure scan settings as required
6. Press "Scan" — the file will be saved to the specified network location

## Troubleshooting Common Issues

### Print Job Not Appearing at Printer

1. Verify the print job was sent successfully (check the print queue on your computer)
2. Ensure you are logged into the correct printer with your badge
3. Check that you selected the correct printer when sending the job (not a PDF printer or virtual device)
4. Verify your computer is connected to the corporate network (VPN connection is required for remote printing)
5. Try cancelling the job and resending it
6. If the job shows as "Error" in your computer's print queue, delete it and try again

### Poor Print Quality

1. Try printing a test page from the printer's built-in menu to determine if the issue is with the printer or your document
2. Check if the issue occurs with all documents or just a specific file
3. For streaks or faded areas, the toner may be running low — report this using the Service Portal
4. For misaligned printing, run the printer's alignment calibration from its built-in menu
5. Ensure you have selected the correct paper type in your print settings (Plain Paper, Glossy, Cardstock)
6. For colour issues, check that you have not accidentally selected "Grayscale" printing

### Paper Jam

If you encounter a paper jam:

1. Check the printer display screen for specific instructions showing which tray or area is jammed
2. Open the indicated tray or access panel carefully
3. Gently pull the jammed paper in the direction of the paper path (do not pull against the feed direction)
4. Check for any small torn pieces of paper that may remain inside
5. Close all panels and trays securely
6. The printer will automatically attempt to resume printing
7. If the jam error persists after clearing all visible paper, contact Facilities

### Printer Offline

If a printer shows as "Offline" on your computer:

1. Check if other users can print to the same printer (ask a colleague or check the service status page)
2. If the printer is offline for everyone, it may be undergoing maintenance — check the Service Portal for scheduled maintenance windows
3. If only offline for you, try removing and re-adding the printer using the steps in the installation section
4. Restart the Print Spooler service on Windows: open Services (services.msc), find "Print Spooler", right-click, and select "Restart"
5. Check that your computer can reach the print server: open Command Prompt and run `ping printserver.company.internal`

### Cannot Find the Printer

If you cannot locate a specific printer:

1. Check the printer location map on the intranet at https://intranet.company.internal/printer-map
2. Printers are typically located in designated print areas near kitchen facilities and common areas
3. Each printer has a label showing its network name
4. Ask your floor warden or office administrator for assistance

## Printing Policies

- Duplex (double-sided) printing is enabled by default to reduce paper usage
- Colour printing is available but employees are encouraged to use black-and-white for internal documents
- Print jobs over 100 pages require manager approval through the Service Portal
- Personal printing is not permitted on corporate printers
- Confidential documents should only be printed using Secure Print Release
- Uncollected secure print jobs are automatically purged after 24 hours

## Getting Help

For printer-related issues:
- Self-service: https://serviceportal.company.internal/printers
- Phone: IT Help Desk, Extension 4357
- Email: helpdesk@company.internal
- For hardware issues (paper jams, toner replacement, device malfunction): Contact Facilities at extension 5000
- Hours: Monday to Friday, 7:00 AM to 7:00 PM
