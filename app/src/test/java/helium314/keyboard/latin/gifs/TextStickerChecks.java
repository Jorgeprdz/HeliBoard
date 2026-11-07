package helium314.keyboard.latin.gifs;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Pure JVM validation and storage contracts; Android rendering has its own Robolectric test. */
public final class TextStickerChecks {
    public static void main(String[] args) throws Exception {
        check(TextStickerText.validate("Hola 😊").equals("Hola 😊"),"Spanish emoji text retained");
        reject(()->TextStickerText.validate(" \n\t"),"empty text");
        reject(()->TextStickerText.validate(repeat("😊",121)),"code point maximum");
        check(TextStickerText.validate(repeat("😊",120)).codePointCount(0,240)==120,"120 astral codepoints allowed");
        reject(()->TextStickerText.validate("broken\ud83d"),"unpaired surrogate refused");
        List<String> clusters=TextStickerText.graphemes("á👩🏽‍💻🇲🇽❤️");
        check(clusters.size()==4,"combining accents, ZWJ, flags and variation selector kept together");
        check(clusters.get(1).equals("👩🏽‍💻"),"emoji modifier and ZWJ preserved");
        check(TextStickerText.appendWithinLimit(repeat("😊",119),"👩🏽‍💻",120).equals(repeat("😊",119)),"whole grapheme rejected at limit");
        check(TextStickerText.deleteLast("Hola 👩🏽‍💻").equals("Hola "),"backspace removes whole last cluster");
        File base=Files.createTempDirectory("text-stickers-test").toFile();
        TextStickerStore store=new TextStickerStore(new File(base,"cache"),new File(base,"files"));
        byte[] png=fixturePng(1);
        File preview=store.preview(png);check(preview.isFile()&&store.saved().isEmpty(),"preview is transient");
        for(int i=0;i<45;i++)store.save(fixturePng(i));
        check(store.saved().size()==40,"saved cap40");
        for(File existing:store.saved())existing.setLastModified(System.currentTimeMillis()+60000);
        check(store.save(fixturePng(99)).isFile(),"new save survives future timestamps and ties");
        TextStickerStore restarted=new TextStickerStore(new File(base,"cache"),new File(base,"files"));
        check(restarted.saved().size()==40,"saved persistence after restart");
        File saved=restarted.saved().get(0),copy=restarted.shareCopy(saved);
        check(copy.isFile()&&copy.getCanonicalPath().contains("gif-share"),"scoped sharing cache");
        check(java.util.Arrays.equals(Files.readAllBytes(saved.toPath()),Files.readAllBytes(copy.toPath())),"share preserves PNG bytes");
        reject(()->store.save(new byte[]{1,2,3}),"non PNG refused");
        reject(()->store.shareCopy(new File(base,"outside.png")),"arbitrary file sharing refused");
        check(store.saved().size()==40,"failed operation preserves collection");
        System.out.println("TextStickerChecks: 18 assertions passed");
    }
    static byte[] fixturePng(int seed) throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
        java.io.ByteArrayOutputStream header=new java.io.ByteArrayOutputStream();java.io.DataOutputStream h=new java.io.DataOutputStream(header);
        h.writeInt(512);h.writeInt(512);h.write(new byte[]{8,6,0,0,0});chunk(out,"IHDR",header.toByteArray());
        chunk(out,"tEXt",("seed\0"+seed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.io.ByteArrayOutputStream compressed=new java.io.ByteArrayOutputStream();
        try(java.util.zip.DeflaterOutputStream z=new java.util.zip.DeflaterOutputStream(compressed)){z.write(new byte[512*(512*4+1)]);}
        chunk(out,"IDAT",compressed.toByteArray());chunk(out,"IEND",new byte[0]);return out.toByteArray();
    }
    private static void chunk(java.io.OutputStream out,String type,byte[] data) throws Exception {
        java.io.DataOutputStream d=new java.io.DataOutputStream(out);byte[] name=type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        d.writeInt(data.length);d.write(name);d.write(data);java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(name);crc.update(data);d.writeInt((int)crc.getValue());
    }
    private static String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}
    private interface Checked {void run() throws Exception;}
    private static void reject(Checked operation,String name)throws Exception{try{operation.run();}catch(IllegalArgumentException|java.io.IOException expected){return;}throw new AssertionError(name);}
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
