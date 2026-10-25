package org.kkaemok.skkaemok.dependency;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;

class PacketEventsInstallerTest {
    @TempDir Path directory;
    private JsonObject release(String url,String digest,boolean prerelease) {
        JsonObject asset=new JsonObject(); asset.addProperty("name","packetevents-spigot-2.14.0.jar"); asset.addProperty("browser_download_url",url);
        asset.addProperty("size",100); asset.addProperty("digest",digest);
        JsonArray assets=new JsonArray(); assets.add(asset); JsonObject release=new JsonObject(); release.add("assets",assets); release.addProperty("prerelease",prerelease); return release;
    }
    @Test void onlyOfficialStableSpigotAssetIsAccepted() throws Exception {
        String url="https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar";
        String digest="sha256:"+"a".repeat(64);
        assertEquals("packetevents-spigot-2.14.0.jar",PacketEventsInstaller.selectAsset(release(url,digest,false)).name());
        assertThrows(IOException.class,()->PacketEventsInstaller.selectAsset(release(url,digest,true)));
        assertThrows(IOException.class,()->PacketEventsInstaller.selectAsset(release(url,"",false)));
        assertThrows(IOException.class,()->PacketEventsInstaller.selectAsset(release(url.replace("github.com","evil.example"),digest,false)));
    }
    @Test void redirectsCannotEscapeApprovedHttpsHosts() {
        for(String url:new String[]{"http://github.com/file","https://127.0.0.1/file","https://github.com.evil.example/file","https://user@github.com/file","https://github.com:8080/file"}) assertFalse(PacketEventsInstaller.safeUri(URI.create(url)));
        assertTrue(PacketEventsInstaller.safeUri(URI.create("https://release-assets.githubusercontent.com/file?signature=test")));
    }
    @Test void validJarIsInstalledAndExistingFilesAreNeverOverwritten() throws Exception {
        byte[] bytes=jar("io.github.retrooper.packetevents.PacketEventsPlugin");
        var asset=asset(bytes); Path file=directory.resolve("packetevents-spigot.jar");
        PacketEventsInstaller.installJar(new ByteArrayInputStream(bytes),asset,file); assertArrayEquals(bytes,Files.readAllBytes(file));
        assertThrows(IOException.class,()->PacketEventsInstaller.installJar(new ByteArrayInputStream(bytes),asset,file)); assertArrayEquals(bytes,Files.readAllBytes(file));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
    @Test void corruptedTruncatedOversizedAndWrongPluginDownloadsAreNotInstalled() throws Exception {
        byte[] bytes=jar("io.github.retrooper.packetevents.PacketEventsPlugin"); var asset=asset(bytes); Path file=directory.resolve("packetevents-spigot.jar");
        byte[] corrupt=bytes.clone(); corrupt[10]^=1;
        for(byte[] invalid:new byte[][]{corrupt,java.util.Arrays.copyOf(bytes,bytes.length-1),java.util.Arrays.copyOf(bytes,bytes.length+1)}) assertThrows(Exception.class,()->PacketEventsInstaller.installJar(new ByteArrayInputStream(invalid),asset,file));
        byte[] other=jar("other.Plugin"); assertThrows(IOException.class,()->PacketEventsInstaller.installJar(new ByteArrayInputStream(other),asset(other),file));
        assertFalse(Files.exists(file)); try(var files=Files.list(directory)){assertEquals(0,files.count());}
    }
    @Test void incompatibleInstalledVersionsAreDetectedWithoutReplacingThem() {
        assertFalse(PacketEventsInstaller.supportedVersion("2.13.0")); assertFalse(PacketEventsInstaller.supportedVersion("unknown"));
        assertTrue(PacketEventsInstaller.supportedVersion("2.14.0")); assertTrue(PacketEventsInstaller.supportedVersion("2.14.1-SNAPSHOT"));
    }
    private PacketEventsInstaller.Asset asset(byte[] bytes) throws Exception {
        return new PacketEventsInstaller.Asset("packetevents-spigot-2.14.0.jar",URI.create("https://github.com/retrooper/packetevents/file"),bytes.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    private byte[] jar(String main) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(JarOutputStream jar=new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry("plugin.yml")); jar.write(("name: packetevents\nversion: '2.14.0'\nmain: "+main+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); jar.closeEntry();
        }
        return bytes.toByteArray();
    }
}
