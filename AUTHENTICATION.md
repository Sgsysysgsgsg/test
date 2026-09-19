# Authentication modes

Geyser Mobile supports:
- Online: `auth-type: online`
- Offline: `auth-type: offline`
- Floodgate: `auth-type: floodgate`

The Floodgate key picker accepts any file ending in `.pem`, including names such
as `key.pem`, `nlk.pem`, or `myserver.pem`. The original filename is displayed
in the UI, while the app stores it privately as `floodgate-key.pem` so Geyser
can use a stable internal path.

The key is never uploaded by the app. Keep Floodgate keys private.
