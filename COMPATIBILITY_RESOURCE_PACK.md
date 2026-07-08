# VibePetCore Resource Pack Compatibility

Version: 2.6.37

## Supported Versions

| Component | Version | Notes |
|-----------|---------|-------|
| **Paper / Purpur server** | 1.21.11 | Matches `paper-api:1.21.11-R0.1-SNAPSHOT` compile target |
| **Minecraft Java client** | 1.21.9 – 1.21.11+ | `pack_format: 75` (1.21.9+) |
| **Resource pack format** | 75 | Defined in `resource-pack/VibePetCore/pack.mcmeta` |

Clients below 1.21.9 cannot load `pack_format: 75` and will reject the pack. The spawn-egg item model overrides use the 1.21.4+ `range_dispatch` / `custom_model_data` JSON format; older clients ignore these models.

## What the Resource Pack Provides

- Custom miniature textures for pet egg GUI slots (17 pet types × 2 states: egg + empty).
- Overrides vanilla spawn egg item models via `custom_model_data` thresholds.

Without the pack, pet functionality is **fully operational**. Only visuals degrade:
- Pet egg items in GUI show default vanilla spawn egg textures.
- `custom_model_data` values are still set server-side but have no client-side model mapping.

## Enabling the Pack

### 1. Install the zip on the server

```text
plugins/VibePetCore/resource-pack/VibePetCore-resource-pack.zip
```

Copy from `dist/VibePetCore-resource-pack.zip` (build: `./gradlew publishResourcePack`) or download from your CDN.

### 2. Configure `config.yml`

```yaml
resource-pack:
  enabled: true
  url: "https://your-cdn.example/VibePetCore-resource-pack.zip"
  sha1: "996118a5a85f33292f85157780201ec8cc1381d4"
  required: false
  prompt: "Для миниатюр питомцев нужен ресурс-пак VibePetCore."
  auto-host:
    enabled: true
    bind-host: "0.0.0.0"
    port: 25512
    public-host: "play.example.ru"
    public-url: ""
```

### 3. Choose a distribution mode

| Mode | When to use | Requirements |
|------|-------------|--------------|
| **External URL** (`resource-pack.url`) | Production, CDN, GitHub raw | HTTPS recommended; set matching `sha1` |
| **Auto-host** (`auto-host.enabled: true`) | Small/private servers | Open `auto-host.port` in firewall; set `public-host` or `public-url` |

Auto-host serves the local file from `plugins/VibePetCore/resource-pack/`. The URL sent to clients must be reachable from their network (not `127.0.0.1`).

## SHA1 Verification

`resource-pack.sha1` is strongly recommended in production.

- On enable, `ResourcePackManager` compares the local zip SHA1 against the configured value.
- **Mismatch blocks pack distribution** with a clear log message — the server will not silently serve a stale pack after a plugin upgrade.
- After upgrading VibePetCore or the pack zip, replace the local file and update `sha1` to match `MIGRATION-2.6.37.txt` or `RESOURCE_PACK_ASSETS.md`.

Clients also verify SHA1 when accepting the pack via `Player.setResourcePack(url, sha1, …)`.

## `required: false` Behavior

When `resource-pack.required: false` (default):
- Clients are prompted to download the pack but may decline.
- Declining does not kick the player.
- Pet mechanics, combat, quests, and GUI remain functional.
- Egg slot icons fall back to vanilla spawn egg appearance.

When `resource-pack.required: true`:
- Clients that reject or fail to load the pack are disconnected by the vanilla client.
- Use only if custom egg miniatures are mandatory for your server policy.

## Network and Firewall

| Port / endpoint | Direction | Purpose |
|-----------------|-----------|---------|
| `auto-host.port` (default 25512) | Inbound to server | HTTP download of pack zip |
| `resource-pack.url` (external) | Outbound from client | Client downloads pack from CDN |

For auto-host behind NAT/reverse proxy:
- Set `auto-host.public-host` to the public IP or domain players use.
- Or set `auto-host.public-url` to a full URL (overrides host/port).

## SQLite Native Libraries (Jar, Not Resource Pack)

The slim plugin jar includes SQLite JDBC natives for:
- **Linux x86_64** (`org/sqlite/native/Linux/x86_64/`)
- **Linux aarch64** (`org/sqlite/native/Linux/aarch64/`)

Excluded platforms: Windows, macOS, FreeBSD, Android, 32-bit ARM/x86, musl, ppc64.

`storage.backend=sqlite` works on supported Linux architectures only. Other platforms need `storage.backend=mysql` or `json`.

## Upgrade Checklist

1. Stop server.
2. Replace plugin jar.
3. Replace `plugins/VibePetCore/resource-pack/VibePetCore-resource-pack.zip` with the new dist artifact.
4. Update `resource-pack.sha1` in `config.yml` if the migration notes a new hash.
5. Restart and verify log line: `Resource pack ready: <url> sha1=<hash>`.
6. Join with a 1.21.9+ client and confirm egg miniatures render.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Log: `Resource pack file is missing` | Zip not copied to data folder | Copy `dist/VibePetCore-resource-pack.zip` to `plugins/VibePetCore/resource-pack/` |
| Log: `SHA1 mismatch` | Stale zip after upgrade | Replace local zip; update `sha1` in config |
| Log: `no URL is configured` | Auto-host enabled but `public-host` empty and no `url` | Set `resource-pack.url` or `auto-host.public-host` |
| Pack prompt but vanilla egg icons | Client declined pack or wrong version | Use client 1.21.9+; accept pack |
| Auto-host unreachable | Firewall blocks port 25512 | Open port or use external CDN URL |
