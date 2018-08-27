package com.tomclaw.cache;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@SuppressWarnings("unused")
public class DiskLruCache {

    public static final int JOURNAL_FORMAT_VERSION = 1;
    private static final boolean LOGGING = false;

    private final File cacheDir;
    private final Journal journal;
    private final long cacheSize;

    private DiskLruCache(File cacheDir, Journal journal, long cacheSize) {
        this.cacheDir = cacheDir;
        this.journal = journal;
        this.cacheSize = cacheSize;
    }

    public static DiskLruCache create(File cacheDir, long cacheSize) throws IOException {
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                throw new IOException("Unable to create specified cache directory");
            }
        }
        File file = new File(cacheDir, "journal.bin");
        Journal journal = Journal.readJournal(file);
        return new DiskLruCache(cacheDir, journal, cacheSize);
    }

    public File put(String key, File file) throws IOException {
        String name = sha256Hex(key);
        long time = System.currentTimeMillis();
        long fileSize = file.length();
        Record record = new Record(key, name, time, fileSize);
        File cacheFile = new File(cacheDir, name);
        if ((cacheFile.exists() && cacheFile.delete()) | file.renameTo(cacheFile)) {
            journal.put(record, cacheSize, cacheDir);
            journal.writeJournal();
            return cacheFile;
        } else {
            throw new IOException(String.format("Unable to move file %s to the cache",
                    file.getName()));
        }
    }

    public File get(String key) {
        Record record = journal.get(key);
        if (record != null) {
            File file = new File(cacheDir, record.getName());
            if (!file.exists()) {
                journal.delete(key);
                file = null;
            }
            journal.writeJournal();
            return file;
        } else {
            log("[-] No requested file with key %s in cache", key);
            return null;
        }
    }

    public long getCacheSize() {
        return cacheSize;
    }

    public long getUsedSpace() {
        return journal.getTotalSize();
    }

    public long getFreeSpace() {
        return cacheSize - journal.getTotalSize();
    }

    public static String sha256Hex(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException ignored) {
        } catch (UnsupportedEncodingException ignored) {
        }
        throw new IllegalArgumentException("Unable to hash key");
    }

    public static void log(String format, Object... args) {
        if (LOGGING) {
            System.out.println(String.format(format, args));
        }
    }

}
