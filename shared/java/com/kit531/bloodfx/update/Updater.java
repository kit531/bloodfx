package com.kit531.bloodfx.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kit531.bloodfx.Compat;
import com.kit531.bloodfx.config.BloodFxConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Remote updates, straight from this mod's own GitHub releases.
 *
 * <p>What it does, and nothing else: ask GitHub for the latest release of
 * {@code kit531/bloodfx}, and if it is newer than the installed version, download the jar
 * built for the running Minecraft version. The download is only accepted from the
 * repository's release URL, is capped in size, is checked against GitHub's SHA-256 when GitHub
 * provides one, and must really be this mod (same id, expected version) before it is used.
 *
 * <p>A running jar cannot be replaced (on Windows it is locked), so the swap happens when the
 * game closes. Until then the new jar waits in {@code config/bloodfx/update/}.
 *
 * <p>Modes ({@link BloodFxConfig#updateMode}): 0 = off, 1 = tell me when there is an update,
 * 2 = download and install automatically.
 */
public final class Updater {
    public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

    private static final String MOD_ID = "bloodfx";
    private static final String REPO = "kit531/bloodfx";
    private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String DOWNLOAD_PREFIX = "https://github.com/" + REPO + "/releases/download/";
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final Pattern ASSET_NAME = Pattern.compile("^bloodfx-(.+)\\+mc(.+)\\.jar$");

    private static volatile State state = State.IDLE;
    private static volatile String message = "";
    private static volatile String latestVersion = "";
    private static volatile String chosenName = "";
    private static volatile String chosenUrl = "";
    private static volatile long chosenSize = 0;
    private static volatile String chosenDigest = "";
    private static volatile boolean announced = false;
    private static boolean hookRegistered = false;
    private static boolean busy = false;

    private Updater() {
    }

    // ---- public API ------------------------------------------------------------------

    /** Call once at startup. */
    public static void init() {
        if (currentJar() == null) {
            // Running from a development environment: there is no jar to replace.
            state = State.IDLE;
            return;
        }
        loadPending();
        if (BloodFxConfig.get().updateMode != 0 && state != State.READY) {
            checkAsync(false);
        }
    }

    /** Asks GitHub for a newer release. With mode 2 it also downloads it. */
    public static synchronized void checkAsync(boolean manual) {
        if (busy || state == State.READY) {
            return;
        }
        busy = true;
        Thread t = new Thread(() -> {
            try {
                check();
                if (state == State.AVAILABLE && BloodFxConfig.get().updateMode == 2) {
                    download();
                }
            } finally {
                busy = false;
            }
        }, "bloodfx-updater");
        t.setDaemon(true);
        t.start();
    }

    /** Downloads the update found by the last check. */
    public static synchronized void installAsync() {
        if (busy || state != State.AVAILABLE) {
            return;
        }
        busy = true;
        Thread t = new Thread(() -> {
            try {
                download();
            } finally {
                busy = false;
            }
        }, "bloodfx-updater");
        t.setDaemon(true);
        t.start();
    }

    public static State state() {
        return state;
    }

    public static String statusText() {
        return switch (state) {
            case IDLE -> "Updates: not checked yet";
            case CHECKING -> "Checking for updates...";
            case UP_TO_DATE -> message;
            case AVAILABLE -> "Update " + latestVersion + " available";
            case DOWNLOADING -> "Downloading " + latestVersion + "...";
            case READY -> "Update " + latestVersion + " ready - restart the game";
            case FAILED -> "Update check failed: " + message;
        };
    }

    /** Called every client tick: tells the player once per session about a ready update. */
    public static void tick(Minecraft mc) {
        if (announced || mc.player == null) {
            return;
        }
        if (state == State.READY) {
            say(mc, "Update " + latestVersion + " downloaded. Restart the game to apply it.");
            announced = true;
        } else if (state == State.AVAILABLE && BloodFxConfig.get().updateMode == 1) {
            say(mc, "Update " + latestVersion + " is available. Open Mod Menu > Blood FX > General to install it.");
            announced = true;
        }
    }

    private static void say(Minecraft mc, String text) {
        Compat.chat(mc, Component.literal("[Blood FX] " + text));
    }

    // ---- checking --------------------------------------------------------------------

    private static void check() {
        try {
            state = State.CHECKING;
            HttpResponse<String> response = http().send(request(LATEST_URL), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                fail("no public release found (the GitHub repository must be public)");
                return;
            }
            if (response.statusCode() != 200) {
                fail("GitHub answered " + response.statusCode());
                return;
            }

            JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
            String tag = release.get("tag_name").getAsString();
            String latest = tag.startsWith("v") ? tag.substring(1) : tag;
            String current = currentVersion();
            latestVersion = latest;

            if (!isNewer(latest, current)) {
                message = "Up to date (" + current + ")";
                state = State.UP_TO_DATE;
                return;
            }

            JsonObject asset = pickAsset(release.getAsJsonArray("assets"), gameVersion());
            if (asset == null) {
                fail("release " + latest + " has no jar for Minecraft " + gameVersion());
                return;
            }
            chosenName = asset.get("name").getAsString();
            chosenUrl = asset.get("browser_download_url").getAsString();
            chosenSize = asset.has("size") ? asset.get("size").getAsLong() : 0;
            JsonElement digest = asset.get("digest");
            chosenDigest = digest != null && !digest.isJsonNull() ? digest.getAsString() : "";
            state = State.AVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        } catch (Exception e) {
            fail(String.valueOf(e.getMessage()));
        }
    }

    /** Prefers the jar built for exactly this Minecraft version, then one for the same release line. */
    private static JsonObject pickAsset(JsonArray assets, String game) {
        if (assets == null) {
            return null;
        }
        JsonObject sameFamily = null;
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            Matcher m = ASSET_NAME.matcher(asset.get("name").getAsString());
            if (!m.matches()) {
                continue;
            }
            String assetGame = m.group(2);
            if (assetGame.equals(game)) {
                return asset;
            }
            if (family(assetGame).equals(family(game))) {
                sameFamily = asset;
            }
        }
        return sameFamily;
    }

    /** "1.21.11" stays as is (every 1.21.x is its own release); "26.1.2" becomes "26.1". */
    static String family(String version) {
        String[] parts = version.split("\\.");
        int keep = version.startsWith("1.") ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(keep, parts.length); i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    // ---- downloading -----------------------------------------------------------------

    private static void download() {
        try {
            state = State.DOWNLOADING;

            if (!chosenUrl.startsWith(DOWNLOAD_PREFIX)) {
                throw new IOException("unexpected download address");
            }
            if (!chosenName.equals(Path.of(chosenName).getFileName().toString())
                    || !ASSET_NAME.matcher(chosenName).matches()) {
                throw new IOException("unexpected file name");
            }
            if (chosenSize > MAX_BYTES) {
                throw new IOException("file is too large");
            }

            Path dir = updateDir();
            Files.createDirectories(dir);
            Path part = dir.resolve(chosenName + ".part");
            Path target = dir.resolve(chosenName);

            HttpResponse<InputStream> response = http().send(request(chosenUrl), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("download answered " + response.statusCode());
            }

            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_BYTES) {
                        throw new IOException("file is too large");
                    }
                    sha.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }

            String hex = HexFormat.of().formatHex(sha.digest());
            if (chosenDigest.startsWith("sha256:") && !chosenDigest.substring(7).equalsIgnoreCase(hex)) {
                Files.deleteIfExists(part);
                throw new IOException("checksum does not match");
            }
            verifyJar(part, latestVersion);

            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(pendingFile(), target.toString(), StandardCharsets.UTF_8);
            scheduleSwap(target);

            state = State.READY;
            announced = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        } catch (Exception e) {
            fail(String.valueOf(e.getMessage()));
        }
    }

    /** The file must be this mod, at the version GitHub said it was. */
    private static void verifyJar(Path jar, String expectedVersion) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) {
                throw new IOException("downloaded file is not a Fabric mod");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!MOD_ID.equals(json.get("id").getAsString())
                        || !json.get("version").getAsString().startsWith(expectedVersion)) {
                    throw new IOException("downloaded file is not the expected release");
                }
            }
        }
    }

    // ---- swapping the jar when the game closes ---------------------------------------

    private static synchronized void scheduleSwap(Path newJar) {
        Path oldJar = currentJar();
        if (oldJar == null || hookRegistered) {
            return;
        }
        hookRegistered = true;
        Path dest = oldJar.resolveSibling(newJar.getFileName());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> swapNow(oldJar, newJar, dest), "bloodfx-update-swap"));
    }

    private static void swapNow(Path oldJar, Path newJar, Path dest) {
        try {
            if (!Files.isRegularFile(newJar)) {
                return;
            }
            if (isWindows()) {
                swapOnWindows(oldJar, newJar, dest);
            } else {
                Files.move(newJar, dest, StandardCopyOption.REPLACE_EXISTING);
                if (!dest.equals(oldJar)) {
                    Files.deleteIfExists(oldJar);
                }
                Files.deleteIfExists(pendingFile());
            }
        } catch (Exception e) {
            System.err.println("[bloodfx] Could not apply the update, the new jar stays in " + newJar + ": " + e);
        }
    }

    /**
     * Windows keeps a running jar locked, so a tiny hidden PowerShell script waits for the game
     * to exit, moves the old jar aside, puts the new one in place and removes the old one. If
     * anything fails the old jar is put back, so the mod is never left missing.
     */
    private static void swapOnWindows(Path oldJar, Path newJar, Path dest) throws IOException {
        String script = ""
                + "$old = '" + ps(oldJar) + "'\n"
                + "$new = '" + ps(newJar) + "'\n"
                + "$dest = '" + ps(dest) + "'\n"
                + "$pending = '" + ps(pendingFile()) + "'\n"
                + "$bak = $old + '.bak'\n"
                + "for ($i = 0; $i -lt 180; $i++) {\n"
                + "  try {\n"
                + "    Move-Item -LiteralPath $old -Destination $bak -Force -ErrorAction Stop\n"
                + "    try {\n"
                + "      Move-Item -LiteralPath $new -Destination $dest -Force -ErrorAction Stop\n"
                + "      Remove-Item -LiteralPath $bak -Force -ErrorAction SilentlyContinue\n"
                + "      Remove-Item -LiteralPath $pending -Force -ErrorAction SilentlyContinue\n"
                + "    } catch {\n"
                + "      Move-Item -LiteralPath $bak -Destination $old -Force -ErrorAction SilentlyContinue\n"
                + "    }\n"
                + "    break\n"
                + "  } catch { Start-Sleep -Seconds 1 }\n"
                + "}\n";
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                .redirectErrorStream(true)
                .start();
    }

    private static String ps(Path path) {
        return path.toAbsolutePath().toString().replace("'", "''");
    }

    /** If a previous session downloaded an update but never got to apply it, pick it up again. */
    private static void loadPending() {
        try {
            Path file = pendingFile();
            if (!Files.isRegularFile(file)) {
                return;
            }
            Path jar = Path.of(Files.readString(file, StandardCharsets.UTF_8).trim());
            Matcher m = ASSET_NAME.matcher(jar.getFileName().toString());
            if (!Files.isRegularFile(jar) || !m.matches() || !isNewer(m.group(1), currentVersion())) {
                // Already applied, or the file is gone: clean up.
                Files.deleteIfExists(file);
                if (Files.isRegularFile(jar)) {
                    Files.deleteIfExists(jar);
                }
                return;
            }
            latestVersion = m.group(1);
            scheduleSwap(jar);
            state = State.READY;
        } catch (Exception e) {
            System.err.println("[bloodfx] Ignoring unreadable pending update: " + e);
        }
    }

    // ---- helpers ---------------------------------------------------------------------

    private static void fail(String reason) {
        message = reason;
        state = State.FAILED;
        System.err.println("[bloodfx] Update check failed: " + reason);
    }

    private static HttpClient http() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "bloodfx-updater")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
    }

    private static Path updateDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("bloodfx").resolve("update");
    }

    private static Path pendingFile() {
        return updateDir().resolve("pending.txt");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String gameVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    /** Installed version without the "+mc..." suffix, e.g. "1.0.0". */
    private static String currentVersion() {
        String full = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0");
        int plus = full.indexOf('+');
        return plus >= 0 ? full.substring(0, plus) : full;
    }

    /** The installed mod's jar, or null when running from a dev environment or a nested jar. */
    private static Path currentJar() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isEmpty() || container.get().getOrigin().getKind() != ModOrigin.Kind.PATH) {
            return null;
        }
        for (Path path : container.get().getOrigin().getPaths()) {
            if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                return path;
            }
        }
        return null;
    }

    static boolean isNewer(String candidate, String installed) {
        int[] a = parse(candidate);
        int[] b = parse(installed);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        String core = version.replaceFirst("^v", "").split("[+-]")[0];
        String[] parts = core.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
