package com.dozenesstudio.chunkoid.util;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileSystemAdapter {
    private final Context context;

    public FileSystemAdapter(Context context) {
        this.context = context;
    }

    public DocumentFile fromFile(File file) {
        return DocumentFile.fromFile(file);
    }

    public DocumentFile fromUri(Uri uri) {
        return DocumentFile.fromSingleUri(context, uri);
    }

    public InputStream openInputStream(DocumentFile file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist");
        }
        ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(file.getUri(), "r");
        if (pfd == null) {
            throw new IOException("Failed to open file descriptor");
        }
        return new FileInputStream(pfd.getFileDescriptor());
    }

    public OutputStream openOutputStream(DocumentFile file) throws IOException {
        if (file == null) {
            throw new IOException("File is null");
        }
        ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(file.getUri(), "w");
        if (pfd == null) {
            throw new IOException("Failed to open file descriptor");
        }
        return new FileOutputStream(pfd.getFileDescriptor());
    }

    public boolean exists(DocumentFile file) {
        return file != null && file.exists();
    }

    public boolean isDirectory(DocumentFile file) {
        return file != null && file.isDirectory();
    }

    public boolean isFile(DocumentFile file) {
        return file != null && file.isFile();
    }

    public String getName(DocumentFile file) {
        return file != null ? file.getName() : null;
    }

    public DocumentFile[] listFiles(DocumentFile directory) {
        return directory != null ? directory.listFiles() : new DocumentFile[0];
    }

    public DocumentFile createDirectory(DocumentFile parent, String name) {
        return parent != null ? parent.createDirectory(name) : null;
    }

    public DocumentFile createFile(DocumentFile parent, String mimeType, String name) {
        return parent != null ? parent.createFile(mimeType, name) : null;
    }

    public boolean delete(DocumentFile file) {
        return file != null && file.delete();
    }

    public boolean renameTo(DocumentFile file, String newName) {
        return file != null && file.renameTo(newName);
    }
}
