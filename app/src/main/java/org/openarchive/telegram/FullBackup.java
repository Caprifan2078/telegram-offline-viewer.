package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Portable ZIP v2. Contains v1 metadata backup plus original Telegram media trees.
 * Streams files without loading media in memory. Archives are private user data: no networking. */
final class FullBackup {
    private static final long MAX_BYTES=80L*1024*1024*1024;
    private static final int MAX_FILES=250000;
    interface Progress {void show(String text);}
    private static long stream(InputStream in,OutputStream out,long max) throws IOException {
        byte[] buf=new byte[128*1024];long count=0;int n;
        while((n=in.read(buf))!=-1){count+=n;if(count>max)throw new IOException("Слишком большой файл");out.write(buf,0,n);}
        return count;
    }
    static void export(Context ctx,SharedPreferences prefs,Uri target,Progress progress) throws Exception {
        try(OutputStream output=ctx.getContentResolver().openOutputStream(target,"w")){
            if(output==null)throw new IOException("Нельзя открыть файл назначения");
            export(ctx,prefs,output,progress);
        }
    }
    static void export(Context ctx,SharedPreferences prefs,OutputStream target,Progress progress) throws Exception {
        JSONArray chats=new JSONArray(prefs.getString("archive_registry","[]"));
        File metadata=File.createTempFile("tg-metadata-", ".zip",ctx.getCacheDir());
        try{
            ArchiveBackup.write(ctx,prefs,Uri.fromFile(metadata),progress::show);
            {
                try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(target,128*1024))){
                    zip.setLevel(1); // media is typically already compressed
                    writeZipFile(zip,metadata,"metadata.zip");
                    JSONObject index=new JSONObject();
                    index.put("format","telegram-archive-full");index.put("version",2);
                    index.put("mediaIncluded",true);
                    JSONArray roots=new JSONArray();long[] total={0};int[] files={0};
                    for(int i=0;i<chats.length();i++){
                        JSONObject chat=chats.getJSONObject(i);
                        String stored=chat.optString("tree","");
                        if(stored.isEmpty())throw new IOException("Для чата «"+chat.optString("name")+"» не подключена исходная папка");
                        DocumentFolder source=stored.startsWith("local:")
                            ?new DocumentFolder(ctx.getContentResolver(),new File(stored.substring(6)))
                            :new DocumentFolder(ctx.getContentResolver(),Uri.parse(stored));
                        JSONObject root=new JSONObject();root.put("chatIndex",i);root.put("prefix","media/chat-"+i+"/");roots.put(root);
                        progress.show("Сохраняю исходные медиа "+(i+1)+"/"+chats.length());
                        traverse(ctx,source,source.rootId(),"","media/chat-"+i+"/",zip,progress,total,files,0);
                    }
                    index.put("chats",roots);index.put("files",files[0]);index.put("bytes",total[0]);
                    zip.putNextEntry(new ZipEntry("full-manifest.json"));
                    zip.write(index.toString().getBytes("UTF-8"));zip.closeEntry();
                }
            }
        }finally{metadata.delete();}
    }
    private static void writeZipFile(ZipOutputStream zip,File file,String name) throws Exception{
        zip.putNextEntry(new ZipEntry(name));
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){stream(in,zip,3L*1024*1024*1024);}
        zip.closeEntry();
    }
    private static void traverse(Context ctx,DocumentFolder folder,String id,String relative,String prefix,ZipOutputStream zip,
                                 Progress progress,long[] total,int[] count,int depth) throws Exception {
        if(depth>32)throw new IOException("Слишком глубокие каталоги");
        for(DocumentFolder.Entry item:folder.children(id)){
            String name=item.name;
            if(name==null||name.isEmpty()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0)throw new IOException("Недопустимое имя файла");
            String path=relative+name;
            if(path.length()>1200)throw new IOException("Слишком длинный путь");
            if("vnd.android.document/directory".equals(item.mime))traverse(ctx,folder,item.id,path+"/",prefix,zip,progress,total,count,depth+1);
            else{
                if(++count[0]>MAX_FILES)throw new IOException("Слишком много файлов");
                zip.putNextEntry(new ZipEntry(prefix+path));
                try(InputStream in=ctx.getContentResolver().openInputStream(folder.uriFor(item.id))){
                    if(in==null)throw new IOException("Нет доступа к "+path);
                    byte[] buffer=new byte[128*1024];int n;long fileSize=0;
                    while((n=in.read(buffer))!=-1){
                        fileSize+=n;total[0]+=n;
                        if(total[0]>MAX_BYTES)throw new IOException("Архив превышает установленный предел");
                        zip.write(buffer,0,n);
                    }
                }
                zip.closeEntry();
                if(count[0]%200==0)progress.show("Упаковано "+count[0]+" файлов · "+total[0]/1048576+" МБ");
            }
        }
    }
    static int restore(Context ctx,SharedPreferences prefs,Uri uri,Progress progress) throws Exception {
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Архив недоступен");
            return restore(ctx,prefs,in,progress);
        }
    }
    static int restore(Context ctx,SharedPreferences prefs,InputStream streamed,Progress progress)throws Exception{
        File staging=new File(new File(ctx.getFilesDir(),"telegram_imports"),"restore-"+UUID.randomUUID().toString().replace("-",""));
        if(!staging.mkdirs())throw new IOException("Невозможно подготовить восстановление");
        File metadata=new File(staging,"metadata.zip");
        int count=0;long total=0;boolean complete=false;JSONObject manifest=null;
        try{
            {
                try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(streamed,128*1024))){
                    ZipEntry entry;
                    while((entry=zip.getNextEntry())!=null){
                        if(entry.isDirectory()){zip.closeEntry();continue;}
                        if(++count>MAX_FILES+2)throw new IOException("Превышено число файлов");
                        String name=entry.getName();
                        if(!name.equals("metadata.zip")&&!name.equals("full-manifest.json")
                            &&!name.matches("media/chat-[0-9]{1,4}/.*"))throw new IOException("Недопустимый элемент резервной копии");
                        if(name.contains("\\")||name.startsWith("/")||name.contains("\0"))throw new IOException("Опасный путь");
                        for(String part:name.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("Опасный путь в ZIP");
                        if(name.equals("full-manifest.json")){
                            ByteArrayOutputStream out=new ByteArrayOutputStream();stream(zip,out,2*1024*1024);
                            manifest=new JSONObject(out.toString("UTF-8"));
                        }else{
                            File file=new File(staging,name);
                            if(!file.getCanonicalPath().startsWith(staging.getCanonicalPath()+File.separator))throw new IOException("Выход за пределы папки");
                            if(file.exists())throw new IOException("Повторный файл в архиве");
                            File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Нельзя создать папку");
                            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
                                byte[] buf=new byte[128*1024];int n;
                                while((n=zip.read(buf))!=-1){
                                    total+=n;if(total>MAX_BYTES)throw new IOException("Превышен лимит распаковки");
                                    out.write(buf,0,n);
                                }
                            }
                        }
                        zip.closeEntry();
                        if(count%200==0)progress.show("Восстановлено "+count+" файлов");
                    }
                }
            }
            if(manifest==null||!"telegram-archive-full".equals(manifest.optString("format"))||
                manifest.optInt("version")!=2||!metadata.isFile())throw new IOException("Некорректная полная резервная копия");
            JSONArray roots=manifest.getJSONArray("chats");
            if(roots.length()>1000)throw new IOException("Слишком много переписок");
            JSONArray prior=new JSONArray(prefs.getString("archive_registry","[]"));
            int oldCount=prior.length();
            int restored=ArchiveBackup.restore(ctx,prefs,Uri.fromFile(metadata),progress::show);
            if(roots.length()!=restored)throw new IOException("Количество медиа-каталогов не совпадает");
            JSONArray all=new JSONArray(prefs.getString("archive_registry","[]"));
            for(int i=0;i<restored;i++){
                JSONObject record=all.getJSONObject(oldCount+i);
                File media=new File(staging,"media/chat-"+i);
                if(!media.isDirectory()&&!media.mkdirs())throw new IOException("Отсутствует папка медиа для чата "+i);
                record.put("tree","local:"+media.getAbsolutePath());
            }
            if(!prefs.edit().putString("archive_registry",all.toString()).commit())
                throw new IOException("Не удалось зарегистрировать восстановленные медиа");
            complete=true;return restored;
        }finally{
            metadata.delete();
            if(!complete)ArchiveFileImport.deleteTree(staging);
            // On success the full media tree remains in private storage.
        }
    }
}