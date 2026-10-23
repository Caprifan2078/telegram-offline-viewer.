package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.zip.*;
import org.json.JSONObject;
import org.json.JSONArray;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import java.util.*;

/** Read-only backup integrity check: consumes every entry/CRC without changing any chat. */
final class BackupVerifier {
    interface Progress {void onProgress(String message);}
    static final class Result {int files;long bytes;boolean full;}
    static final class DryRun {
        int chats,messages,favorites,pins,notes,mediaFiles;
        long bytes;
        String summary(){
            return "Переписок: "+chats+"\nСообщений: "+messages+"\nИзбранных сообщений: "+favorites+
                "\nЗакреплений: "+pins+"\nЗаметок: "+notes+"\nФайлов медиа: "+mediaFiles+
                "\nПрочитано: "+(bytes/1048576)+" МБ";
        }
    }
    /** Dry-run in app cache only: no changes to production DBs or chat registry. */
    static DryRun simulate(Context context,Uri uri,char[] password,Progress progress)throws Exception{
        java.io.File stage=new java.io.File(context.getCacheDir(),"backup-test-"+java.util.UUID.randomUUID().toString());
        if(!stage.mkdirs())throw new IOException("Не получилось создать временную область");
        try{
            DryRun report=new DryRun();
            try(InputStream source=SecureArchive.openInput(context,uri,password)){
                scanBackup(context,source,stage,report,progress);
            }
            return report;
        }finally{ArchiveFileImport.deleteTree(stage);}
    }
    private static void scanBackup(Context context,InputStream source,File stage,DryRun result,Progress progress)throws Exception {
        boolean full=false;File inner=null;JSONObject manifest=null;
        java.util.HashSet<String> entries=new java.util.HashSet<>();
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source,128*1024))){
            ZipEntry entry;int count=0;
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory()){zip.closeEntry();continue;}
                if(++count>260000)throw new IOException("Превышен лимит файлов");
                String name=entry.getName();
                if(!entries.add(name))throw new IOException("Повторяющийся файл");
                if(name==null||name.startsWith("/")||name.indexOf(92)>=0||name.indexOf(0)>=0)throw new IOException("Опасный путь в ZIP");
                for(String part:name.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("Опасный путь");
                boolean database=name.matches("db/(?:chat-[0-9]{1,5}|notes)\\.sqlite");
                boolean innerMeta=name.equals("metadata.zip");
                if(innerMeta){full=true;inner=new File(stage,"metadata.zip");}
                File extracted=database?new File(stage,name.replace('/','_')):innerMeta?inner:null;
                if(extracted!=null){
                    try(OutputStream out=new FileOutputStream(extracted)){
                        byte[] buf=new byte[65536];int n;long size=0;
                        while((n=zip.read(buf))!=-1){
                            size+=n;result.bytes+=n;
                            if(size>3L*1024*1024*1024)throw new IOException("Временная база слишком велика");
                            out.write(buf,0,n);
                        }
                    }
                    if(database)checkDatabase(extracted,name,result);
                }else{
                    byte[] buf=new byte[65536];int n;
                    while((n=zip.read(buf))!=-1){result.bytes+=n;if(result.bytes>90L*1024*1024*1024)throw new IOException("Архив слишком велик");}
                    if(name.startsWith("media/"))result.mediaFiles++;
                }
                zip.closeEntry();
                if(count%250==0)progress.onProgress("Пробное восстановление: "+count+" файлов");
            }
        }
        if(full){
            if(inner==null||!inner.isFile())throw new IOException("Отсутствует metadata.zip");
            try(InputStream nested=new FileInputStream(inner)){scanBackup(context,nested,stage,result,progress);}
        }
        if(result.chats==0)throw new IOException("Не найдены проверяемые переписки");
    }
    private static void checkDatabase(File file,String name,DryRun report)throws Exception{
        SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
        try{
            try(Cursor c=db.rawQuery("PRAGMA integrity_check",null)){
                if(!c.moveToFirst()||!"ok".equalsIgnoreCase(c.getString(0)))throw new IOException("Повреждена база: "+name);
            }
            if(name.endsWith("notes.sqlite")){
                try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM saved_notes",null)){if(c.moveToFirst())report.notes+=c.getInt(0);}
            }else{
                report.chats++;
                for(String[] pair:new String[][]{{"messages","messages"},{"favorites","favorites"},{"pins","pins"}}){
                    try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+pair[0],null)){
                        if(c.moveToFirst()){
                            int n=c.getInt(0);
                            if(pair[1].equals("messages"))report.messages+=n;
                            if(pair[1].equals("favorites"))report.favorites+=n;
                            if(pair[1].equals("pins"))report.pins+=n;
                        }
                    }
                }
            }
        }finally{db.close();}
    }
    static Result verify(Context context,Uri uri,char[] password,Progress progress)throws Exception {
        try(InputStream source=SecureArchive.openInput(context,uri,password)){
            return verifyStream(source,progress);
        }
    }
    static Result verifyStream(InputStream source,Progress progress)throws Exception{
        Result result=new Result();boolean metadata=false,manifest=false;
        InputStream keep=new FilterInputStream(source){@Override public void close()throws IOException{}};
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(keep,128*1024))){
            ZipEntry entry;byte[] buf=new byte[128*1024];
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory()){zip.closeEntry();continue;}
                if(++result.files>260000)throw new IOException("Слишком много файлов");
                String name=entry.getName();
                if(name==null||name.startsWith("/")||name.contains("\\")||name.contains("\0"))throw new IOException("Недопустимый путь");
                for(String part:name.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("Недопустимый путь");
                if("metadata.zip".equals(name))metadata=true;
                if("full-manifest.json".equals(name)){manifest=true;result.full=true;}
                long size=0;int n;
                while((n=zip.read(buf))!=-1){
                    size+=n;result.bytes+=n;
                    if(result.bytes>90L*1024*1024*1024)throw new IOException("Архив слишком велик");
                }
                zip.closeEntry(); // validates ZIP CRC
                if(result.files%200==0)progress.onProgress("Проверяю: "+result.files+" файлов");
            }
        }
        byte[] remaining=new byte[128*1024];while(source.read(remaining)!=-1){}
        if(result.files==0)throw new IOException("Пустой ZIP");
        if(result.full&&(!metadata||!manifest))throw new IOException("Неполная резервная копия");
        progress.onProgress("Контрольные суммы ZIP проверены: "+result.files+" файлов");
        return result;
    }
}