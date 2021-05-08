package com.hyperfield.remotehelp

import com.opentok.accelerator.core.utils.OTConfig

object AppConfig {
    // Fill the following arguments for your OpenTok Project. All the required information can be found in the dashboard
    // https://dashboard.tokbox.com/
    val otConfig = OTConfig(
             apiKey = "47134714",  // Replace with API key from the dashboard
            sessionId = "1_MX40NzEzNDcxNH5-MTYyMDIzNTk1MjU2NX5veXZDdzdBZk1yY0wyQldtVmFDQkNsbDJ-fg",  // Replace with a session id
            token = "T1==cGFydG5lcl9pZD00NzEzNDcxNCZzaWc9NjU4NTRhYTAxOTZmNTZlMTA0OGE2NzAxMTZlNjljYjVlM2QxOWEzYTpzZXNzaW9uX2lkPTFfTVg0ME56RXpORGN4Tkg1LU1UWXlNREl6TlRrMU1qVTJOWDV2ZVhaRGR6ZEJaazF5WTB3eVFsZHRWbUZEUWtOc2JESi1mZyZjcmVhdGVfdGltZT0xNjIwMjM2MDA1Jm5vbmNlPTAuODEwMDk3MzAyNjUzNTcxNCZyb2xlPXB1Ymxpc2hlciZleHBpcmVfdGltZT0xNjIyODI4MDA0JmluaXRpYWxfbGF5b3V0X2NsYXNzX2xpc3Q9" , // Replace with a token from the dashboard (or the OpenTok server SDK)
            sessionName = "acceleratretor-sample-app",
        subscribeToSelf = false,
        subscribeAutomatically = true
    )
}

