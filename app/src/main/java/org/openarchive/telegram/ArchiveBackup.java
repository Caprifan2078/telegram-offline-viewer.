package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Offline portable backup of indexed chat text, media references, favorites, pins and notes.
 * Deliberately does not copy the potentially enormous Telegram media folders. */
final class ArchiveBackup {
    interface Progress {void update(String message);}
    static final int VERSION=1;
    static final long MAX_DB_BYTES=2L*1024*1024*1024; // refuse malformed or huge ZIP expansion
    private static String hex(byte[] data){
        StringBuilder out=new StringBuilder();for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static MessageDigest digest() throws Exception{return MessageDigest.getInstance("SHA-256");}
    private static long copy(InputStream input,OutputStream output,MessageDigest sha,long limit) throws IOException{
        byte[] buf=new byte[64*1024];long size=0;int n;
        while((n=input.read(buf))!=-1){
            size+=n;if(size>limit)throw new IOException("Превышен допустимый размер данных в резервной копии");
            output.write(buf,0,n);if(sha!=null)sha.update(buf,0,n);
        }return size;
    }
    private static JSONObject exportedDb(ZipOutputStream zip,File file,String entryName) throws Exception{
        if(!file.isFile())throw new IOException("Не найдена локальная база: "+file.getName());
        MessageDigest sha=digest();zip.putNextEntry(new ZipEntry(entryName));
        long len;try(InputStream input=new BufferedInputStream(new FileInputStream(file))){
            len=copy(input,zip,sha,MAX_DB_BYTES);
        }
        zip.closeEntry();
        JSONObject o=new JSONObject();o.put("file",entryName);o.put("sha256",hex(sha.digest()));o.put("size",len);return o;
    }
    private static void checkpoint(SQLiteDatabase db) throws Exception{
        // Export only committed pages from the main .db file; do not silently omit WAL.
        try(Cursor c=db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null)){
            if(c.moveToFirst()&&c.getInt(0)!=0)throw new IOException("База занята, повтори резервное копирование");
        }
    }
    static void write(Context ctx,SharedPreferences prefs,Uri destination,Progress progress) throws Exception {
        JSONArray chats=new JSONArray(prefs.getString("archive_registry","[]"));
        JSONObject manifest=new JSONObject();
        manifest.put("format","telegram-archive-metadata");manifest.put("version",VERSION);
        manifest.put("created",System.currentTimeMillis());manifest.put("mediaIncluded",false);
        JSONArray records=new JSONArray();
        JSONObject settings=new JSONObject();
        for(Map.Entry<String,?> item:prefs.getAll().entrySet()){
            Object v=item.getValue();
            if(v instanceof String||v instanceof Number||v instanceof Boolean)settings.put(item.getKey(),v);
        }
        manifest.put("settings",settings);
        try(OutputStream file=ctx.getContentResolver().openOutputStream(destination,"w")){
            if(file==null)throw new IOException("Невозможно открыть файл для записи");
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(file))){
                for(int i=0;i<chats.length();i++){
                    JSONObject chat=chats.getJSONObject(i);
                    String key=chat.optString("key","default"),name=chat.optString("name","Переписка");
                    if(!key.matches("[a-zA-Z0-9_-]{1,80}"))throw new IOException("Некорректный ID переписки");
                    progress.update("Сохраняю "+(i+1)+"/"+chats.length()+": "+name);
                    MessageDb helper=new MessageDb(ctx,key);
                    File dbFile=ctx.getDatabasePath("default".equals(key)?"telegram_archive.db":"telegram_"+key+".db");
                    try{checkpoint(helper.getWritableDatabase());}finally{helper.close();}
                    JSONObject entry=exportedDb(zip,dbFile,"db/chat-"+i+".sqlite");
                    entry.put("key",key);entry.put("name",name);
                    records.put(entry);
                }
                progress.update("Сохраняю избранное и личные заметки");
                SavedStore notes=new SavedStore(ctx);
                try{checkpoint(notes.getWritableDatabase());}finally{notes.close();}
                manifest.put("chats",records);
                manifest.put("notes",exportedDb(zip,ctx.getDatabasePath("global_saved_notes.db"),"db/notes.sqlite"));
                zip.putNextEntry(new ZipEntry("manifest.json"));
                byte[] bytes=manifest.toString().getBytes("UTF-8");
                zip.write(bytes);zip.closeEntry();
            }
        }
    }
    private static File stage(Context ctx,ZipInputStream zip,ZipEntry entry,Map<String,File> files,Map<String,String> hashes) throws Exception{
        if(entry.isDirectory()||!entry.getName().matches("db/(?:chat-[0-9]{1,5}|notes)\\.sqlite"))throw new IOException("Недопустимый файл в архиве");
        if(files.containsKey(entry.getName()))throw new IOException("Повторяющийся элемент ZIP");
        File temp=File.createTempFile("tgrestore-", ".sqlite",ctx.getCacheDir());
        MessageDigest sha=digest();
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
            copy(zip,out,sha,MAX_DB_BYTES);
        }catch(Exception e){temp.delete();throw e;}
        files.put(entry.getName(),temp);hashes.put(entry.getName(),hex(sha.digest()));
        return temp;
    }
    private static void verifyDb(File path,String table) throws Exception{
        SQLiteDatabase db=SQLiteDatabase.openDatabase(path.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
        try{
            try(Cursor c=db.rawQuery("PRAGMA quick_check",null)){
                if(!c.moveToFirst()||!"ok".equalsIgnoreCase(c.getString(0)))throw new IOException("Повреждена база в резервной копии");
            }
            try(Cursor c=db.rawQuery("SELECT 1 FROM "+table+" LIMIT 1",null)){} // schema presence
        }finally{db.close();}
    }
    private static void verifyRecord(JSONObject rec,Map<String,File> files,Map<String,String> sha) throws Exception{
        String name=rec.getString("file");File file=files.get(name);
        if(file==null||file.length()!=rec.getLong("size"))throw new IOException("Неполные данные ZIP: "+name);
        if(!sha.get(name).equals(rec.getString("sha256")))throw new IOException("Контрольная сумма не совпадает: "+name);
    }
    private static void replaceWithCopy(File src,File dst) throws Exception{
        if(dst.exists())throw new IOException("База с таким именем уже существует");
        File parent=dst.getParentFile();if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("Нет доступа к каталогу баз");
        File temp=new File(parent,dst.getName()+".restoring");
        try(InputStream in=new FileInputStream(src);OutputStream out=new FileOutputStream(temp)){
            copy(in,out,null,MAX_DB_BYTES);
            out.flush();
        }
        if(!temp.renameTo(dst)){temp.delete();throw new IOException("Не удалось сохранить базу переписки");}
    }
    static int restore(Context ctx,SharedPreferences prefs,Uri source,Progress progress) throws Exception{
        File cache=ctx.getCacheDir();Map<String,File> files=new HashMap<>();Map<String,String> hashes=new HashMap<>();
        JSONObject manifest=null;ArrayList<File> created=new ArrayList<>();
        try{
            try(InputStream input=ctx.getContentResolver().openInputStream(source)){
                if(input==null)throw new IOException("Файл резервной копии недоступен");
                try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))){
                    ZipEntry entry;int entries=0;
                    while((entry=zip.getNextEntry())!=null){
                        if(++entries>3000)throw new IOException("Слишком много файлов в архиве");
                        if("manifest.json".equals(entry.getName())){
                            if(manifest!=null)throw new IOException("Повторяющийся manifest.json");
                            ByteArrayOutputStream out=new ByteArrayOutputStream();
                            copy(zip,out,null,2*1024*1024);manifest=new JSONObject(out.toString("UTF-8"));
                        }else stage(ctx,zip,entry,files,hashes);
                        zip.closeEntry();
                    }
                }
            }
            if(manifest==null||!"telegram-archive-metadata".equals(manifest.optString("format"))||
                    manifest.optInt("version")!=VERSION)throw new IOException("Неподдерживаемая резервная копия");
            JSONArray chats=manifest.getJSONArray("chats");
            JSONObject notes=manifest.getJSONObject("notes");verifyRecord(notes,files,hashes);
            verifyDb(files.get(notes.getString("file")),"saved_notes");
            if(chats.length()>1000)throw new IOException("Слишком много переписок");
            for(int i=0;i<chats.length();i++){
                JSONObject record=chats.getJSONObject(i);verifyRecord(record,files,hashes);
                verifyDb(files.get(record.getString("file")),"messages");
            }
            JSONArray registered=new JSONArray(prefs.getString("archive_registry","[]"));
            JSONObject prefsSaved=manifest.optJSONObject("settings");
            SharedPreferences.Editor edit=prefs.edit();
            Map<String,String> keyMap=new HashMap<>();
            for(int i=0;i<chats.length();i++){
                JSONObject rec=chats.getJSONObject(i);
                String newKey=UUID.randomUUID().toString().replace("-","");
                String oldKey=rec.optString("key","default");
                String name=rec.optString("name","Архив")+" (восстановлен)";
                keyMap.put(oldKey,newKey);
                File destination=ctx.getDatabasePath("telegram_"+newKey+".db");
                progress.update("Восстанавливаю "+(i+1)+"/"+chats.length()+": "+name);
                replaceWithCopy(files.get(rec.getString("file")),destination);
                created.add(destination);
                JSONObject archive=new JSONObject();
                archive.put("key",newKey);archive.put("name",name);archive.put("tree","");
                registered.put(archive);
            }
            // Merge restored notebook items with existing local notes; never overwrite the user's notes.
            SavedStore store=new SavedStore(ctx);
            SQLiteDatabase other=SQLiteDatabase.openDatabase(files.get(notes.getString("file")).getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
            try(Cursor c=other.rawQuery("SELECT body,file_name,file_id,mime FROM saved_notes ORDER BY id",null)){
                while(c.moveToNext())store.add(c.getString(0),c.getString(1),null,c.getString(3)); // old content URIs cannot be reused on a different phone
            }finally{other.close();store.close();}
            if(prefsSaved!=null){
                for(Iterator<String> it=prefsSaved.keys();it.hasNext();){
                    String key=it.next();
                    if(key.startsWith("sender_")){
                        for(Map.Entry<String,String> map:keyMap.entrySet()){
                            String prefix="sender_"+map.getKey()+"_";
                            if(key.startsWith(prefix)){edit.putInt("sender_"+map.getValue()+"_"+key.substring(prefix.length()),prefsSaved.optInt(key));break;}
                        }
                    }else if(key.startsWith("read_offset_")){
                        String original=key.substring("read_offset_".length());
                        if(keyMap.containsKey(original))edit.putInt("read_offset_"+keyMap.get(original),prefsSaved.optInt(key));
                    }
                }
            }
            edit.putString("archive_registry",registered.toString());
            if(!edit.commit())throw new IOException("Не удалось сохранить список архивов");
            return chats.length();
        }catch(Exception e){
            // Do not remove or overwrite older chats on restore failure.
            for(File file:created)file.delete();
            throw e;
        }finally{
            for(File file:files.values())file.delete();
        }
    }
}
