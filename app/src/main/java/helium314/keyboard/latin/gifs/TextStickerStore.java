// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;

/** Explicit saves only. Private PNGs are copied to the existing FileProvider cache for sharing. */
public final class TextStickerStore {
    public static final int MAX_SAVED=40, MAX_BYTES=2*1024*1024;
    private final File sharing,saved;
    public TextStickerStore(File cacheDirectory,File privateDirectory){
        sharing=new File(cacheDirectory,"gif-share");saved=new File(privateDirectory,"text-stickers");
    }
    public synchronized File preview(byte[] png)throws IOException {
        validatePng(png);ensure(sharing);prunePreviews();
        File file=File.createTempFile("text-preview-",".png",sharing);write(file,png);return file;
    }
    /** Promote before creating its URI. Ordinary preview churn never removes a shared attachment. */
    public synchronized File retainForSharing(File file)throws IOException {
        if(file==null||!file.isFile()||!file.getCanonicalFile().getParentFile().equals(sharing.getCanonicalFile())
            ||!file.getName().matches("text-(preview|shared)-[a-zA-Z0-9_-]+\\.png")||file.length()>MAX_BYTES)
            throw new IOException("Vista previa no válida para compartir");
        long now=System.currentTimeMillis();
        File[] sharedFiles=sharing.listFiles(candidate->candidate.isFile()&&candidate.getName().matches("text-shared-[a-zA-Z0-9_-]+\\.png"));
        if(sharedFiles!=null)for(File candidate:sharedFiles)
            if(!candidate.equals(file)&&now-candidate.lastModified()>86400000L)candidate.delete();
        File retained=file;
        if(file.getName().startsWith("text-preview-")){
            retained=new File(sharing,"text-shared-"+UUID.randomUUID()+".png");
            if(!file.renameTo(retained))throw new IOException("No se pudo conservar el adjunto");
        }
        if(!retained.setLastModified(now))throw new IOException("No se pudo renovar el adjunto");
        return retained;
    }
    public synchronized File save(byte[] png)throws IOException {
        validatePng(png);ensure(saved);
        File target=new File(saved,digest(png)+".png");
        if(!target.isFile()){
            File temporary=File.createTempFile("pending-",".tmp",saved);
            try{write(temporary,png);if(!temporary.renameTo(target))throw new IOException("No se pudo guardar el sticker");}
            finally{if(temporary.exists())temporary.delete();}
        }
        List<File> prior=saved();long newest=System.currentTimeMillis();
        for(File file:prior)if(file.lastModified()<Long.MAX_VALUE)newest=Math.max(newest,file.lastModified()+1);
        target.setLastModified(newest);
        List<File> entries=saved();entries.remove(target);entries.add(0,target);for(int i=MAX_SAVED;i<entries.size();i++)if(!entries.get(i).delete())throw new IOException("No se pudo limitar la colección");
        return target;
    }
    public synchronized List<File> saved(){
        File[] files=saved.listFiles(file->file.isFile()&&file.getName().matches("[a-f0-9]{64}\\.png")&&file.length()<=MAX_BYTES);
        if(files==null)return new ArrayList<>();
        Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed().thenComparing(File::getName));
        return new ArrayList<>(Arrays.asList(files));
    }
    public synchronized File shareCopy(File file)throws IOException {
        if(file==null||!file.isFile()||!file.getCanonicalFile().getParentFile().equals(saved.getCanonicalFile())
            ||!file.getName().matches("[a-f0-9]{64}\\.png")||file.length()>MAX_BYTES)throw new IOException("Sticker guardado no válido");
        byte[] bytes=new byte[(int)file.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(bytes);}
        return preview(bytes);
    }
    private void prunePreviews(){
        File[] files=sharing.listFiles(file->file.getName().startsWith("text-preview-")&&file.getName().endsWith(".png"));
        if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());
        for(int i=0;i<files.length;i++)if(i>=8||System.currentTimeMillis()-files[i].lastModified()>86400000L)files[i].delete();
    }
    private static void ensure(File directory)throws IOException{if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Almacenamiento no disponible");}
    private static void write(File file,byte[] bytes)throws IOException{try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}}
    private static String digest(byte[] bytes)throws IOException {
        try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder result=new StringBuilder();for(byte item:hash)result.append(String.format(Locale.ROOT,"%02x",item&255));return result.toString();}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
    static void validatePng(byte[] bytes)throws IOException {
        byte[] signature={(byte)137,80,78,71,13,10,26,10};
        if(bytes==null||bytes.length<57||bytes.length>MAX_BYTES)throw new IOException("PNG no válido");
        for(int i=0;i<signature.length;i++)if(bytes[i]!=signature[i])throw new IOException("PNG no válido");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes,8,bytes.length-8));boolean header=false,data=false,end=false;
        while(in.available()>0){
            int length=in.readInt();if(length<0||length>in.available()-8)throw new IOException("PNG incompleto");
            byte[] type=new byte[4];in.readFully(type);byte[] content=new byte[length];in.readFully(content);int checksum=in.readInt();
            CRC32 crc=new CRC32();crc.update(type);crc.update(content);if((int)crc.getValue()!=checksum)throw new IOException("PNG dañado");
            String name=new String(type,java.nio.charset.StandardCharsets.US_ASCII);
            if(!header){if(!name.equals("IHDR")||length!=13)throw new IOException("Cabecera PNG no válida");
                DataInputStream dimensions=new DataInputStream(new ByteArrayInputStream(content));
                if(dimensions.readInt()!=512||dimensions.readInt()!=512)throw new IOException("El sticker debe medir 512×512");header=true;
            }else if(name.equals("IDAT"))data=true;
            if(name.equals("IEND")){if(length!=0||in.available()!=0)throw new IOException("Final PNG no válido");end=true;break;}
        }
        if(!header||!data||!end)throw new IOException("PNG incompleto");
    }
}
