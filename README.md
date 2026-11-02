# FertDLC Key Server

Java 21 HTTP service for validating, issuing, revoking, and listing FertDLC access keys. Only SHA-256 hashes, status, and role are stored in `keys.db`; an issued key is returned once by an issue endpoint. Three-column databases are migrated as regular user keys.

## Run locally

In PowerShell, from the repository root:

```powershell
$adminToken = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
Write-Host "Admin token: $adminToken"
$env:FERTDLC_ADMIN_TOKEN = $adminToken
$env:FERTDLC_KEYS_FILE = "$PWD\keys.db"
.\gradlew.bat -p key-server run
```

The service listens on `127.0.0.1:8787`. Keep it running while testing the loader and client. The server refuses to bind to a non-loopback address so its plain HTTP API cannot be exposed directly to a network.

## Manage keys

Copy the printed token to a secure place. In another PowerShell window, set the same value before running the admin commands:

```powershell
$env:FERTDLC_ADMIN_TOKEN = 'paste-the-printed-token-here'
$headers = @{ Authorization = "Bearer $env:FERTDLC_ADMIN_TOKEN" }
$key = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/admin/issue' -Headers $headers
$key
```

For a key that opens the in-loader admin panel, use `/admin/issue-admin` instead. To promote an existing key, post the key and role separated by a tab:

```powershell
$adminKey = 'paste-the-client-key-here'
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/admin/set-role' -Headers $headers -ContentType 'text/plain' -Body "$adminKey`tadmin"
```

Save issued keys; they cannot be recovered from the server database. An admin client key can use the loader's `/manage/issue`, `/manage/revoke`, and `/manage/list` routes. The separate `FERTDLC_ADMIN_TOKEN` remains server-only.

```powershell
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/admin/list' -Headers $headers
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/admin/revoke' -Headers $headers -ContentType 'text/plain' -Body $key
```

The list contains a short hash identifier, creation timestamp, status, and role. Revoke requires the original key.

## Production setup

The database stores only SHA-256 key hashes; the admin token stays server-side and must be at least 32 characters. Keep the token out of the loader, client, source control, and shared logs.

For remote use, run a TLS reverse proxy on the same host and keep KeyServer bound to loopback behind it. Configure `FERTDLC_API_BASE` to the same HTTPS base URL for both the loader and Minecraft process, then rebuild. The loader and client reject plain HTTP for non-loopback hosts. The default `http://127.0.0.1:8787` is local development only; it is not a public key service.
