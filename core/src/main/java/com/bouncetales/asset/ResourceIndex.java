package com.bouncetales.asset;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Indice de recursos original, {@code resources/a} (1806 bytes).
 *
 * <pre>
 *   u16 nFicheros
 *   nFicheros x { readUTF nombre, s32 offset, s32 longitud }   // offset = -1 => fichero suelto
 *   u16 nObjetos
 *   nObjetos  x { u8 tipoLoader, u8 nRefs, s16 chunk, nRefs x s16 chunkImagen }
 *   u16 nPares
 *   nPares    x { s16 idLoader, s16 chunk }
 * </pre>
 *
 * Se conserva en el port como referencia: sirve para resolver "indice de nivel -&gt; fichero"
 * ({@code n.v} en el original) y para localizar los bancos de sprites y el audio.
 */
public final class ResourceIndex {

    public static final class FileEntry {
        public final String name;
        public final int offset;
        public final int length;

        FileEntry(String name, int offset, int length) {
            this.name = name;
            this.offset = offset;
            this.length = length;
        }

        /** {@code true} si el fichero vive completo en {@code resources/}. */
        public boolean isStandalone() {
            return offset < 0;
        }
    }

    public static final class ObjectEntry {
        public final int loaderType;
        public final int chunkFile;
        public final int[] imageFiles;

        ObjectEntry(int loaderType, int chunkFile, int[] imageFiles) {
            this.loaderType = loaderType;
            this.chunkFile = chunkFile;
            this.imageFiles = imageFiles;
        }
    }

    public final List<FileEntry> files;
    public final List<ObjectEntry> objects;
    public final int[] preloadLoaders;
    public final int[] preloadChunks;

    private ResourceIndex(List<FileEntry> files, List<ObjectEntry> objects,
                          int[] preloadLoaders, int[] preloadChunks) {
        this.files = files;
        this.objects = objects;
        this.preloadLoaders = preloadLoaders;
        this.preloadChunks = preloadChunks;
    }

    public static ResourceIndex parse(byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

            int nFiles = in.readUnsignedShort();
            List<FileEntry> files = new ArrayList<>(nFiles);
            for (int i = 0; i < nFiles; i++) {
                files.add(new FileEntry(in.readUTF(), in.readInt(), in.readInt()));
            }

            int nObjects = in.readUnsignedShort();
            List<ObjectEntry> objects = new ArrayList<>(nObjects);
            for (int i = 0; i < nObjects; i++) {
                int loaderType = in.readUnsignedByte();
                int nRefs = in.readUnsignedByte();
                int chunk = in.readShort();
                int[] refs = new int[nRefs];
                for (int r = 0; r < nRefs; r++) {
                    refs[r] = in.readShort();
                }
                objects.add(new ObjectEntry(loaderType, chunk, refs));
            }

            int nPairs = in.readUnsignedShort();
            int[] loaders = new int[nPairs];
            int[] chunks = new int[nPairs];
            for (int i = 0; i < nPairs; i++) {
                loaders[i] = in.readShort();
                chunks[i] = in.readShort();
            }

            return new ResourceIndex(files, objects, loaders, chunks);
        } catch (IOException e) {
            throw new IllegalArgumentException("indice de recursos invalido", e);
        }
    }

    /** Nombre del fichero/contenedor al que apunta un chunk. */
    public String fileName(int fileIndex) {
        return (fileIndex >= 0 && fileIndex < files.size()) ? files.get(fileIndex).name : null;
    }

    /**
     * Los 15 niveles jugables siguen el orden de {@code n.v}: 39,40,41,42,51,43,44,45,46,52,
     * 47,48,49,50,53 y despues 54 (banco de poligonos de la tuberia).
     */
    public static final int[] LEVEL_OBJECTS = {
            39, 40, 41, 42, 51, 43, 44, 45, 46, 52, 47, 48, 49, 50, 53, 54
    };

    public String levelFileName(int levelIndex) {
        if (levelIndex < 0 || levelIndex >= LEVEL_OBJECTS.length) {
            return null;
        }
        ObjectEntry o = objects.get(LEVEL_OBJECTS[levelIndex]);
        return fileName(o.chunkFile);
    }
}
