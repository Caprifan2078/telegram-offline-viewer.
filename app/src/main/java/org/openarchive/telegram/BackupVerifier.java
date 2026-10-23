package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.zip.*;
import org.json.JSONObject;

/** Read-only backup integrity check: consumes every entry/CRC without changing any chat. */
final class BackupVerifier {
    interface Progress {void onProgress(String message);}
    static final class Result {int files;long bytes;boolean full;}
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